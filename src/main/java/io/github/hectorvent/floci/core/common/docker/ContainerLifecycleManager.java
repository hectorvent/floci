package io.github.hectorvent.floci.core.common.docker;

import io.github.hectorvent.floci.services.lambda.launcher.ImageCacheService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.Closeable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manages Docker container lifecycle operations including create, start, stop, and remove.
 * Consolidates common container management patterns used across Floci services.
 */
@ApplicationScoped
public class ContainerLifecycleManager {

    private static final Logger LOG = Logger.getLogger(ContainerLifecycleManager.class);

    private final DockerClient dockerClient;
    private final ImageCacheService imageCacheService;
    private final ContainerDetector containerDetector;
    private final PortAllocator portAllocator;

    @Inject
    public ContainerLifecycleManager(DockerClient dockerClient,
                                     ImageCacheService imageCacheService,
                                     ContainerDetector containerDetector,
                                     PortAllocator portAllocator) {
        this.dockerClient = dockerClient;
        this.imageCacheService = imageCacheService;
        this.containerDetector = containerDetector;
        this.portAllocator = portAllocator;
    }

    /**
     * Creates and starts a container from the given specification.
     * Automatically pulls the image if not present locally.
     *
     * @param spec the container specification
     * @return information about the created container including resolved endpoints
     */
    public ContainerInfo createAndStart(ContainerSpec spec) {
        LOG.debugv("Creating container from spec: image={0}, name={1}", spec.image(), spec.name());

        // Ensure image is available
        imageCacheService.ensureImageExists(spec.image());

        // Build HostConfig
        HostConfig hostConfig = buildHostConfig(spec);

        // Create container command
        CreateContainerCmd createCmd = dockerClient.createContainerCmd(spec.image())
                .withHostConfig(hostConfig);

        if (spec.name() != null) {
            createCmd.withName(spec.name());
        }
        if (spec.env() != null && !spec.env().isEmpty()) {
            createCmd.withEnv(spec.env());
        }
        if (spec.cmd() != null && !spec.cmd().isEmpty()) {
            createCmd.withCmd(spec.cmd());
        }
        if (spec.entrypoint() != null && !spec.entrypoint().isEmpty()) {
            createCmd.withEntrypoint(spec.entrypoint());
        }
        if (spec.exposedPorts() != null && !spec.exposedPorts().isEmpty()) {
            ExposedPort[] exposed = spec.exposedPorts().stream()
                    .map(ExposedPort::tcp)
                    .toArray(ExposedPort[]::new);
            createCmd.withExposedPorts(exposed);
        }

        // Create the container
        CreateContainerResponse response = createCmd.exec();
        String containerId = response.getId();
        LOG.infov("Created container {0} (name={1})", containerId, spec.name());

        // Start the container
        dockerClient.startContainerCmd(containerId).exec();
        LOG.infov("Started container {0}", containerId);

        // Resolve endpoints
        Map<Integer, EndpointInfo> endpoints = resolveEndpoints(containerId, spec);

        return new ContainerInfo(containerId, endpoints);
    }

