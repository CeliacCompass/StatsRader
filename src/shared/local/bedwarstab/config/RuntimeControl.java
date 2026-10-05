package local.bedwarstab.config;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Local, per-process mailbox. Only an already loaded agent can serve it. No keys or sockets. */
public final class RuntimeControl implements AutoCloseable {
    @FunctionalInterface public interface Handler { String execute(String action) throws Exception; }
    private final Path directory, descriptor;
    private final Properties identity = new Properties();
    private final Handler handler;
    private final ScheduledExecutorService worker;
    private long published;
    private volatile boolean closed;

    public RuntimeControl(Path data, Path release, Handler handler) throws IOException {
        this.handler = handler;
        long pid = ProcessHandle.current().pid();
        directory = data.resolve("control-" + pid);
        Files.createDirectories(directory);
        descriptor = directory.resolve("agent.properties");
        identity.setProperty("protocol", "1");
        identity.setProperty("pid", Long.toString(pid));
        identity.setProperty("started", start(pid));
        identity.setProperty("session", UUID.randomUUID().toString());
        identity.setProperty("release", release.toRealPath().toString());
        publish();
        worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BedwarsTab-Control"); t.setDaemon(true); return t;
        });
        worker.scheduleWithFixedDelay(this::poll, 200, 200, TimeUnit.MILLISECONDS);
    }
    private void publish() throws IOException {
        published = System.currentTimeMillis();
        identity.setProperty("heartbeat", Long.toString(published));
        write(descriptor, identity);
    }
    private void poll() {
        if (closed) return;
        try {
            if (System.currentTimeMillis() - published >= 1000) publish();
            Path request = directory.resolve("request.properties");
            if (!Files.isRegularFile(request)) return;
            Properties p = read(request);
            Files.deleteIfExists(request);
            String action = p.getProperty("action", "");
            if (!identity.getProperty("session").equals(p.getProperty("session")) ||
                !p.getProperty("id", "").matches("[a-f0-9-]{36}") ||
                !Set.of("attach", "detach").contains(action) ||
                Long.parseLong(p.getProperty("expires", "0")) < System.currentTimeMillis()) return;
            Properties result = new Properties();
            result.setProperty("id", p.getProperty("id"));
            try { result.setProperty("message", handler.execute(action)); result.setProperty("ok", "true"); }
            catch (Exception | LinkageError e) {
                result.setProperty("ok", "false");
                result.setProperty("message", "Aktion fehlgeschlagen: " + e.getClass().getSimpleName() + ". Siehe agent.log.");
            }
            write(directory.resolve("response.properties"), result);
            publish();
        } catch (IOException | RuntimeException ignored) { /* Retry transient file access on the next poll. */ }
    }
    public static boolean available(Path data, long pid) {
        try { return live(read(data.resolve("control-" + pid).resolve("agent.properties")), pid); }
        catch (IOException | RuntimeException e) { return false; }
    }
    private static boolean live(Properties p, long pid) {
        long age = System.currentTimeMillis() - Long.parseLong(p.getProperty("heartbeat", "0"));
        return "1".equals(p.getProperty("protocol")) && Long.toString(pid).equals(p.getProperty("pid")) &&
            !start(pid).isEmpty() && start(pid).equals(p.getProperty("started")) && age >= -1000 && age < 15000;
    }
    private static String start(long pid) {
        return ProcessHandle.of(pid).filter(ProcessHandle::isAlive).flatMap(p -> p.info().startInstant())
            .map(i -> Long.toString(i.toEpochMilli())).orElse("");
    }
    /** Returns null only if no live resident agent is available; caller may then try Java Attach. */
    public static String request(Path data, Path release, long pid, String action) throws Exception {
        if (!Set.of("attach", "detach").contains(action)) throw new IllegalArgumentException("Ungültige Aktion");
        Path directory = data.resolve("control-" + pid);
        Properties agent;
        try { agent = read(directory.resolve("agent.properties")); }
        catch (NoSuchFileException e) { return null; }
        if (!live(agent, pid)) return null;
        if (!release.toRealPath().equals(Path.of(agent.getProperty("release")).toRealPath()))
            throw new IOException("Andere Programmversion im Spiel geladen. Einmal über den zur App gehörenden Starter neu starten.");
        try (FileChannel channel = FileChannel.open(directory.resolve("request.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock;
            try { lock = channel.tryLock(); } catch (OverlappingFileLockException e) { lock = null; }
            if (lock == null) throw new IOException("Ein anderer App-Auftrag läuft bereits.");
            try {
                String id = UUID.randomUUID().toString();
                Properties request = new Properties();
                request.setProperty("id", id); request.setProperty("action", action);
                request.setProperty("session", agent.getProperty("session"));
                request.setProperty("expires", Long.toString(System.currentTimeMillis() + 20000));
                write(directory.resolve("request.properties"), request);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
                while (System.nanoTime() < deadline) {
                    if (!start(pid).equals(agent.getProperty("started"))) throw new IOException("Spiel wurde beendet.");
                    try {
                        Properties response = read(directory.resolve("response.properties"));
                        if (id.equals(response.getProperty("id"))) {
                            if (!"true".equals(response.getProperty("ok"))) throw new IOException(response.getProperty("message"));
                            return response.getProperty("message");
                        }
                    } catch (NoSuchFileException ignored) { }
                    Thread.sleep(100);
                }
                throw new IOException("Keine Bestätigung nach 20 Sekunden. Status unklar; Spiel und agent.log prüfen.");
            } finally { Files.deleteIfExists(directory.resolve("request.properties")); lock.release(); }
        }
    }
    private static Properties read(Path path) throws IOException {
        if (Files.size(path) > 8192) throw new IOException("Ungültige Steuerdatei");
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) { p.load(reader); }
        return p;
    }
    private static void write(Path path, Properties p) throws IOException {
        Path tmp = Files.createTempFile(path.getParent(), "control-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(tmp)) { p.store(writer, "Bedwars Tab control - no keys"); }
            try { Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }
    public void close() {
        closed = true; worker.shutdownNow();
        try { Files.deleteIfExists(descriptor); } catch (IOException ignored) { }
    }
}
