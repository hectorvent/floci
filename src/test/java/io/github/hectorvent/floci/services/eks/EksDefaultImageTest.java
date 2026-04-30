package io.github.hectorvent.floci.services.eks;

import io.github.hectorvent.floci.config.EmulatorConfig;
import io.github.hectorvent.floci.core.common.docker.ContainerBuilder;
import io.github.hectorvent.floci.core.common.docker.ContainerLifecycleManager;
import io.github.hectorvent.floci.core.common.docker.ContainerDetector;
import io.github.hectorvent.floci.core.common.docker.ContainerSpec;
import io.github.hectorvent.floci.core.common.docker.PortAllocator;
import io.smallrye.config.WithDefault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

import io.github.hectorvent.floci.services.eks.model.Cluster;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bug condition exploration and preservation tests for EksServiceConfig.defaultImage().
 *
 * Property 1: Bug Condition — Default Image Is Not Pinned
 *
 * These tests are EXPECTED TO FAIL on unfixed code.
 * Failure confirms the bug exists: defaultImage() returns "rancher/k3s:latest"
 * instead of the pinned tag "rancher/k3s:v1.33.10-k3s1".
 *
 * Property 2: Preservation — User-Supplied Image Override Is Respected
 *
 * These tests PASS on both unfixed and fixed code.
 * They confirm that when a user supplies a custom image override via
 * eksConfig.defaultImage(), EksClusterManager.startCluster() passes it through unchanged.
 */
class EksDefaultImageTest {

    private static final String EXPECTED_PINNED_IMAGE = "rancher/k3s:v1.33.10-k3s1";
    private static final String BUGGY_LATEST_IMAGE = "rancher/k3s:latest";

    // -------------------------------------------------------------------------
    // Preservation test infrastructure
    // -------------------------------------------------------------------------

    private EksClusterManager clusterManager;
    private EmulatorConfig.EksServiceConfig eksConfig;
    private ContainerBuilder containerBuilder;
    private ContainerBuilder.Builder builderChain;

    @BeforeEach
    void setUpPreservationInfrastructure() {
        // Mock the full EmulatorConfig hierarchy
        EmulatorConfig config = Mockito.mock(EmulatorConfig.class);
        EmulatorConfig.ServicesConfig servicesConfig = Mockito.mock(EmulatorConfig.ServicesConfig.class);
        eksConfig = Mockito.mock(EmulatorConfig.EksServiceConfig.class);
        EmulatorConfig.DockerConfig dockerConfig = Mockito.mock(EmulatorConfig.DockerConfig.class);

        when(config.services()).thenReturn(servicesConfig);
        when(servicesConfig.eks()).thenReturn(eksConfig);
        when(servicesConfig.dockerNetwork()).thenReturn(Optional.empty());
        when(eksConfig.dockerNetwork()).thenReturn(Optional.empty());
        when(eksConfig.apiServerBasePort()).thenReturn(6500);
        when(eksConfig.apiServerMaxPort()).thenReturn(6599);
        when(config.docker()).thenReturn(dockerConfig);
        when(dockerConfig.logMaxSize()).thenReturn("10m");
        when(dockerConfig.logMaxFile()).thenReturn("3");

        // Mock PortAllocator to return a fixed port
        PortAllocator portAllocator = Mockito.mock(PortAllocator.class);
        when(portAllocator.allocate(anyInt(), anyInt())).thenReturn(6500);

        // Mock ContainerLifecycleManager
        ContainerLifecycleManager lifecycleManager = Mockito.mock(ContainerLifecycleManager.class);
        ContainerLifecycleManager.ContainerInfo containerInfo =
                new ContainerLifecycleManager.ContainerInfo("test-container-id", Map.of());
        when(lifecycleManager.createAndStart(any())).thenReturn(containerInfo);

        // Mock ContainerDetector
        ContainerDetector containerDetector = Mockito.mock(ContainerDetector.class);
        when(containerDetector.isRunningInContainer()).thenReturn(false);

        // Mock ContainerBuilder — capture the image argument via a spy on the builder chain
        containerBuilder = Mockito.mock(ContainerBuilder.class);
        builderChain = Mockito.mock(ContainerBuilder.Builder.class);

        // All builder chain methods return the same mock builder (fluent API)
        when(containerBuilder.newContainer(anyString())).thenReturn(builderChain);
        when(builderChain.withName(anyString())).thenReturn(builderChain);
        when(builderChain.withCmd(Mockito.<java.util.List<String>>any())).thenReturn(builderChain);
        when(builderChain.withEnv(anyString(), anyString())).thenReturn(builderChain);
        when(builderChain.withPortBinding(anyInt(), anyInt())).thenReturn(builderChain);
        when(builderChain.withNamedVolume(anyString(), anyString())).thenReturn(builderChain);
        when(builderChain.withDockerNetwork(any())).thenReturn(builderChain);
        when(builderChain.withPrivileged(Mockito.anyBoolean())).thenReturn(builderChain);
        when(builderChain.withLogRotation()).thenReturn(builderChain);
        when(builderChain.build()).thenReturn(new ContainerSpec("placeholder-image"));

        clusterManager = new EksClusterManager(
                containerBuilder, lifecycleManager, containerDetector, portAllocator, config);
    }

