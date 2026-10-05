package local.bedwarstab;

public final class AttachDiagnosticsTest {
    public static void main(String[] args) throws Exception {
        InternalError disabled = new InternalError("Remote thread failed for unknown reason (100)");
        String message = AttachDiagnostics.explain(disabled);
        if (!message.contains("Fehler 100") || !message.contains("DisableAttachMechanism")) throw new AssertionError("missing disabled-attach diagnosis");
        if (!AttachDiagnostics.explain(new Exception("wrapper", disabled)).contains("Fehler 100")) throw new AssertionError("nested attach error");
        for (Throwable unrelated : new Throwable[]{new InternalError("Remote thread failed for unknown reason (101)"), new java.io.IOException("Access denied"), new IllegalArgumentException("100")}) {
            if (AttachDiagnostics.explain(unrelated).contains("DisableAttachMechanism")) throw new AssertionError("unrelated error misclassified");
        }
        if (Launcher.runCommand(new String[]{"--invalid", "123"}) != 1) throw new AssertionError("invalid command accepted");
        if (Launcher.runCommand(new String[]{"--attach"}) != 1) throw new AssertionError("missing arguments accepted");
        for (String[] bad : new String[][]{new String[]{}, new String[]{"x"}, new String[]{"--probe", "0"}, new String[]{"--probe", null}, new String[]{"--attach", "1", "does-not-exist"}})
            if (Launcher.runCommand(bad) != 1) throw new AssertionError("malformed invocation accepted");
        java.nio.file.Path log = java.nio.file.Files.createTempFile(java.nio.file.Path.of(args[0]), "diagnostic-", ".log");
        Process child = new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
            "-Dfile.encoding=windows-1252", "-cp", System.getProperty("java.class.path"), Launcher.class.getName(),
            "--attach", "1", log.resolveSibling("missing-distribution").toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            if (!child.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("diagnostic helper hung");
            if (child.exitValue() != 1 || !java.nio.file.Files.readString(log).contains("Unvollständiges Programmpaket")) throw new AssertionError("helper output is not UTF-8");
        } finally { if (child.isAlive()) child.destroyForcibly(); }
        System.out.println("PASS: Attach error 100, nested errors, argument validation and UTF-8 helper output");
        String previousDirectory = System.getProperty("bedwarstab.dataDir");
        java.nio.file.Path settingsRoot = java.nio.file.Path.of(args[0], "alias-cli-test");
        try {
            System.setProperty("bedwarstab.dataDir", settingsRoot.toString());
            local.bedwarstab.config.DisplaySettings.update(settingsRoot, p -> { p.setProperty("hypixelKey", "dummy-local-test"); p.setProperty("showWins", "false"); });
            if (Launcher.runCommand(new String[]{"--allow-server", "free.stopthelag.lol"}) != 0) throw new AssertionError("alias save failed");
            java.util.Properties saved = local.bedwarstab.config.DisplaySettings.read(settingsRoot);
            if (!"free.stopthelag.lol".equals(saved.getProperty("hypixelAliases")) || !"dummy-local-test".equals(saved.getProperty("hypixelKey")) || !"false".equals(saved.getProperty("showWins"))) throw new AssertionError("alias update lost settings");
        } finally {
            if (previousDirectory == null) System.clearProperty("bedwarstab.dataDir"); else System.setProperty("bedwarstab.dataDir", previousDirectory);
        }
        System.out.println("PASS: alias CLI preserves keys and category settings");
    }
}
