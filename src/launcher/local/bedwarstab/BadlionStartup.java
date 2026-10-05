package local.bedwarstab;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import local.bedwarstab.config.DisplaySettings;

/** Portable startup: no project paths, PowerShell, global environment edits or client modifications. */
final class BadlionStartup {
    static Path find(Path settings) throws IOException {
        String saved = DisplaySettings.read(settings).getProperty("badlionLauncher", "");
        if (!saved.isBlank()) {
            try { Path p = Path.of(saved); if (validExecutable(p)) return p; }
            catch (InvalidPathException ignored) { }
        }
        for (String variable : List.of("LOCALAPPDATA", "ProgramFiles", "ProgramFiles(x86)")) {
            String base = System.getenv(variable); if (base == null) continue;
            Path p = Path.of(base, variable.equals("LOCALAPPDATA") ? "Programs/Badlion Client/Badlion Client.exe" : "Badlion Client/Badlion Client.exe");
            if (validExecutable(p)) return p;
        }
        return null;
    }
    static boolean validExecutable(Path file) {
        return file.getFileName() != null && file.getFileName().toString().equalsIgnoreCase("Badlion Client.exe") && Files.isRegularFile(file);
    }
    static boolean isClientProcess(String command) {
        String p = command.replace('\\', '/').toLowerCase(Locale.ROOT);
        return p.endsWith("/badlion client.exe") ||
            (p.contains("/badlion client/") && (p.endsWith("/java.exe") || p.endsWith("/javaw.exe")));
    }
    static String startupOptions(Path distribution, String previous) throws IOException {
        Path root = distribution.toRealPath();
        for (String jar : List.of("agent.jar", "bridge.jar", "core.jar", "screens.jar"))
            if (!Files.isRegularFile(root.resolve("dist/" + jar))) throw new IOException("Unvollständiges Programmpaket: " + jar);
        String path = root.toString();
        if (path.contains("\"") || path.contains("\n") || path.contains("\r") || path.contains("|")) throw new IOException("Nicht unterstützter Programmpfad");
        return (previous == null ? "" : previous.trim() + " ") + "\"-Dbedwarstab.manualActivation=true\" \"-javaagent:" + root.resolve("dist/agent.jar") + "=" + root + "\"";
    }
    static void start(Path executable, Path distribution) throws IOException {
        if (!validExecutable(executable)) throw new IOException("Bitte die Datei Badlion Client.exe auswählen.");
        if (ProcessHandle.allProcesses().anyMatch(p -> p.info().command().map(BadlionStartup::isClientProcess).orElse(false)))
            throw new IOException("Minecraft und Badlion zuerst vollständig schließen, auch das Badlion-Symbol im Infobereich. Es wird kein laufendes Spiel geschlossen.");
        ProcessBuilder builder = new ProcessBuilder(executable.toAbsolutePath().toString());
        builder.directory(executable.toAbsolutePath().getParent().toFile());
        builder.environment().put("JAVA_TOOL_OPTIONS", startupOptions(distribution, builder.environment().get("JAVA_TOOL_OPTIONS")));
        // Badlion can log login data. Never capture, persist or display its console streams.
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.start();
    }
}
