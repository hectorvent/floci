package io.github.hectorvent.floci.services.elasticache.container;

import io.github.hectorvent.floci.config.EmulatorConfig;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.common.docker.ContainerDetector;
import io.github.hectorvent.floci.services.cloudwatch.logs.CloudWatchLogsService;
import io.github.hectorvent.floci.services.lambda.launcher.ImageCacheService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages backend Docker container lifecycle for ElastiCache replication groups.
 * In native (dev) mode, binds container port 6379 to a random host port.
 * In Docker mode, uses the container's internal network IP directly.
 */
@ApplicationScoped
public class ElastiCacheContainerManager {

    private static final Logger LOG = Logger.getLogger(ElastiCacheContainerManager.class);
    private static final int BACKEND_PORT = 6379;
    private static final DateTimeFormatter LOG_STREAM_DATE_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final DockerClient dockerClient;
    private final ImageCacheService imageCacheService;
    private final ContainerDetector containerDetector;
    private final EmulatorConfig config;
    private final CloudWatchLogsService cloudWatchLogsService;
    private final RegionResolver regionResolver;

    @Inject
    public ElastiCacheContainerManager(DockerClient dockerClient,
                                       ImageCacheService imageCacheService,
                                       ContainerDetector containerDetector,
                                       EmulatorConfig config,
                                       CloudWatchLogsService cloudWatchLogsService,
                                       RegionResolver regionResolver) {
        this.dockerClient = dockerClient;
        this.imageCacheService = imageCacheService;
        this.containerDetector = containerDetector;
        this.config = config;
        this.cloudWatchLogsService = cloudWatchLogsService;
        this.regionResolver = regionResolver;
    }

    public ElastiCacheContainerHandle start(String groupId, String image) {
        LOG.infov("Starting ElastiCache backend container for group: {0}", groupId);
        imageCacheService.ensureImageExists(image);

        HostConfig hostConfig = buildHostConfig();
        String containerName = "floci-valkey-" + groupId;

        config.services().elasticache().dockerNetwork()
                .or(() -> config.services().dockerNetwork())
                .ifPresent(network -> {
                    if (!network.isBlank()) {
                        hostConfig.withNetworkMode(network);
                        LOG.debugv("Attaching ElastiCache container to network: {0}", network);
                    }
                });

        CreateContainerResponse container = dockerClient.createContainerCmd(image)
                .withName(containerName)
                .withEnv(List.of("VALKEY_EXTRA_FLAGS=--loglevel verbose"))
                .withExposedPorts(ExposedPort.tcp(BACKEND_PORT))
                .withHostConfig(hostConfig)
                .exec();

        String containerId = container.getId();
        LOG.infov("Created ElastiCache container {0} for group {1}", containerId, groupId);

        dockerClient.startContainerCmd(containerId).exec();
        LOG.infov("Started ElastiCache container {0}", containerId);

        String backendHost;
        int backendPort;

        if (!containerDetector.isRunningInContainer()) {
            // Retrieve the actual allocated host port from the container inspect
            var inspect = dockerClient.inspectContainerCmd(containerId).exec();
            var bindings = inspect.getNetworkSettings().getPorts().getBindings();
            var binding = bindings.get(ExposedPort.tcp(BACKEND_PORT));
            if (binding != null && binding.length > 0) {
                backendPort = Integer.parseInt(binding[0].getHostPortSpec());
            } else {
                backendPort = BACKEND_PORT;
            }
            backendHost = "localhost";
        } else {
            // Docker mode: use container IP on the docker network
            var inspect = dockerClient.inspectContainerCmd(containerId).exec();
            var networks = inspect.getNetworkSettings().getNetworks();
            String containerIp = null;
            if (networks != null) {
                for (Map.Entry<String, ?> entry : networks.entrySet()) {
                    var netEntry = (com.github.dockerjava.api.model.ContainerNetwork) entry.getValue();
                    containerIp = netEntry.getIpAddress();
                    if (containerIp != null && !containerIp.isBlank()) {
                        break;
                    }
                }
            }
            if (containerIp == null || containerIp.isBlank()) {
                containerIp = inspect.getNetworkSettings().getIpAddress();
            }
            backendHost = containerIp;
            backendPort = BACKEND_PORT;
        }

        LOG.infov("ElastiCache backend for group {0}: {1}:{2}", groupId, backendHost, backendPort);

        ElastiCacheContainerHandle handle = new ElastiCacheContainerHandle(
                containerId, groupId, backendHost, backendPort);

        String shortId = containerId.length() >= 8 ? containerId.substring(0, 8) : containerId;
        attachLogStream(handle, groupId, containerId, shortId);

        return handle;
    }