    /**
     * Stops and removes a container, closing any associated log stream.
     *
     * @param containerId the container ID to stop and remove
     * @param logStream optional log stream to close (may be null)
     */
    public void stopAndRemove(String containerId, Closeable logStream) {
        LOG.infov("Stopping container {0}", containerId);

        // Close log stream first
        if (logStream != null) {
            try {
                logStream.close();
            } catch (Exception e) {
                LOG.debugv("Error closing log stream: {0}", e.getMessage());
            }
        }

        // Stop container
        try {
            dockerClient.stopContainerCmd(containerId).withTimeout(5).exec();
        } catch (NotFoundException e) {
            LOG.debugv("Container {0} not found (already removed)", containerId);
            return;
        } catch (Exception e) {
            LOG.warnv("Error stopping container {0}: {1}", containerId, e.getMessage());
        }

        // Remove container
        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            LOG.debugv("Removed container {0}", containerId);
        } catch (NotFoundException e) {
            // Already gone
        } catch (Exception e) {
            LOG.warnv("Error removing container {0}: {1}", containerId, e.getMessage());
        }
    }

    /**
     * Finds an existing container by name.
     *
     * @param name the container name to search for
     * @return the container if found
     */
    public Optional<Container> findByName(String name) {
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .exec();

            for (Container c : containers) {
                String[] names = c.getNames();
                if (names == null) {
                    continue;
                }
                for (String n : names) {
                    // Docker prefixes names with /
                    if (n.equals("/" + name) || n.equals(name)) {
                        return Optional.of(c);
                    }
                }
            }
        } catch (Exception e) {
            LOG.debugv("Error searching for container {0}: {1}", name, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Adopts an existing container, starting it if stopped.
     * Useful for services like ECR that reuse containers across restarts.
     *
     * @param containerId the container ID to adopt
     * @param ports the container ports to resolve endpoints for
     * @return information about the adopted container
     */
    public ContainerInfo adopt(String containerId, List<Integer> ports) {
        LOG.infov("Adopting existing container {0}", containerId);

        InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId).exec();
        boolean running = Boolean.TRUE.equals(inspect.getState().getRunning());

        if (!running) {
            dockerClient.startContainerCmd(containerId).exec();
            LOG.infov("Started adopted container {0}", containerId);
            inspect = dockerClient.inspectContainerCmd(containerId).exec();
        }

        Map<Integer, EndpointInfo> endpoints = new HashMap<>();
        for (int port : ports) {
            endpoints.put(port, resolveEndpoint(inspect, port));
        }

        return new ContainerInfo(containerId, endpoints);
    }

    /**
     * Removes a container by name if it exists. Useful for cleaning up stale containers
     * from previous runs before creating a new one.
     *
     * @param name the container name to remove
     */
    public void removeIfExists(String name) {
        try {
            dockerClient.removeContainerCmd(name).withForce(true).exec();
            LOG.infov("Removed stale container {0}", name);
        } catch (NotFoundException e) {
            // Not found - normal case
        } catch (Exception e) {
            LOG.debugv("Could not remove container {0}: {1}", name, e.getMessage());
        }
    }

    /**
     * Resolves the endpoint (host and port) to connect to a specific container port.
     *
     * @param containerId the container ID
     * @param containerPort the container port to resolve
     * @return the endpoint information
     */
    public EndpointInfo resolveEndpoint(String containerId, int containerPort) {
        InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId).exec();
        return resolveEndpoint(inspect, containerPort);
    }

    /**
     * Returns the underlying DockerClient for operations not covered by this manager.
     * Prefer using manager methods when available.
     */
    public DockerClient getDockerClient() {
        return dockerClient;
    }

    private HostConfig buildHostConfig(ContainerSpec spec) {
        HostConfig hostConfig = HostConfig.newHostConfig();

        // Memory limit
        if (spec.hasMemoryLimit()) {
            hostConfig.withMemory(spec.memoryBytes());
        }

        // Port bindings — services decide whether to request them based on their
        // own in-container-vs-native logic. When Floci runs inside Docker, most
        // backends are reached via the docker network IP, so services omit the
        // binding. ECR's sibling registry is the exception: it always publishes
        // its port because host-side docker clients (and CDK in compat tests)
        // connect via localhost:<hostPort>.
        if (spec.hasPortBindings()) {
            Ports ports = new Ports();
            for (Map.Entry<Integer, Integer> entry : spec.portBindings().entrySet()) {
                int containerPort = entry.getKey();
                int hostPort = entry.getValue();

                // 0 means dynamic allocation
                if (hostPort == 0) {
                    hostPort = portAllocator.allocateAny();
                }

                ports.bind(ExposedPort.tcp(containerPort), Ports.Binding.bindPort(hostPort));
                LOG.debugv("Port binding: {0} -> {1}", containerPort, hostPort);
            }
            hostConfig.withPortBindings(ports);
        }

        // Network mode
        if (spec.networkMode() != null && !spec.networkMode().isBlank()) {
            hostConfig.withNetworkMode(spec.networkMode());
        }

        // Mounts (named volumes, bind mounts)
        if (spec.mounts() != null && !spec.mounts().isEmpty()) {
            hostConfig.withMounts(spec.mounts());
        }

        // Legacy binds
        if (spec.binds() != null && !spec.binds().isEmpty()) {
            hostConfig.withBinds(spec.binds().toArray(new Bind[0]));
        }

        // Extra hosts (e.g., host.docker.internal on Linux)
        if (spec.extraHosts() != null && !spec.extraHosts().isEmpty()) {
            hostConfig.withExtraHosts(spec.extraHosts().toArray(new String[0]));
        }

        // Log configuration (log rotation)
        if (spec.hasLogConfig()) {
            hostConfig.withLogConfig(spec.logConfig());
        }

        return hostConfig;
    }

    private Map<Integer, EndpointInfo> resolveEndpoints(String containerId, ContainerSpec spec) {
        if (spec.exposedPorts() == null || spec.exposedPorts().isEmpty()) {
            return Map.of();
        }

        InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId).exec();
        Map<Integer, EndpointInfo> endpoints = new HashMap<>();

        for (int containerPort : spec.exposedPorts()) {
            endpoints.put(containerPort, resolveEndpoint(inspect, containerPort));
        }

        return endpoints;
    }

    private EndpointInfo resolveEndpoint(InspectContainerResponse inspect, int containerPort) {
        if (!containerDetector.isRunningInContainer()) {
            // Native mode: use localhost and the bound host port
            var bindings = inspect.getNetworkSettings().getPorts().getBindings();
            var binding = bindings.get(ExposedPort.tcp(containerPort));

            if (binding != null && binding.length > 0) {
                int hostPort = Integer.parseInt(binding[0].getHostPortSpec());
                return new EndpointInfo("localhost", hostPort);
            }
            // Fallback to container port
            return new EndpointInfo("localhost", containerPort);
        } else {
            // Container mode: use container IP on the docker network
            String containerIp = resolveContainerIp(inspect);
            return new EndpointInfo(containerIp, containerPort);
        }
    }

    private String resolveContainerIp(InspectContainerResponse inspect) {
        var networks = inspect.getNetworkSettings().getNetworks();
        if (networks != null) {
            for (Map.Entry<String, ContainerNetwork> entry : networks.entrySet()) {
                String ip = entry.getValue().getIpAddress();
                if (ip != null && !ip.isBlank()) {
                    return ip;
                }
            }
        }
        // Fallback to the global IP
        return inspect.getNetworkSettings().getIpAddress();
    }

    /**
     * Information about a created or adopted container.
     *
     * @param containerId the Docker container ID
     * @param endpoints map of container port to resolved endpoint (host:port for connection)
     */
    public record ContainerInfo(
            String containerId,
            Map<Integer, EndpointInfo> endpoints
    ) {
        /**
         * Gets the endpoint for a specific container port.
         */
        public EndpointInfo getEndpoint(int containerPort) {
            return endpoints.get(containerPort);
        }
    }

    /**
     * Network endpoint information for connecting to a container.
     *
     * @param host the host to connect to (localhost in native mode, container IP in Docker mode)
     * @param port the port to connect to
     */
    public record EndpointInfo(String host, int port) {
        @Override
        public String toString() {
            return host + ":" + port;
        }
    }
}
