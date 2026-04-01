package io.github.hectorvent.floci.lifecycle;

import io.github.hectorvent.floci.config.EmulatorConfig;
import io.github.hectorvent.floci.core.common.ServiceRegistry;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import io.github.hectorvent.floci.lifecycle.inithook.InitializationHook;
import io.github.hectorvent.floci.lifecycle.inithook.InitializationHooksRunner;
import io.github.hectorvent.floci.services.elasticache.proxy.ElastiCacheProxyManager;
import io.github.hectorvent.floci.services.rds.proxy.RdsProxyManager;
import io.quarkus.runtime.StartupEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmulatorLifecycleTest {

    @Mock private StorageFactory storageFactory;
    @Mock private ServiceRegistry serviceRegistry;
    @Mock private EmulatorConfig config;
    @Mock private EmulatorConfig.StorageConfig storageConfig;
    @Mock private ElastiCacheProxyManager elastiCacheProxyManager;
    @Mock private RdsProxyManager rdsProxyManager;
    @Mock private InitializationHooksRunner initializationHooksRunner;

    private EmulatorLifecycle emulatorLifecycle;

    @BeforeEach
    void setUp() {
        when(config.storage()).thenReturn(storageConfig);
        when(storageConfig.mode()).thenReturn("in-memory");
        when(storageConfig.persistentPath()).thenReturn("/app/data");
        emulatorLifecycle = new EmulatorLifecycle(
                storageFactory, serviceRegistry, config,
                elastiCacheProxyManager, rdsProxyManager, initializationHooksRunner);
    }

    @Test
    @DisplayName("Should log Ready immediately when no startup hooks exist")
    void shouldLogReadyImmediatelyWhenNoHooksExist() throws IOException, InterruptedException {
        when(initializationHooksRunner.hasHooks(InitializationHook.START)).thenReturn(false);

        emulatorLifecycle.onStart(Mockito.mock(StartupEvent.class));

        verify(storageFactory).loadAll();
        verify(initializationHooksRunner).hasHooks(InitializationHook.START);
        verify(initializationHooksRunner, never()).run(InitializationHook.START);
    }

    @Test
    @DisplayName("Should defer hook execution when startup hooks exist")
    void shouldDeferHookExecutionWhenHooksExist() throws IOException, InterruptedException {
        when(initializationHooksRunner.hasHooks(InitializationHook.START)).thenReturn(true);

        emulatorLifecycle.onStart(Mockito.mock(StartupEvent.class));

        verify(storageFactory).loadAll();
        verify(initializationHooksRunner).hasHooks(InitializationHook.START);
        // run() is NOT called synchronously — it will be called by the virtual thread
        verify(initializationHooksRunner, never()).run(InitializationHook.START);
    }
}
