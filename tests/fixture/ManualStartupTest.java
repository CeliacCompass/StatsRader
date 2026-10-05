import java.nio.file.*;
import local.bedwarstab.config.RuntimeControl;

/** The requested workflow: Minecraft runs first, then the separate app activates its hooks. */
public class ManualStartupTest {
    public static void main(String[] args) throws Exception {
        Path data = Path.of(System.getProperty("bedwarstab.dataDir")), release = Path.of(args[0]);
        awh tab = new awh(); bdc player = new bdc(); awv chat = new awv(); ave.A();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (!RuntimeControl.available(data, ProcessHandle.current().pid()) && System.nanoTime() < deadline) Thread.sleep(50);
        if (!RuntimeControl.available(data, ProcessHandle.current().pid())) throw new AssertionError("manual mode did not prepare connection");
        if (!tab.a(player).equals("Player")) throw new AssertionError("manual mode activated before Inject");
        chat.submit("/config", 28);
        if (chat.forwarded != 1) throw new AssertionError("chat changed before Inject");
        // Deactivating a prepared agent before the first Inject must leave it available.
        StartupTest.control(release, data, "detach");
        StartupTest.control(release, data, "attach");
        if (!tab.a(player).contains("BW:")) throw new AssertionError("first Inject failed");
        chat.submit("/config", 28);
        if (chat.forwarded != 1 || ave.A().m == null) throw new AssertionError("local config missing after Inject");
        StartupTest.control(release, data, "detach");
        if (!tab.a(player).equals("Player")) throw new AssertionError("manual detach failed");
        StartupTest.control(release, data, "attach");
        if (!tab.a(player).contains("BW:")) throw new AssertionError("second Inject failed");
        System.out.println("PASS: manual app mode leaves game unchanged until separate launcher Inject; /config, deactivate and reactivate work");
    }
}
