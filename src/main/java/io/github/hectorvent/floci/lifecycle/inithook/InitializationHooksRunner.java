package io.github.hectorvent.floci.lifecycle.inithook;

import io.github.hectorvent.floci.lifecycle.InitLifecycleState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class InitializationHooksRunner {

    private static final Logger LOG = Logger.getLogger(InitializationHooksRunner.class);

    private static final FilenameFilter SCRIPT_FILE_FILTER =
            (ignored, name) -> name.endsWith(".sh") || name.endsWith(".py");

    private final HookScriptExecutor hookScriptExecutor;
    private final InitLifecycleState initLifecycleState;

    @Inject
    public InitializationHooksRunner(final HookScriptExecutor hookScriptExecutor,
                                     final InitLifecycleState initLifecycleState) {
        this.hookScriptExecutor = hookScriptExecutor;
        this.initLifecycleState = initLifecycleState;
    }

    public boolean hasHooks(final InitializationHook hook) {
        return !findMergedScripts(hook).isEmpty();
    }

    public void run(final InitializationHook hook) throws IOException, InterruptedException {
        final List<File> scripts = findMergedScripts(hook);
        if (!scripts.isEmpty()) {
            LOG.infov("Running {0} hook with {1} script(s): {2}",
                    hook.getName(), scripts.size(),
                    scripts.stream().map(File::getAbsolutePath).toList());
            for (final File script : scripts) {
                LOG.infov("Executing {0} hook script: {1}", hook.getName(), script.getAbsolutePath());
                try {
                    hookScriptExecutor.run(script);
                    initLifecycleState.addScript(hook, script.getAbsolutePath(), "successful", 0);
                } catch (IllegalStateException e) {
                    initLifecycleState.addScript(hook, script.getAbsolutePath(), "error", parseExitCode(e));
                    throw e;
                }
            }
        }
    }

    private static int parseExitCode(IllegalStateException e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("exited with code ")) {
            try {
                return Integer.parseInt(msg.substring(msg.lastIndexOf(' ') + 1));
            } catch (NumberFormatException ignored) {
            }
        }
        return 1;
    }

    /**
     * Runs scripts from an arbitrary directory — kept for test utilities and direct invocations.
     */
    public void run(final String hookName, final File hookDirectory) throws IOException, InterruptedException {
        final String[] scriptFileNames = findScriptFileNames(hookName, hookDirectory);
        if (scriptFileNames.length > 0) {
            LOG.infov("Running {0} hook with {1} script(s) from {2}: {3}",
                    hookName, scriptFileNames.length, hookDirectory.getAbsolutePath(),
                    Arrays.toString(scriptFileNames));
            for (final String scriptFileName : scriptFileNames) {
                LOG.infov("Executing {0} hook script: {1}", hookName, scriptFileName);
                hookScriptExecutor.run(hookDirectory, scriptFileName);
            }
        }
    }

    /**
     * Merges scripts from all primary (Floci) paths and then compat (LocalStack) paths.
     * First occurrence of a filename wins; merged list is sorted lexicographically.
     */
    private static List<File> findMergedScripts(final InitializationHook hook) {
        final Map<String, File> merged = new LinkedHashMap<>();
        for (final File dir : hook.getPrimaryPaths()) {
            collectScripts(hook.getName(), dir, merged);
        }
        for (final File dir : hook.getCompatPaths()) {
            collectScripts(hook.getName(), dir, merged);
        }
        return merged.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    private static void collectScripts(final String hookName, final File dir,
                                       final Map<String, File> target) {
        if (!dir.exists()) {
            LOG.debugv("{0} hook directory does not exist: {1}", hookName, dir.getAbsolutePath());
            return;
        }
        if (!dir.isDirectory()) {
            LOG.warnv("{0} hook path is not a directory: {1}", hookName, dir.getAbsolutePath());
            return;
        }
        final File[] scripts = dir.listFiles(SCRIPT_FILE_FILTER);
        if (scripts == null || scripts.length == 0) {
            LOG.debugv("No {0} hook scripts found in {1}", hookName, dir.getAbsolutePath());
            return;
        }
        for (final File script : scripts) {
            if (target.putIfAbsent(script.getName(), script) == null) {
                LOG.debugv("Found {0} hook script: {1}", hookName, script.getAbsolutePath());
            } else {
                LOG.debugv("Skipping {0} (shadowed by higher-priority path)", script.getAbsolutePath());
            }
        }
    }

    private static String[] findScriptFileNames(final String hookName, final File hookDirectory) {
        if (!hookDirectory.exists()) {
            LOG.debugv("{0} hook directory does not exist: {1}", hookName, hookDirectory.getAbsolutePath());
            return new String[0];
        }
        if (!hookDirectory.isDirectory()) {
            LOG.warnv("{0} hook path is not a directory: {1}", hookName, hookDirectory.getAbsolutePath());
            return new String[0];
        }
        final String[] scriptFileNames = hookDirectory.list(SCRIPT_FILE_FILTER);
        if (scriptFileNames == null || scriptFileNames.length == 0) {
            LOG.debugv("No {0} hook scripts found in {1}", hookName, hookDirectory.getAbsolutePath());
            return new String[0];
        }
        Arrays.sort(scriptFileNames);
        return scriptFileNames;
    }
}
