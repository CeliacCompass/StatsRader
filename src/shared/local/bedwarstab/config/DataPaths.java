package local.bedwarstab.config;
import java.nio.file.Path;

public final class DataPaths {
    private DataPaths() { }
    public static Path directory() {
        String override = System.getProperty("bedwarstab.dataDir");
        if (override != null) return Path.of(override).toAbsolutePath();
        String local = System.getenv("LOCALAPPDATA");
        if (local == null || local.isBlank()) throw new IllegalStateException("Windows LOCALAPPDATA fehlt");
        return Path.of(local, "BedwarsTab");
    }
}
