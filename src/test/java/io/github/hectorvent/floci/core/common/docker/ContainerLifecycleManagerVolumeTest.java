package io.github.hectorvent.floci.core.common.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectVolumeCmd;
import com.github.dockerjava.api.command.InspectVolumeResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import io.github.hectorvent.floci.services.lambda.launcher.ImageCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContainerLifecycleManagerVolumeTest {

    @Mock
    private DockerClient dockerClient;

    @Mock
    private ImageCacheService imageCacheService;

    @Mock
    private ContainerDetector containerDetector;

    @Mock
    private PortAllocator portAllocator;

    private ContainerLifecycleManager manager;

    @BeforeEach
    void setUp() {
        manager = new ContainerLifecycleManager(dockerClient, imageCacheService, containerDetector, portAllocator);
    }

    @Test
    void volumeExists_returnsTrue_whenVolumeExists() {
        InspectVolumeCmd cmd = mock(InspectVolumeCmd.class);
        when(dockerClient.inspectVolumeCmd("my-volume")).thenReturn(cmd);
        when(cmd.exec()).thenReturn(mock(InspectVolumeResponse.class));

        assertTrue(manager.volumeExists("my-volume"));
    }

    @Test
    void volumeExists_returnsFalse_whenVolumeNotFound() {
        InspectVolumeCmd cmd = mock(InspectVolumeCmd.class);
        when(dockerClient.inspectVolumeCmd("nonexistent")).thenReturn(cmd);
        when(cmd.exec()).thenThrow(new NotFoundException("No such volume"));

        assertFalse(manager.volumeExists("nonexistent"));
    }

    @Test
    void volumeExists_returnsFalse_forNullName() {
        assertFalse(manager.volumeExists(null));
        verifyNoInteractions(dockerClient);
    }

    @Test
    void volumeExists_returnsFalse_forBlankName() {
        assertFalse(manager.volumeExists("  "));
        verifyNoInteractions(dockerClient);
    }

    @Test
    void volumeExists_returnsFalse_forAbsolutePath() {
        assertFalse(manager.volumeExists("/var/lib/data"));
        verifyNoInteractions(dockerClient);
    }

    @Test
    void volumeExists_returnsFalse_forRelativePath() {
        assertFalse(manager.volumeExists("./data"));
        verifyNoInteractions(dockerClient);
    }

    @Test
    void volumeExists_returnsFalse_forWindowsAbsolutePathBackslash() {
        assertFalse(manager.volumeExists("C:\\Users\\data"));
        verifyNoInteractions(dockerClient);
    }

    @Test
    void volumeExists_returnsFalse_forWindowsAbsolutePathForwardSlash() {
        assertFalse(manager.volumeExists("D:/sources/data"));
        verifyNoInteractions(dockerClient);
    }

    @Test
    void volumeExists_returnsFalse_onDockerException() {
        InspectVolumeCmd cmd = mock(InspectVolumeCmd.class);
        when(dockerClient.inspectVolumeCmd("some-volume")).thenReturn(cmd);
        when(cmd.exec()).thenThrow(new DockerException("Connection refused", 500));

        assertFalse(manager.volumeExists("some-volume"));
    }
}

