package local.bedwarstab.core;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import local.bedwarstab.bridge.Bridge;

/** Only class names, loader identities and counters; never chat content or credentials. */
final class RuntimeDiagnostics implements AutoCloseable {
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "BedwarsTab-Diagnostics"); t.setDaemon(true); return t;
    });
    private final Instrumentation instrumentation;
    private final Class<?> minecraft;
    private final Path file;
    private final GameLoop gameLoop;
    private boolean closed;
    RuntimeDiagnostics(Instrumentation instrumentation, ClassLoader loader, Path root, GameLoop gameLoop) throws ClassNotFoundException {
        this.gameLoop = gameLoop;
        this.instrumentation = instrumentation;
        minecraft = Class.forName("ave", false, loader);
        file = root.resolve("runtime-" + ProcessHandle.current().pid() + ".properties");
    }
    void start() { worker.scheduleWithFixedDelay(this::snapshot, 1, 3, TimeUnit.SECONDS); }
    private static String type(Object object) {
        if (object == null) return "null";
        Class<?> c = object instanceof Class<?> ? (Class<?>) object : object.getClass();
        ClassLoader loader = c.getClassLoader();
        return c.getName() + " @ " + (loader == null ? "bootstrap" : loader.getClass().getName() + ":" + Integer.toHexString(System.identityHashCode(loader)));
    }
    private synchronized void snapshot() {
        if (closed) return;
        Properties p = new Properties();
        p.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
        p.setProperty("tick.calls", Long.toString(Bridge.tickCalls.get()));
        p.setProperty("tab.calls", Long.toString(Bridge.tabCalls.get()));
        p.setProperty("tab.outcome", Agent.tabOutcome);
        p.setProperty("chat.calls", Long.toString(Bridge.chatCalls.get()));
        p.setProperty("bridge.failure", Bridge.lastFailure);
        p.setProperty("queue.submitted", Long.toString(gameLoop.submitted.get()));
        p.setProperty("queue.executed", Long.toString(gameLoop.executed.get()));
        p.setProperty("queue.failure", gameLoop.failure);
        p.setProperty("selected.minecraft", type(minecraft));
        try {
            Object mc = minecraft.getMethod("A").invoke(null);
            p.setProperty("minecraft.instance", type(mc));
            for (String name : new String[]{"f", "h", "m", "q"}) {
                Object value = minecraft.getField(name).get(mc);
                p.setProperty("minecraft.field." + name, type(value));
                if (name.equals("q") && value != null) for (Class<?> c = value.getClass(); c != null; c = c.getSuperclass()) {
                    for (Field field : c.getDeclaredFields()) if (!Modifier.isStatic(field.getModifiers()) && field.getType().getName().equals("awh")) {
                        field.setAccessible(true); p.setProperty("tab.instance", type(field.get(value)));
                    }
                }
            }
            Class<?> bridge = Class.forName("local.bedwarstab.bridge.Bridge", false, minecraft.getClassLoader());
            p.setProperty("bridge.gameLoaderIdentityMatches", Boolean.toString(bridge == Bridge.class));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { p.setProperty("inspection.failure", e.getClass().getName()); }
        int index = 0;
        for (Class<?> c : instrumentation.getAllLoadedClasses()) {
            for (Class<?> parent = c; parent != null; parent = parent.getSuperclass()) {
                if (Set.of("ave", "awh", "awv", "bcy").contains(parent.getName())) {
                    p.setProperty("class." + index++, type(c) + " extends " + type(c.getSuperclass())); break;
                }
            }
        }
        try {
            Path pending = Files.createTempFile(file.getParent(), "runtime-", ".tmp");
            try {
                try (var out = Files.newBufferedWriter(pending)) { p.store(out, "BedwarsTab hook diagnostics; no credentials or chat text"); }
                Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(pending); }
        } catch (Exception ignored) { }
    }
    public synchronized void close() { closed = true; worker.shutdownNow(); }
}
