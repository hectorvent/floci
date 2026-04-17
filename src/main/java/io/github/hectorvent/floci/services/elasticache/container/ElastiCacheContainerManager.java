package io.github.hectorvent.floci.services.elasticache.container;

import io.github.hectorvent.floci.config.EmulatorConfig;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.common.docker.ContainerBuilder;
import io.github.hectorvent.floci.core.common.docker.ContainerDetector;
import io.github.hectorvent.floci.core.common.docker.ContainerLifecycleManager;
import io.github.hectorvent.floci.core.common.docker.ContainerLifecycleManager.ContainerInfo;
import io.github.hectorvent.floci.core.common.docker.ContainerLifecycleManager.EndpointInfo;
import io.github.hectorvent.floci.core.common.docker.ContainerLogStreamer;
import io.github.hectorvent.floci.core.common.docker.ContainerSpec;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages backend Docker container lifecycle for ElastiCache replication groups.
 * In native (dev) mode, binds container port 6379 to a random host port.
 * In Docker mode, uses the container's internal network IP directly.
 */
@ApplicationScoped
public class ElastiCacheContainerManager {

    private static final Logger LOG = Logger.getLogger(ElastiCacheContainerManager.class);
    private static final int BACKEND_PORT = 6379;

    private final ContainerBuilder containerBuilder;
    private final ContainerLifecycleManager lifecycleManager;
    private final ContainerLogStreamer logStreamer;
    private final ContainerDetector containerDetector;
    private final EmulatorConfig config;
    private final RegionResolver regionResolver;
    private final Map<String, ElastiCacheContainerHandle> activeContainers = new ConcurrentHashMap<>();

    @Inject
    public ElastiCacheContainerManager(ContainerBuilder containerBuilder,
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

    public ElastiCacheContainerHandle start(String groupId, String image) {
        LOG.infov("Starting ElastiCache backend container for group: {0}", groupId);

        String containerName = "floci-valkey-" + groupId;

        // Remove any stale container with the same name
        lifecycleManager.removeIfExists(containerName);

        // Build container spec. Only publish the backend port to the host in
        // native mode — in Docker mode the JVM reaches the container via its
        // network IP, no host binding needed.
        ContainerBuilder.Builder specBuilder = containerBuilder.newContainer(image)
                .withName(containerName)
                .withEnv("VALKEY_EXTRA_FLAGS", "--loglevel verbose")
                .withDockerNetwork(config.services().elasticache().dockerNetwork())
                .withLogRotation();

        if (!containerDetector.isRunningInContainer()) {
            specBuilder.withDynamicPort(BACKEND_PORT);
        } else {
            specBuilder.withExposedPort(BACKEND_PORT);
        }

        ContainerSpec spec = specBuilder.build();

        // Create and start container
        ContainerInfo info = lifecycleManager.createAndStart(spec);
        EndpointInfo endpoint = info.getEndpoint(BACKEND_PORT);

        LOG.infov("ElastiCache backend for group {0}: {1}", groupId, endpoint);

        ElastiCacheContainerHandle handle = new ElastiCacheContainerHandle(
                info.containerId(), groupId, endpoint.host(), endpoint.port());
        activeContainers.put(groupId, handle);

        // Attach log streaming
        String shortId = info.containerId().length() >= 8
                ? info.containerId().substring(0, 8)
                : info.containerId();
        String logGroup = "/aws/elasticache/cluster/" + groupId + "/engine-log";
        String logStream = logStreamer.generateLogStreamName(shortId);
        String region = regionResolver.getDefaultRegion();

        Closeable logHandle = logStreamer.attach(
                info.containerId(), logGroup, logStream, region, "elasticache:" + groupId);
        handle.setLogStream(logHandle);

        return handle;
    }

    public void stop(ElastiCacheContainerHandle handle) {
        if (handle == null) {
            return;
        }
        activeContainers.remove(handle.getGroupId());
        lifecycleManager.stopAndRemove(handle.getContainerId(), handle.getLogStream());
    }

    public void stopAll() {
        List<ElastiCacheContainerHandle> handles = new ArrayList<>(activeContainers.values());
        if (!handles.isEmpty()) {
            LOG.infov("Stopping {0} ElastiCache container(s) on shutdown", handles.size());
        }
        for (ElastiCacheContainerHandle handle : handles) {
            stop(handle);
        }
    }
}
