package io.github.hectorvent.floci.services.ecs.container;

import io.github.hectorvent.floci.config.EmulatorConfig;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.services.cloudwatch.logs.CloudWatchLogsService;
import io.github.hectorvent.floci.services.ecs.model.Container;
import io.github.hectorvent.floci.services.ecs.model.ContainerDefinition;
import io.github.hectorvent.floci.services.ecs.model.EcsTask;
import io.github.hectorvent.floci.services.ecs.model.NetworkBinding;
import io.github.hectorvent.floci.services.ecs.model.PortMapping;
import io.github.hectorvent.floci.services.ecs.model.TaskDefinition;
import io.github.hectorvent.floci.services.lambda.launcher.DockerHostResolver;
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

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages Docker container lifecycle for ECS tasks.
 * Starts one Docker container per ContainerDefinition in a task and attaches logs to CloudWatch.
 */
@ApplicationScoped
public class EcsContainerManager {

    private static final Logger LOG = Logger.getLogger(EcsContainerManager.class);
    private static final String HOST_DOCKER_INTERNAL = "host.docker.internal";
    private static final DateTimeFormatter LOG_STREAM_DATE_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final DockerClient dockerClient;
    private final ImageCacheService imageCacheService;
    private final DockerHostResolver dockerHostResolver;
    private final EmulatorConfig config;
    private final CloudWatchLogsService cloudWatchLogsService;
    private final RegionResolver regionResolver;

    @Inject
    public EcsContainerManager(DockerClient dockerClient,
                                ImageCacheService imageCacheService,
                                DockerHostResolver dockerHostResolver,
                                EmulatorConfig config,
                                CloudWatchLogsService cloudWatchLogsService,
                                RegionResolver regionResolver) {
        this.dockerClient = dockerClient;
        this.imageCacheService = imageCacheService;
        this.dockerHostResolver = dockerHostResolver;
        this.config = config;
        this.cloudWatchLogsService = cloudWatchLogsService;
        this.regionResolver = regionResolver;
    }

    /**
     * Starts Docker containers for all container definitions in a task.
     * Updates the task's container list in-place with runtime network bindings and docker IDs.
     */
    public EcsTaskHandle startTask(EcsTask task, TaskDefinition taskDef, String region) {
        String taskId = extractTaskId(task.getTaskArn());
        boolean nativeMode = HOST_DOCKER_INTERNAL.equals(dockerHostResolver.resolve());

        Map<String, String> containerIds = new LinkedHashMap<>();
        List<Closeable> logStreams = new ArrayList<>();
        List<Container> runtimeContainers = new ArrayList<>();

        for (ContainerDefinition def : taskDef.getContainerDefinitions()) {
            imageCacheService.ensureImageExists(def.getImage());

            List<ExposedPort> exposedPorts = buildExposedPorts(def);
            HostConfig hostConfig = buildHostConfig(nativeMode, def, exposedPorts);

            applyDockerNetwork(hostConfig);

            List<String> envVars = buildEnvVars(def);
            String containerName = "floci-ecs-" + taskId + "-" + def.getName();

            var createCmd = dockerClient.createContainerCmd(def.getImage())
                    .withName(containerName)
                    .withEnv(envVars)
                    .withHostConfig(hostConfig);

            if (!exposedPorts.isEmpty()) {
                createCmd.withExposedPorts(exposedPorts);
            }
            if (def.getCommand() != null && !def.getCommand().isEmpty()) {
                createCmd.withCmd(def.getCommand());
            }
            if (def.getEntryPoint() != null && !def.getEntryPoint().isEmpty()) {
                createCmd.withEntrypoint(def.getEntryPoint());
            }

            CreateContainerResponse created = createCmd.exec();
            String dockerId = created.getId();
            LOG.infov("Created ECS container {0} for task {1} container {2}", dockerId, taskId, def.getName());

            dockerClient.startContainerCmd(dockerId).exec();
            LOG.infov("Started ECS container {0}", dockerId);

            List<NetworkBinding> networkBindings = resolveNetworkBindings(dockerId, def, nativeMode);

            Container container = buildContainer(task.getTaskArn(), def, dockerId, networkBindings, region);
            runtimeContainers.add(container);
            containerIds.put(def.getName(), dockerId);

            Closeable logStream = attachLogStream(dockerId, def.getName(), taskDef.getFamily(), taskId, region);
            if (logStream != null) {
                logStreams.add(logStream);
            }
        }

        task.setContainers(runtimeContainers);
        task.setLastStatus(TaskStatus.RUNNING.name());
        task.setDesiredStatus(TaskStatus.RUNNING.name());
        task.setStartedAt(Instant.now());

        return new EcsTaskHandle(task.getTaskArn(), containerIds, logStreams);
    }

    /**
     * Stops and removes all Docker containers for a task.
     */
    public void stopTask(EcsTaskHandle handle) {
        if (handle == null) {
            return;
        }

        for (Closeable logStream : handle.getLogStreams()) {
            try { logStream.close(); } catch (Exception ignored) {}
        }

        for (Map.Entry<String, String> entry : handle.getContainerIds().entrySet()) {
            String dockerId = entry.getValue();
            try {
                dockerClient.stopContainerCmd(dockerId).withTimeout(5).exec();
            } catch (Exception e) {
                LOG.warnv("Error stopping ECS container {0}: {1}", dockerId, e.getMessage());
            }
            try {
                dockerClient.removeContainerCmd(dockerId).withForce(true).exec();
            } catch (Exception e) {
                LOG.warnv("Error removing ECS container {0}: {1}", dockerId, e.getMessage());
            }
        }
    }

