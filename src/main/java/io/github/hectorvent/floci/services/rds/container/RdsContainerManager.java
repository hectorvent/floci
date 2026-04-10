package io.github.hectorvent.floci.services.rds.container;

import io.github.hectorvent.floci.config.EmulatorConfig;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.common.docker.ContainerDetector;
import io.github.hectorvent.floci.services.cloudwatch.logs.CloudWatchLogsService;
import io.github.hectorvent.floci.services.lambda.launcher.ImageCacheService;
import io.github.hectorvent.floci.services.rds.model.DatabaseEngine;
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
 * Manages backend Docker container lifecycle for RDS DB instances and clusters.
 * Starts postgres/mysql/mariadb containers and resolves the backend host:port for the auth proxy.
 */
@ApplicationScoped
public class RdsContainerManager {

    private static final Logger LOG = Logger.getLogger(RdsContainerManager.class);
    private static final DateTimeFormatter LOG_STREAM_DATE_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final DockerClient dockerClient;
    private final ImageCacheService imageCacheService;
    private final ContainerDetector containerDetector;
    private final EmulatorConfig config;
    private final CloudWatchLogsService cloudWatchLogsService;
    private final RegionResolver regionResolver;

    @Inject
    public RdsContainerManager(DockerClient dockerClient,
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

    public RdsContainerHandle start(String instanceId, DatabaseEngine engine,
                                    String image, String masterUsername,
                                    String masterPassword, String dbName) {
        LOG.infov("Starting RDS backend container for instance: {0} engine={1}", instanceId, engine);
        imageCacheService.ensureImageExists(image);

        int enginePort = engine.defaultPort();

        HostConfig hostConfig = buildHostConfig(enginePort);
        String containerName = "floci-rds-" + instanceId;

        config.services().rds().dockerNetwork()
                .or(() -> config.services().dockerNetwork())
                .ifPresent(network -> {
                    if (!network.isBlank()) {
                        hostConfig.withNetworkMode(network);
                        LOG.debugv("Attaching RDS container to network: {0}", network);
                    }
                });

        List<String> envVars = buildEnvVars(engine, masterUsername, masterPassword, dbName);
        List<String> cmd = buildContainerCmd(engine);

        var createCmd = dockerClient.createContainerCmd(image)
                .withName(containerName)
                .withEnv(envVars)
                .withExposedPorts(ExposedPort.tcp(enginePort))
                .withHostConfig(hostConfig);

        if (!cmd.isEmpty()) {
            createCmd.withCmd(cmd);
        }

        CreateContainerResponse container = createCmd.exec();
        String containerId = container.getId();
        LOG.infov("Created RDS container {0} for instance {1}", containerId, instanceId);

        dockerClient.startContainerCmd(containerId).exec();
        LOG.infov("Started RDS container {0}", containerId);

        String backendHost;
        int backendPort;

        if (!containerDetector.isRunningInContainer()) {
            var inspect = dockerClient.inspectContainerCmd(containerId).exec();
            var bindings = inspect.getNetworkSettings().getPorts().getBindings();
            var binding = bindings.get(ExposedPort.tcp(enginePort));
            if (binding != null && binding.length > 0) {
                backendPort = Integer.parseInt(binding[0].getHostPortSpec());
            } else {
                backendPort = enginePort;
            }
            backendHost = "localhost";
        } else {
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
            backendPort = enginePort;
        }

        LOG.infov("RDS backend for instance {0}: {1}:{2}", instanceId, backendHost, backendPort);

        RdsContainerHandle handle = new RdsContainerHandle(containerId, instanceId, backendHost, backendPort);
        String shortId = containerId.length() >= 8 ? containerId.substring(0, 8) : containerId;
        attachLogStream(handle, instanceId, containerId, shortId);
        return handle;
    }

    public void stop(RdsContainerHandle handle) {
        if (handle == null) {
            return;
        }
        LOG.infov("Stopping RDS container {0}", handle.getContainerId());

        if (handle.getLogStream() != null) {
            try { handle.getLogStream().close(); } catch (Exception ignored) {}
        }

        try {
            dockerClient.stopContainerCmd(handle.getContainerId()).withTimeout(5).exec();
        } catch (Exception e) {
            LOG.warnv("Error stopping RDS container {0}: {1}", handle.getContainerId(), e.getMessage());
        }

        try {
            dockerClient.removeContainerCmd(handle.getContainerId()).withForce(true).exec();
        } catch (Exception e) {
            LOG.warnv("Error removing RDS container {0}: {1}", handle.getContainerId(), e.getMessage());
        }
    }

    private HostConfig buildHostConfig(int enginePort) {
        HostConfig hostConfig = HostConfig.newHostConfig();
        if (!containerDetector.isRunningInContainer()) {
            int freePort = findFreePort();
            Ports portBindings = new Ports();
            portBindings.bind(ExposedPort.tcp(enginePort), Ports.Binding.bindPort(freePort));
            hostConfig.withPortBindings(portBindings);
            LOG.debugv("Native mode: binding container port {0} → host port {1}", enginePort, freePort);
        }
        return hostConfig;
    }

    private List<String> buildEnvVars(DatabaseEngine engine, String masterUsername,
                                      String masterPassword, String dbName) {
        String effectiveUser = (masterUsername != null && !masterUsername.isBlank()) ? masterUsername : "postgres";
        String effectiveDb   = (dbName != null && !dbName.isBlank()) ? dbName : effectiveUser;
        return switch (engine) {
            case POSTGRES -> List.of(
                    "POSTGRES_USER=" + effectiveUser,
                    "POSTGRES_PASSWORD=" + masterPassword,
                    "POSTGRES_DB=" + effectiveDb,
                    "POSTGRES_HOST_AUTH_METHOD=md5"
            );
            case MYSQL -> {
                var envs = new java.util.ArrayList<String>();
                envs.add("MYSQL_ROOT_PASSWORD=" + masterPassword);
                if (!"root".equals(effectiveUser)) {
                    envs.add("MYSQL_USER=" + effectiveUser);
                    envs.add("MYSQL_PASSWORD=" + masterPassword);
                }
                envs.add("MYSQL_DATABASE=" + effectiveDb);
                yield envs;
            }
            case MARIADB -> {
                var envs = new java.util.ArrayList<String>();
                envs.add("MARIADB_ROOT_PASSWORD=" + masterPassword);
                if (!"root".equals(effectiveUser)) {
                    envs.add("MARIADB_USER=" + effectiveUser);
                    envs.add("MARIADB_PASSWORD=" + masterPassword);
                }
                envs.add("MARIADB_DATABASE=" + effectiveDb);
                yield envs;
            }
        };
    }

    private List<String> buildContainerCmd(DatabaseEngine engine) {
        // Configure MySQL to use mysql_native_password so the proxy can authenticate
        // without needing caching_sha2_password RSA key exchange
        return switch (engine) {
            case MYSQL -> List.of("--default-authentication-plugin=mysql_native_password");
            case POSTGRES, MARIADB -> List.of();
        };
    }

    private static int findFreePort() {
        try (ServerSocket s = new ServerSocket(0)) {
            s.setReuseAddress(true);
            return s.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Could not find a free port for RDS container", e);
        }
    }

    private void attachLogStream(RdsContainerHandle handle, String instanceId,
                                  String containerId, String shortId) {
        String logGroup = "/aws/rds/instance/" + instanceId + "/error";
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
                                LOG.infov("[rds:{0}] {1}", instanceId, line);
                                forwardToCloudWatchLogs(logGroup, logStream, region, line);
                            }
                        }
                    });
            handle.setLogStream(logCallback);
        } catch (Exception e) {
            LOG.warnv("Could not attach log stream for RDS container {0}: {1}",
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
            LOG.debugv("Could not forward RDS log line to CloudWatch Logs: {0}", e.getMessage());
        }
    }
}
