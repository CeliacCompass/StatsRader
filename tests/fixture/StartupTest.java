import java.nio.file.*;

/** Real premain entry, with dynamic Attach disabled; no TestAgent and no agentmain call. */
public class StartupTest {
    public static void main(String[] args) throws Exception {
        var bean = java.lang.management.ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class);
        if (!"true".equals(bean.getVMOption("DisableAttachMechanism").getValue())) throw new AssertionError("Attach is not disabled");
        awh tab = new awh(); bdc player = new bdc(); awv chat = new awv(); ave.A();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (!tab.a(player).contains("BW:") && System.nanoTime() < deadline) Thread.sleep(50);
        if (!tab.a(player).contains("BW:")) throw new AssertionError("startup did not install Tab hook");
        chat.submit("/config", 28);
        if (chat.forwarded != 0 || ave.A().m == null) throw new AssertionError("startup did not install local config menu");
        Path data = Path.of(System.getProperty("bedwarstab.dataDir"));
        Path log = data.resolve("startup-" + ProcessHandle.current().pid() + ".log");
        while ((!Files.exists(log) || !Files.readString(log).contains("Startup activation completed")) && System.nanoTime() < deadline) Thread.sleep(50);
        if (!Files.readString(log).contains("Startup activation completed")) throw new AssertionError("missing startup diagnosis");
        AutoWhoScenario.run();
        java.util.Properties runtime = new java.util.Properties();
        try (var reader = Files.newBufferedReader(data.resolve("runtime-" + ProcessHandle.current().pid() + ".properties"))) { runtime.load(reader); }
        if (runtime.containsKey("inspection.failure") || !"true".equals(runtime.getProperty("bridge.gameLoaderIdentityMatches"))) throw new AssertionError("runtime inspection failed: " + runtime);
        for (String counter : new String[]{"tab.calls", "chat.calls", "tick.calls"})
            if (Long.parseLong(runtime.getProperty(counter, "0")) == 0) throw new AssertionError("missing runtime callback counter: " + counter);
        // Exercise the actual launcher in a separate JVM, against this Attach-disabled JVM.
        Path release = Path.of(args[0]);
        Object originalHandler = Class.forName("local.bedwarstab.bridge.Bridge", false, null).getField("tickHandler").get(null);
        control(release, data, "attach");
        if (originalHandler != Class.forName("local.bedwarstab.bridge.Bridge", false, null).getField("tickHandler").get(null))
            throw new AssertionError("repeated Inject resets AutoWho state");
        control(release, data, "detach");
        if (!"Player".equals(tab.a(player))) throw new AssertionError("app detach did not restore Tab");
        control(release, data, "attach");
        if (!tab.a(player).contains("BW:")) throw new AssertionError("app reactivation failed");
        if (!"true".equals(bean.getVMOption("DisableAttachMechanism").getValue())) throw new AssertionError("Attach protection changed");
        System.out.println("PASS: premain through JAVA_TOOL_OPTIONS; Tab, /config and automatic /who work with DisableAttachMechanism still enabled");
        System.out.println("PASS: separate launcher JVM activates/deactivates resident agent with Attach disabled; repeated Inject preserves AutoWho");
    }
    static void control(Path release, Path data, String action) throws Exception {
        Path output = Files.createTempFile(data, "control-test-", ".log");
        ProcessBuilder builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java.exe").toString(),
            "-Dbedwarstab.dataDir=" + data, "-jar", release.resolve("dist/launcher.jar").toString(),
            "--" + action, Long.toString(ProcessHandle.current().pid()), release.toString());
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        Process child = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            if (!child.waitFor(25, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("app control timeout");
            if (child.exitValue() != 0) throw new AssertionError("app control failed: " + Files.readString(output));
        } finally { if (child.isAlive()) child.destroyForcibly(); }
    }
}
