package io.github.hectorvent.floci.services.msk;

import io.github.hectorvent.floci.config.EmulatorConfig;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.common.docker.ContainerBuilder;
import io.github.hectorvent.floci.core.common.docker.ContainerDetector;
import io.github.hectorvent.floci.core.common.docker.ContainerLifecycleManager;
import io.github.hectorvent.floci.core.common.docker.ContainerLifecycleManager.ContainerInfo;
import io.github.hectorvent.floci.core.common.docker.ContainerLifecycleManager.EndpointInfo;
import io.github.hectorvent.floci.core.common.docker.ContainerLogStreamer;
import io.github.hectorvent.floci.core.common.docker.ContainerSpec;
import io.github.hectorvent.floci.services.msk.model.MskCluster;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class RedpandaManager {

    private static final Logger LOG = Logger.getLogger(RedpandaManager.class);
    private static final int KAFKA_PORT = 9092;
    private static final int ADMIN_PORT = 9644;

    private final ContainerBuilder containerBuilder;
    private final ContainerLifecycleManager lifecycleManager;
    private final ContainerLogStreamer logStreamer;
    private final ContainerDetector containerDetector;
    private final EmulatorConfig config;
    private final RegionResolver regionResolver;
    private final Map<String, Closeable> logStreams = new ConcurrentHashMap<>();

    @Inject
    public RedpandaManager(ContainerBuilder containerBuilder,
                           ContainerLifecycleManager lifecycleManager,
                           ContainerLogStreamer logStreamer,
                           ContainerDetector containerDetector,
                           EmulatorConfig config,
                           RegionResolver regionResolver) {
        this.containerBuilder = containerBuilder;
        this.lifecycleManager = lifecycleManager;
        this.logStreamer = logStreamer;
        this.containerDetector = containerDetector;
        this.config = config;
        this.regionResolver = regionResolver;
    }

    public void startContainer(MskCluster cluster) {
        String image = config.services().msk().defaultImage();
        LOG.infov("Starting Redpanda container for MSK cluster: {0} using image {1}", cluster.getClusterName(), image);

        String containerName = "floci-msk-" + cluster.getClusterName();

        // Ensure internal persistence path exists
        Path dataPath = Path.of(config.storage().persistentPath(), "msk", cluster.getClusterName());
        try {
            Files.createDirectories(dataPath);
        } catch (IOException e) {
            LOG.errorv("Failed to create MSK data directory: {0}", dataPath, e);
        }

        // Cleanup stale container
        lifecycleManager.removeIfExists(containerName);

        // Build command
        List<String> cmd = new ArrayList<>(List.of(
                "redpanda", "start", "--overprovisioned", "--smp", "1",
                "--memory", "512M", "--reserve-memory", "0M"));

        // Build container spec. Publish Kafka/admin ports to the host only in
        // native mode; in Docker mode producers/consumers reach the broker via
        // the docker network IP resolved from container inspect.
        ContainerBuilder.Builder specBuilder = containerBuilder.newContainer(image)
                .withName(containerName)
                .withDockerNetwork(config.services().dockerNetwork())
                .withLogRotation();

        if (!containerDetector.isRunningInContainer()) {
            specBuilder.withDynamicPort(KAFKA_PORT).withDynamicPort(ADMIN_PORT);
        } else {
            specBuilder.withExposedPort(KAFKA_PORT).withExposedPort(ADMIN_PORT);
        }

        // Handle persistence mounting
        String hostPersistentPath = config.storage().hostPersistentPath();
        boolean isVolume = !hostPersistentPath.startsWith("/") && !hostPersistentPath.startsWith(".");

        if (isVolume) {
            // Volume mode: mount the whole volume and point Redpanda to the subdirectory
            String internalMountPath = "/app/data";
            specBuilder.withBind(hostPersistentPath, internalMountPath);
            cmd.add("--data-dir");
            cmd.add(internalMountPath + "/msk/" + cluster.getClusterName());
        } else {
            // Directory mode: mount the specific subdirectory directly
            String hostDataPath = Path.of(hostPersistentPath, "msk", cluster.getClusterName())
                    .toAbsolutePath().toString();
            specBuilder.withBind(hostDataPath, "/var/lib/redpanda/data");
        }

        specBuilder.withCmd(cmd);
        ContainerSpec spec = specBuilder.build();

        // Create and start container
        ContainerInfo info = lifecycleManager.createAndStart(spec);
        cluster.setContainerId(info.containerId());

        // Resolve endpoints
        EndpointInfo kafkaEndpoint = info.getEndpoint(KAFKA_PORT);

        cluster.setBootstrapBrokers(kafkaEndpoint.host() + ":" + kafkaEndpoint.port());
        LOG.infov("Redpanda container {0} started. Bootstrap: {1}", info.containerId(), cluster.getBootstrapBrokers());

        // Attach log streaming (new feature)
        String shortId = info.containerId().length() >= 8
                ? info.containerId().substring(0, 8)
                : info.containerId();
        String logGroup = "/aws/msk/cluster/" + cluster.getClusterName();
        String logStream = logStreamer.generateLogStreamName(shortId);
        String region = regionResolver.getDefaultRegion();

        Closeable logHandle = logStreamer.attach(
                info.containerId(), logGroup, logStream, region, "msk:" + cluster.getClusterName());
        if (logHandle != null) {
            logStreams.put(cluster.getClusterName(), logHandle);
        }
    }

    public boolean isReady(MskCluster cluster) {
        String bootstrap = cluster.getBootstrapBrokers();
        if (bootstrap == null) {
            return false;
        }

        // Derive admin URL from the container
        String adminUrl;
        if (!containerDetector.isRunningInContainer()) {
            var dockerClient = lifecycleManager.getDockerClient();
            var inspect = dockerClient.inspectContainerCmd(cluster.getContainerId()).exec();
            var bindings = inspect.getNetworkSettings().getPorts().getBindings();
            var binding = bindings.get(com.github.dockerjava.api.model.ExposedPort.tcp(ADMIN_PORT));
            if (binding != null && binding.length > 0) {
                adminUrl = "http://localhost:" + binding[0].getHostPortSpec() + "/ready";
            } else {
                return false;
            }
        } else {
            String containerIp = bootstrap.split(":")[0];
            adminUrl = "http://" + containerIp + ":" + ADMIN_PORT + "/ready";
        }

        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(adminUrl).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public void stopContainer(MskCluster cluster) {
        if (cluster.getContainerId() == null) {
            return;
        }

        // Close log stream
        Closeable logHandle = logStreams.remove(cluster.getClusterName());

        lifecycleManager.stopAndRemove(cluster.getContainerId(), logHandle);
        LOG.infov("Redpanda container {0} stopped and removed", cluster.getContainerId());
    }
}