    /**
     * Reads the @WithDefault annotation value from EksServiceConfig.defaultImage() via reflection.
     * This is the actual default that Quarkus will use when no user override is configured.
     */
    private String readDefaultImageAnnotationValue() throws NoSuchMethodException {
        Method method = EmulatorConfig.EksServiceConfig.class.getMethod("defaultImage");
        WithDefault annotation = method.getAnnotation(WithDefault.class);
        assertNotNull(annotation, "@WithDefault annotation must be present on defaultImage()");
        return annotation.value();
    }

    /**
     * Test 1: Assert that the @WithDefault value for defaultImage() is the pinned tag.
     *
     * EXPECTED TO FAIL on unfixed code — the annotation currently reads "rancher/k3s:latest".
     * Counterexample: defaultImage() returns "rancher/k3s:latest" instead of "rancher/k3s:v1.33.10-k3s1"
     */
    @Test
    void defaultImageShouldBePinnedTag() throws NoSuchMethodException {
        String actualDefault = readDefaultImageAnnotationValue();

        assertEquals(
                EXPECTED_PINNED_IMAGE,
                actualDefault,
                "defaultImage() @WithDefault must be the pinned tag \"" + EXPECTED_PINNED_IMAGE
                        + "\" but was \"" + actualDefault + "\". "
                        + "This confirms the bug: the floating 'latest' tag resolves to k3s v1.34.x "
                        + "which rejects --storage-backend=sqlite3."
        );
    }

    /**
     * Test 2: Assert that the @WithDefault value for defaultImage() is NOT "rancher/k3s:latest".
     *
     * EXPECTED TO FAIL on unfixed code — the annotation currently reads "rancher/k3s:latest".
     * Counterexample: defaultImage() returns "rancher/k3s:latest" (the floating tag that causes the bug)
     */
    @Test
    void defaultImageShouldNotBeLatestTag() throws NoSuchMethodException {
        String actualDefault = readDefaultImageAnnotationValue();

        assertNotEquals(
                BUGGY_LATEST_IMAGE,
                actualDefault,
                "defaultImage() @WithDefault must NOT be \"" + BUGGY_LATEST_IMAGE + "\". "
                        + "The 'latest' tag is a floating reference that silently moved to k3s v1.34.x, "
                        + "which no longer accepts --storage-backend=sqlite3, leaving EKS clusters "
                        + "permanently stuck in CREATING state."
        );
    }

    /**
     * Property 2: Preservation — User-Supplied Image Override Is Respected
     *
     * For any arbitrary valid image tag string, when eksConfig.defaultImage() is stubbed
     * to return that tag, EksClusterManager.startCluster() passes it through unchanged
     * to ContainerBuilder.newContainer().
     *
     * EXPECTED TO PASS on unfixed code — the override mechanism is independent of the
     * @WithDefault annotation value.
     */
    @ParameterizedTest(name = "startCluster passes image override unchanged: {0}")
    @ValueSource(strings = {
            "rancher/k3s:v1.32.0-k3s1",
            "rancher/k3s:v1.31.5-k3s1",
            "custom/k3s:abc123"
    })
    void startClusterPassesImageOverrideUnchanged(String overrideImage) {
        // Stub eksConfig.defaultImage() to return the parameterized override value
        when(eksConfig.defaultImage()).thenReturn(overrideImage);

        Cluster cluster = new Cluster();
        cluster.setName("test-cluster");

        // Call startCluster — this should use the stubbed image
        clusterManager.startCluster(cluster);

        // Capture the image argument passed to ContainerBuilder.newContainer()
        ArgumentCaptor<String> imageCaptor = ArgumentCaptor.forClass(String.class);
        verify(containerBuilder).newContainer(imageCaptor.capture());

        String actualImage = imageCaptor.getValue();
        assertEquals(
                overrideImage,
                actualImage,
                "EksClusterManager.startCluster() must pass the user-supplied image override \""
                        + overrideImage + "\" to ContainerBuilder.newContainer() unchanged, "
                        + "but received \"" + actualImage + "\". "
                        + "The override mechanism must be preserved after the fix."
        );
    }
}