    public void stop(ElastiCacheContainerHandle handle) {
        if (handle == null) {
            return;
        }
        LOG.infov("Stopping ElastiCache container {0}", handle.getContainerId());

        if (handle.getLogStream() != null) {
            try { handle.getLogStream().close(); } catch (Exception ignored) {}
        }

        try {
            dockerClient.stopContainerCmd(handle.getContainerId()).withTimeout(5).exec();
        } catch (Exception e) {
            LOG.warnv("Error stopping ElastiCache container {0}: {1}",
                    handle.getContainerId(), e.getMessage());
        }

        try {
            dockerClient.removeContainerCmd(handle.getContainerId()).withForce(true).exec();
        } catch (Exception e) {
            LOG.warnv("Error removing ElastiCache container {0}: {1}",
                    handle.getContainerId(), e.getMessage());
        }
    }

    private HostConfig buildHostConfig() {
        HostConfig hostConfig = HostConfig.newHostConfig();
        if (!containerDetector.isRunningInContainer()) {
            // Bind BACKEND_PORT → random host port so the JVM can reach the container
            int freePort = findFreePort();
            Ports portBindings = new Ports();
            portBindings.bind(ExposedPort.tcp(BACKEND_PORT), Ports.Binding.bindPort(freePort));
            hostConfig.withPortBindings(portBindings);
            LOG.debugv("Native mode: binding container port 6379 → host port {0}", freePort);
        }
        return hostConfig;
    }

    private static int findFreePort() {
        try (ServerSocket s = new ServerSocket(0)) {
            s.setReuseAddress(true);
            return s.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Could not find a free port for ElastiCache container", e);
        }
    }

    private void attachLogStream(ElastiCacheContainerHandle handle, String groupId,
                                  String containerId, String shortId) {
        String logGroup = "/aws/elasticache/cluster/" + groupId + "/engine-log";
        String region = regionResolver.getDefaultRegion();
        String logStream = LOG_STREAM_DATE_FMT.format(LocalDate.now()) + "/" + shortId;
        ensureLogGroupAndStream(logGroup, logStream, region);

        try {
            ResultCallback.Adapter<Frame> logCallback = dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .withTimestamps(false)
                    .exec(new ResultCallback.Adapter<>() {
                        @Override
                        public void onNext(Frame frame) {
                            String line = new String(frame.getPayload(), StandardCharsets.UTF_8).stripTrailing();
                            if (!line.isEmpty()) {
                                LOG.infov("[elasticache:{0}] {1}", groupId, line);
                                forwardToCloudWatchLogs(logGroup, logStream, region, line);
                            }
                        }
                    });
            handle.setLogStream(logCallback);
        } catch (Exception e) {
            LOG.warnv("Could not attach log stream for ElastiCache container {0}: {1}",
                    containerId, e.getMessage());
        }
    }

    private void ensureLogGroupAndStream(String logGroup, String logStream, String region) {
        try {
            cloudWatchLogsService.createLogGroup(logGroup, null, null, region);
        } catch (AwsException ignored) {
        } catch (Exception e) {
            LOG.warnv("Could not create CW log group {0}: {1}", logGroup, e.getMessage());
        }
        try {
            cloudWatchLogsService.createLogStream(logGroup, logStream, region);
        } catch (AwsException ignored) {
        } catch (Exception e) {
            LOG.warnv("Could not create CW log stream {0}/{1}: {2}", logGroup, logStream, e.getMessage());
        }
    }

    private void forwardToCloudWatchLogs(String logGroup, String logStream, String region, String line) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("timestamp", System.currentTimeMillis());
            event.put("message", line);
            cloudWatchLogsService.putLogEvents(logGroup, logStream, List.of(event), region);
        } catch (Exception e) {
            LOG.debugv("Could not forward ElastiCache log line to CloudWatch Logs: {0}", e.getMessage());
        }
    }
}
