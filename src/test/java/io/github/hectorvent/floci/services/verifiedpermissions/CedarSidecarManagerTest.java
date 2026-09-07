package io.github.hectorvent.floci.services.verifiedpermissions;

import io.github.hectorvent.floci.config.EmulatorConfig;
import io.github.hectorvent.floci.core.common.docker.ContainerBuilder;
import io.github.hectorvent.floci.core.common.docker.ContainerLifecycleManager;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CedarSidecarManagerTest {

    @Test
    void configuredUrlSkipsContainerManagement() {
        ContainerBuilder containerBuilder = mock(ContainerBuilder.class);
        ContainerLifecycleManager lifecycleManager = mock(ContainerLifecycleManager.class);
        EmulatorConfig config = mock(EmulatorConfig.class);
        EmulatorConfig.ServicesConfig services = mock(EmulatorConfig.ServicesConfig.class);
        EmulatorConfig.VerifiedPermissionsServiceConfig verifiedPermissions =
                mock(EmulatorConfig.VerifiedPermissionsServiceConfig.class);

        when(config.services()).thenReturn(services);
        when(services.verifiedpermissions()).thenReturn(verifiedPermissions);
        when(verifiedPermissions.cedarUrl()).thenReturn(Optional.of("http://cedar.internal:8180/"));

        CedarSidecarManager manager = new CedarSidecarManager(containerBuilder, lifecycleManager, config);

        assertEquals("http://cedar.internal:8180", manager.ensureReady());
        verifyNoInteractions(containerBuilder, lifecycleManager);
    }
}
