package io.github.hectorvent.floci.lifecycle.inithook;

import java.io.File;
import java.util.List;

public enum InitializationHook {

    BOOT("boot", "boot",
         List.of("/etc/floci/init/boot.d"),
         List.of("/etc/localstack/init/boot.d")),
    START("startup", "start",
          List.of("/etc/floci/init/start.d"),
          List.of("/etc/localstack/init/start.d")),
    READY("ready", "ready",
          List.of("/etc/floci/init/ready.d"),
          List.of("/etc/localstack/init/ready.d")),
    STOP("shutdown", "shutdown",
         List.of("/etc/floci/init/stop.d", "/etc/floci/init/shutdown.d"),
         List.of("/etc/localstack/init/shutdown.d"));

    private final String name;
    private final String responseKey;
    private final List<File> primaryPaths;
    private final List<File> compatPaths;

    InitializationHook(String name, String responseKey, List<String> primaryPaths, List<String> compatPaths) {
        this.name = name;
        this.responseKey = responseKey;
        this.primaryPaths = primaryPaths.stream().map(File::new).toList();
        this.compatPaths = compatPaths.stream().map(File::new).toList();
    }

    public String getName() {
        return name;
    }

    /** Key used in the {@code /_floci/init} and {@code /_localstack/init} response body. */
    public String getResponseKey() {
        return responseKey;
    }

    /** Floci-native directories for this phase. First occurrence of a filename wins. */
    public List<File> getPrimaryPaths() {
        return primaryPaths;
    }

    /** LocalStack-compat directories for this phase. Only used when a filename is not already in a primary path. */
    public List<File> getCompatPaths() {
        return compatPaths;
    }
}
