package local.bedwarstab;

import java.lang.instrument.Instrumentation;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.jar.JarFile;
import local.bedwarstab.config.DataPaths;
import local.bedwarstab.config.RuntimeControl;

public final class Boot {
    private static URLClassLoader coreLoader;
    private static JarFile bridgeJar;
    private static Path loadedRoot;
    private static volatile Thread startupThread;
    private static RuntimeControl control;
    private static boolean active;
    /** Standard JVM startup agent: wait until Minecraft has created its game loader. */
    public static void premain(String options, Instrumentation instrumentation) {
        try {
            Path root = Path.of(options).toRealPath();
            startupLog("Startup agent loaded; waiting for Minecraft 1.8.9 classes");
            Thread thread = new Thread(() -> {
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(30);
                try {
                    while (!Thread.currentThread().isInterrupted() && System.nanoTime() < deadline) {
                        boolean tab = false, minecraft = false;
                        for (Class<?> type : instrumentation.getAllLoadedClasses()) {
                            if (type.getName().equals("awh")) tab = true;
                            if (type.getName().equals("ave")) minecraft = true;
                        }
                        if (tab && minecraft) {
                            synchronized (Boot.class) {
                                if (Thread.currentThread().isInterrupted()) return;
                                if (Boolean.getBoolean("bedwarstab.manualActivation")) {
                                    openControl(root, instrumentation);
                                    startupLog("App control ready; waiting for Inject"); return;
                                }
                                if (coreLoader == null) agentmain("attach|" + root, instrumentation);
                            }
                            startupLog("Startup activation completed"); return;
                        }
                        Thread.sleep(500);
                    }
                    startupLog("No compatible Minecraft classes within 30 minutes");
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                catch (Exception | LinkageError e) { startupLog("Startup activation failed: " + e.getClass().getSimpleName()); }
            }, "BedwarsTab-Startup");
            thread.setDaemon(true); startupThread = thread; thread.start();
        } catch (Exception | LinkageError e) { startupLog("Startup agent initialization failed: " + e.getClass().getSimpleName()); }
    }
    private static void startupLog(String message) {
        try {
            Path directory = DataPaths.directory(); Files.createDirectories(directory);
            Files.writeString(directory.resolve("startup-" + ProcessHandle.current().pid() + ".log"),
                java.time.Instant.now() + " " + message + System.lineSeparator(),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) { }
    }
    public static synchronized void agentmain(String options, Instrumentation instrumentation) throws Exception {
        String[] parts = options == null ? new String[0] : options.split("\\|", 2);
        if (parts.length != 2 || (!parts[0].equals("attach") && !parts[0].equals("detach"))) throw new IllegalArgumentException("Ungültige Agent-Optionen");
        if (parts[0].equals("detach") && startupThread != null) startupThread.interrupt();
        Path root = Path.of(parts[1]).toRealPath();
        if (coreLoader != null && !root.equals(loadedRoot) && !parts[0].equals("detach"))
            throw new IllegalStateException("Eine andere Programmversion ist bereits geladen. Minecraft vor dem Versionswechsel neu starten.");
        if (coreLoader == null) {
            if (parts[0].equals("detach")) return;
            for (String file : new String[]{"core.jar", "bridge.jar", "screens.jar"})
                if (!Files.isRegularFile(root.resolve("dist/" + file))) throw new IllegalStateException("Unvollständiges Programmpaket: " + file);
            bridgeJar = new JarFile(root.resolve("dist/bridge.jar").toFile());
            instrumentation.appendToBootstrapClassLoaderSearch(bridgeJar);
            try {
                if (Class.forName("local.bedwarstab.bridge.Bridge", false, null).getField("API_VERSION").getInt(null) != 6)
                    throw new NoSuchFieldException("bridge version");
            } catch (ReflectiveOperationException e) { throw new IllegalStateException("Alte Bridge geladen; Minecraft neu starten", e); }
            // Keep ASM and Gson isolated from Minecraft's older dependencies.
            coreLoader = new URLClassLoader(new java.net.URL[] {
                root.resolve("dist/core.jar").toUri().toURL()
            }, ClassLoader.getPlatformClassLoader());
            loadedRoot = root;
        }
        try {
            active = false;
            coreLoader.loadClass("local.bedwarstab.core.Agent").getMethod("run", String.class,
                String.class, Instrumentation.class).invoke(null, parts[0], loadedRoot.toString(), instrumentation);
            active = parts[0].equals("attach");
            try { openControl(loadedRoot, instrumentation); }
            catch (Exception e) { startupLog("App control unavailable: " + e.getClass().getSimpleName()); }
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new IllegalStateException("Agent failed: " + e.getCause().getMessage(), e.getCause());
        }
    }
    private static void openControl(Path release, Instrumentation instrumentation) throws Exception {
        if (control != null) return;
        control = new RuntimeControl(DataPaths.directory(), release, action -> {
            synchronized (Boot.class) {
                if (action.equals("attach") && active)
                    return "Bereits aktiv. Gespeicherte Keys und Modus werden im Hintergrund übernommen; Tab öffnen.";
                agentmain(action + "|" + release, instrumentation);
                return action.equals("attach") ? "Erweiterung im laufenden Spiel aktiviert. Tab öffnen." : "Erweiterung deaktiviert. Erneutes Aktivieren mit Inject möglich.";
            }
        });
    }
}
