import java.nio.file.*;
import java.lang.instrument.*;
import java.security.ProtectionDomain;
import local.bedwarstab.Boot;
import local.bedwarstab.bridge.Bridge;

/** Simulate a downstream client transformer erasing every method patch. */
public class DirectIntegrationTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("bedwarstab.dataDir", Path.of(args[0], "direct-state").toString());
        ave mc = ave.A(); awh original = mc.q.tab();
        Path state = Path.of(args[0], "direct-state"); Files.createDirectories(state);
        Files.writeString(state.resolve("settings.properties"), "hypixelAliases=free.stopthelag.lol\n");
        mc.serverData.b = "free.stopthelag.lol:25565";
        bln renderer = new bln(); bet player = new bet(java.util.UUID.randomUUID()); biu renderManager = new biu(renderer);
        Boot.agentmain("attach|" + args[0], TestAgent.instrumentation);
        long beforeRows = Bridge.nametagRows.get();
        Bridge.nametagHandler = values -> "§e123★ §8│ §a5.00 FKDR";
        renderManager.a(player, 1.0, 2.0, 3.0, 0.0f, 0.0f, false);
        if (renderer.labels.size() != 2 || Bridge.nametagRows.get() != beforeRows + 1 || !renderer.labels.get(0).equals("VanillaName") ||
            !renderer.labels.get(1).equals("§e123★ §8│ §a5.00 FKDR") || renderer.yValues.get(0) != 2.0 || renderer.yValues.get(1) != 1.75)
            throw new AssertionError("nametag row did not preserve the vanilla name and draw below it");
        worldPassNameTags(mc);
        Bridge.nametagHandler = null;
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
            avp.texts.stream().noneMatch(t -> t.value().equals("Urchin"))) throw new AssertionError("real agent table data did not reach renderer");
        // Use the real installed OptiFine GuiChatOF class, with lightweight Minecraft fixtures.
        awv chat = (awv) Class.forName("net.optifine.gui.GuiChatOF").getConstructor(awv.class).newInstance(new awv("draft text"));
        mc.a(chat); pump(mc, 200);
        if (!mc.m.getClass().getName().equals("BedwarsTabOptifineChatScreen")) throw new AssertionError("OptiFine chat not wrapped");
        if (!ayb.getGuiChatText((awv) mc.m).equals("draft text")) throw new AssertionError("chat draft lost");
        awv wrapped = (awv) mc.m;
        wrapped.submit("/config", 28);
        if (wrapped.forwarded != 0 || mc.m == null || !mc.m.getClass().getName().equals("BedwarsTabConfigScreen")) throw new AssertionError("local command failed without method patch");
        int nametagToggle = local.bedwarstab.config.DisplaySettings.Category.values().length;
        if (!Bridge.config(-1)[nametagToggle].contains("Nametag-Stats: §aAN")) throw new AssertionError("nametag setting should default on");
        Bridge.config(nametagToggle);
        if (!Bridge.config(-1)[nametagToggle].contains("Nametag-Stats: §cAUS") ||
            !Files.readString(state.resolve("settings.properties")).contains("showNametagStats=false")) throw new AssertionError("nametag setting did not persist");
        Bridge.config(nametagToggle);
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
    /** Badlion never reaches the vanilla label methods, so the world pass must draw the line on its own. */
    private static void worldPassNameTags(ave mc) throws Exception {
        if (Bridge.worldNametagHandler == null) throw new AssertionError("world-pass nametag renderer not installed");
        bln renderer = new bln(); mc.renderManager = new biu(renderer); mc.renderManager.camera(10.0, 60.0, 10.0);
        bet visible = new bet(java.util.UUID.randomUUID());
        visible.P = 10.0; visible.Q = 60.0; visible.R = 10.0; visible.s = 12.0; visible.t = 62.0; visible.u = 10.0;
        bet invisible = new bet(java.util.UUID.randomUUID()); invisible.nameHidden = true;
        bet sneaking = new bet(java.util.UUID.randomUUID()); sneaking.sneaking = true;
        mc.f.j.clear(); mc.f.j.addAll(java.util.List.of(visible, invisible, sneaking));
        org.lwjgl.opengl.GL11.lighting = false;
        long rows = Bridge.worldNametagRows.get();
        Bridge.worldNameTags(0.5f);
        // Interpolated feet at y=61 relative to camera y=60 -> 1.0, drawn 0.25 below the name.
        if (renderer.labels.size() != 1 || !renderer.labels.get(0).equals("§e123★ §8│ §a5.00 FKDR") || renderer.yValues.get(0) != 0.75 ||
            Bridge.worldNametagRows.get() != rows + 1) throw new AssertionError("world pass did not draw exactly one visible player's stats: " + renderer.labels + " " + renderer.yValues);
        if (org.lwjgl.opengl.GL11.lighting) throw new AssertionError("world pass left lighting enabled");
        // A renderer hook that already drew this entity in the same frame wins; no duplicate line.
        Bridge.nameTag(renderer, visible, "VanillaName", 0.0, 1.0, 0.0, 64);
        Bridge.worldNameTags(0.5f);
        if (renderer.labels.size() != 2) throw new AssertionError("world pass duplicated a line already drawn by a renderer hook");
        Bridge.worldNameTags(0.5f);
        if (renderer.labels.size() != 3) throw new AssertionError("world pass did not resume on the next frame");
        mc.f.j.clear(); mc.renderManager = null;
    }
    private static void pump(ave mc, long millis) throws InterruptedException {
        long end = System.nanoTime() + millis * 1_000_000;
        while (System.nanoTime() < end) { mc.s(); Thread.sleep(20); }
    }
}
