import java.nio.file.*;
import java.lang.instrument.*;
import java.security.ProtectionDomain;
import local.bedwarstab.Boot;

/** Simulate a downstream client transformer erasing every method patch. */
public class DirectIntegrationTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("bedwarstab.dataDir", Path.of(args[0], "direct-state").toString());
        ave mc = ave.A(); awh original = mc.q.tab();
        Path state = Path.of(args[0], "direct-state"); Files.createDirectories(state);
        Files.writeString(state.resolve("settings.properties"), "hypixelAliases=free.stopthelag.lol\n");
        mc.serverData.b = "free.stopthelag.lol:25565";
        Boot.agentmain("attach|" + args[0], TestAgent.instrumentation);
        ClassFileTransformer erase = new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytes) {
                if (!java.util.Set.of("awh", "awv", "ave").contains(name)) return null;
                try (var input = DirectIntegrationTest.class.getClassLoader().getResourceAsStream(name + ".class")) { return input.readAllBytes(); }
                catch (Exception e) { throw new AssertionError(e); }
            }
        };
        TestAgent.instrumentation.addTransformer(erase, true);
        TestAgent.instrumentation.retransformClasses(awh.class, awv.class, ave.class);
        if (!original.a(new bdc()).equals("Player")) throw new AssertionError("method patch was not erased");
        pump(mc, 300);
        if (!mc.q.tab().getClass().getName().equals("BedwarsTabPlayerTabOverlay")) throw new AssertionError("direct overlay not installed");
        if (!mc.q.tab().a(new bdc()).contains("BW:") || !mc.q.tab().header.equals("original header")) throw new AssertionError("direct overlay decoration/state failed");
        mc.network.players.add(new bdc());
        avp.reset(854, 480);
        int previousRenders = awh.vanillaRenders;
        mc.q.tab().a(854, new auo(), new auk());
        if (awh.vanillaRenders != previousRenders || avp.texts.stream().noneMatch(t -> t.value().equals("BBLR")) ||
            avp.texts.stream().noneMatch(t -> t.value().equals("Seraph"))) throw new AssertionError("real agent table data did not reach renderer");
        // Use the real installed OptiFine GuiChatOF class, with lightweight Minecraft fixtures.
        awv chat = (awv) Class.forName("net.optifine.gui.GuiChatOF").getConstructor(awv.class).newInstance(new awv("draft text"));
        mc.a(chat); pump(mc, 200);
        if (!mc.m.getClass().getName().equals("BedwarsTabOptifineChatScreen")) throw new AssertionError("OptiFine chat not wrapped");
        if (!ayb.getGuiChatText((awv) mc.m).equals("draft text")) throw new AssertionError("chat draft lost");
        awv wrapped = (awv) mc.m;
        wrapped.submit("/config", 28);
        if (wrapped.forwarded != 0 || mc.m == null || !mc.m.getClass().getName().equals("BedwarsTabConfigScreen")) throw new AssertionError("local command failed without method patch");
        mc.a(new awv("ordinary draft")); pump(mc, 200);
        wrapped = (awv) mc.m; wrapped.submit("/party list", 28);
        if (wrapped.forwarded != 1 || mc.m != null) throw new AssertionError("ordinary chat behavior changed");
        AutoWhoScenario.run();
        mc.q.tab().header = "updated header";
        Boot.agentmain("detach|" + args[0], TestAgent.instrumentation); pump(mc, 100);
        if (mc.q.tab() != original || !original.header.equals("updated header")) throw new AssertionError("original Tab state not restored");
        TestAgent.instrumentation.removeTransformer(erase);
        System.out.println("PASS: direct Tab, real OptiFine chat, /config, automatic /who on configured alias and restoration with all bytecode hooks erased");
    }
    private static void pump(ave mc, long millis) throws InterruptedException {
        long end = System.nanoTime() + millis * 1_000_000;
        while (System.nanoTime() < end) { mc.s(); Thread.sleep(20); }
    }
}