    private List<ExposedPort> buildExposedPorts(ContainerDefinition def) {
        List<ExposedPort> exposed = new ArrayList<>();
        if (def.getPortMappings() != null) {
            for (PortMapping pm : def.getPortMappings()) {
                exposed.add(ExposedPort.tcp(pm.containerPort()));
            }
        }
        return exposed;
    }

    private HostConfig buildHostConfig(boolean nativeMode, ContainerDefinition def, List<ExposedPort> exposedPorts) {
        HostConfig hostConfig = HostConfig.newHostConfig();

        if (def.getMemory() != null) {
            hostConfig.withMemory((long) def.getMemory() * 1024 * 1024);
        }

        if (nativeMode && !exposedPorts.isEmpty()) {
            Ports portBindings = new Ports();
            for (ExposedPort ep : exposedPorts) {
                portBindings.bind(ep, Ports.Binding.bindPort(0)); // 0 = dynamic host port
            }
            hostConfig.withPortBindings(portBindings);
        }

        return hostConfig;
    }

    private void applyDockerNetwork(HostConfig hostConfig) {
        config.services().ecs().dockerNetwork()
                .or(() -> config.services().dockerNetwork())
                .filter(n -> !n.isBlank())
                .ifPresent(network -> {
                    hostConfig.withNetworkMode(network);
                    LOG.debugv("Attaching ECS container to network: {0}", network);
                });
    }

    private List<String> buildEnvVars(ContainerDefinition def) {
        List<String> envVars = new ArrayList<>();
        if (def.getEnvironment() != null) {
            for (var kv : def.getEnvironment()) {
                envVars.add(kv.name() + "=" + kv.value());
            }
        }
        return envVars;
    }

    private List<NetworkBinding> resolveNetworkBindings(String dockerId, ContainerDefinition def, boolean nativeMode) {
        List<NetworkBinding> bindings = new ArrayList<>();
        if (def.getPortMappings() == null || def.getPortMappings().isEmpty()) {
            return bindings;
        }

        var inspect = dockerClient.inspectContainerCmd(dockerId).exec();
        var portBindingsMap = inspect.getNetworkSettings().getPorts().getBindings();

        for (PortMapping pm : def.getPortMappings()) {
            ExposedPort ep = ExposedPort.tcp(pm.containerPort());
            var binding = portBindingsMap.get(ep);
            int hostPort = pm.containerPort();
            String bindIp = "0.0.0.0";

            if (nativeMode && binding != null && binding.length > 0) {
                hostPort = Integer.parseInt(binding[0].getHostPortSpec());
                if (binding[0].getHostIp() != null && !binding[0].getHostIp().isBlank()) {
                    bindIp = binding[0].getHostIp();
                }
            }

            bindings.add(new NetworkBinding(bindIp, pm.containerPort(), hostPort, pm.protocol()));
        }
        return bindings;
    }

    private Container buildContainer(String taskArn, ContainerDefinition def, String dockerId,
                                     List<NetworkBinding> networkBindings, String region) {
        Container container = new Container();
        container.setTaskArn(taskArn);
        container.setName(def.getName());
        container.setImage(def.getImage());
        container.setLastStatus("RUNNING");
        container.setNetworkBindings(networkBindings);
        container.setDockerId(dockerId);
        container.setContainerArn(regionResolver.buildArn("ecs", region,
                "container/" + extractTaskId(taskArn) + "/" + def.getName()));
        return container;
    }

    private Closeable attachLogStream(String dockerId, String containerName,
                                      String family, String taskId, String region) {
        String logGroup = "/ecs/" + family;
        String logStream = LOG_STREAM_DATE_FMT.format(LocalDate.now()) + "/" + containerName + "/" + taskId;
        ensureLogGroupAndStream(logGroup, logStream, region);

        try {
            ResultCallback.Adapter<Frame> callback = dockerClient.logContainerCmd(dockerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .withTimestamps(false)
                    .exec(new ResultCallback.Adapter<>() {
                        @Override
                        public void onNext(Frame frame) {
                            String line = new String(frame.getPayload(), StandardCharsets.UTF_8).stripTrailing();
                            if (!line.isEmpty()) {
                                LOG.infov("[ecs:{0}:{1}] {2}", family, containerName, line);
                                forwardToCloudWatchLogs(logGroup, logStream, region, line);
                            }
                        }
                    });
            return callback;
        } catch (Exception e) {
            LOG.warnv("Could not attach log stream for ECS container {0}: {1}", dockerId, e.getMessage());
            return null;
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
            LOG.debugv("Could not forward ECS log line to CloudWatch Logs: {0}", e.getMessage());
        }
    }

    private static String extractTaskId(String taskArn) {
        int slash = taskArn.lastIndexOf('/');
        return slash >= 0 ? taskArn.substring(slash + 1) : taskArn;
    }

    // Inner enum to avoid import cycle — mirrors model.TaskStatus for readability
    private enum TaskStatus { RUNNING }
}
