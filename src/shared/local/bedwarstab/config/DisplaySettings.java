package local.bedwarstab.config;

import java.io.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.function.Consumer;
import java.util.*;

/** Immutable display selection, shared by launcher and in-game configuration. */
public final class DisplaySettings {
    public static final String BACKGROUND_OPACITY = "backgroundOpacity";
    public static int backgroundOpacity(Properties settings) {
        try { return Math.max(0, Math.min(100, Integer.parseInt(settings.getProperty(BACKGROUND_OPACITY, "100")))); }
        catch (NumberFormatException e) { return 100; }
    }
    public enum Category {
        STARS("showStars", "Sterne", true), FKDR("showFkdr", "FKDR", true),
        WINS("showWins", "Siege", true), WLR("showWlr", "WLR", false),
        FINAL_KILLS("showFinalKills", "Finale Kills", false), FINAL_DEATHS("showFinalDeaths", "Finale Tode", false),
        BEDS_BROKEN("showBedsBroken", "Betten zerstört", false), BEDS_LOST("showBedsLost", "Betten verloren", false),
        KDR("showKdr", "KDR", false), BLACKLIST("showBlacklist", "Seraph-Blacklist", true),
        BBLR("showBblr", "BBLR (Betten-Verhältnis)", true);
        public final String key, label;
        public final boolean defaultValue;
        Category(String key, String label, boolean defaultValue) { this.key = key; this.label = label; this.defaultValue = defaultValue; }
    }
    private final Set<Category> enabled;
    private final boolean statsEnabled;
    public DisplaySettings(Set<Category> enabled) { this.enabled = Set.copyOf(enabled); statsEnabled = enabled.stream().anyMatch(c -> c != Category.BLACKLIST); }
    public boolean enabled(Category category) { return enabled.contains(category); }
    public boolean anyStats() { return statsEnabled; }
    public boolean any() { return !enabled.isEmpty(); }
    public DisplaySettings toggle(Category category) {
        Set<Category> next = EnumSet.noneOf(Category.class); next.addAll(enabled);
        if (!next.remove(category)) next.add(category);
        return new DisplaySettings(next);
    }
    public static DisplaySettings defaults() { return from(new Properties()); }
    public static DisplaySettings from(Properties properties) {
        Set<Category> selected = EnumSet.noneOf(Category.class);
        for (Category c : Category.values()) if (Boolean.parseBoolean(properties.getProperty(c.key, Boolean.toString(c.defaultValue)))) selected.add(c);
        return new DisplaySettings(selected);
    }
    public static Properties read(Path directory) throws IOException {
        Properties p = new Properties(); Path file = directory.resolve("settings.properties");
        if (Files.isRegularFile(file)) try (Reader reader = Files.newBufferedReader(file)) { p.load(reader); }
        catch (IllegalArgumentException e) { throw new IOException("Ungültige Einstellungsdatei; Original wurde nicht verändert", e); }
        return p;
    }
    public void save(Path directory) throws IOException {
        update(directory, current -> {
            for (Category c : Category.values()) current.setProperty(c.key, Boolean.toString(enabled(c)));
        });
    }
    /** Lock the complete read/modify/write transaction across launcher and game JVMs. */
    public static synchronized void update(Path directory, Consumer<Properties> change) throws IOException {
        Files.createDirectories(directory);
        try (FileChannel channel = FileChannel.open(directory.resolve("settings.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock = null;
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
            while (lock == null && System.nanoTime() < deadline) {
                try { lock = channel.tryLock(); } catch (OverlappingFileLockException ignored) { }
                if (lock == null) try { Thread.sleep(10); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Speichern unterbrochen", e); }
            }
            if (lock == null) throw new IOException("Einstellungen werden gerade in einem anderen Fenster gespeichert");
            try {
                Properties current = read(directory); change.accept(current); writeAtomic(directory, current);
            } finally { lock.release(); }
        }
    }
    public static void write(Path directory, Properties properties) throws IOException {
        update(directory, current -> { current.clear(); current.putAll(properties); });
    }
    private static void writeAtomic(Path directory, Properties properties) throws IOException {
        Path file = directory.resolve("settings.properties");
        Path temp = Files.createTempFile(directory, "settings-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp)) { properties.store(writer, "Bedwars Tab - private settings"); }
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
