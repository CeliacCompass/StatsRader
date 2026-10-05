import java.nio.file.*;
import local.bedwarstab.Boot;

public class IntegrationTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("bedwarstab.dataDir", Path.of(args[0], "state").toString());
        awh tab = new awh(); bdc player = new bdc();
        if (!tab.a(player).equals("Player")) throw new AssertionError("initial state");
        Boot.agentmain("attach|" + args[0], TestAgent.instrumentation);
        if (!tab.a(player).contains("BW:")) throw new AssertionError("callback not reached");
        Path otherRelease = Files.createDirectory(Path.of(args[0], "other-release"));
        try { Boot.agentmain("attach|" + otherRelease, TestAgent.instrumentation); throw new AssertionError("mixed release accepted"); }
        catch (IllegalStateException expected) { if (!expected.getMessage().contains("neu starten")) throw expected; }
        if (!tab.a(player).contains("BW:")) throw new AssertionError("failed version switch disrupted active hook");
        Thread.sleep(1400);
        String result = tab.a(player);
        if (!result.contains("BW:KEY") || !result.contains("BL:KEY")) throw new AssertionError("missing keys status: " + result);
        if (!tab.a(null).equals("null")) throw new AssertionError("null render safety");
        awv chat = new awv();
        chat.submit("/party list", 28);
        if (chat.forwarded != 1) throw new AssertionError("normal command swallowed");
        chat.submit("/config", 30);
        if (chat.typed != 1 || ave.A().m != null) throw new AssertionError("menu opened before Enter");
        chat.submit(" /CoNfIg ", 28);
        axu menu = ave.A().m;
        if (chat.forwarded != 1 || menu == null || !menu.getClass().getName().equals("BedwarsTabConfigScreen")) throw new AssertionError("/config did not open locally");
        if (menu.d() || menu.buttons().size() != 13) throw new AssertionError("menu buttons/pause behavior");
        for (int[] resolution : new int[][]{{320,240},{427,240},{854,480}}) {
            menu.l = resolution[0]; menu.m = resolution[1]; menu.b();
            for (avs button : menu.buttons()) if (button.h < 0 || button.i < 0 || button.h + button.width > menu.l || button.i + button.height > menu.m) throw new AssertionError("menu off-screen");
            menu.a(0, 0, 0f);
        }
        var opacity = Class.forName("local.bedwarstab.bridge.Bridge", false, null).getMethod("background", int.class);
        if (!opacity.invoke(null, -1).equals(100)) throw new AssertionError("default background changed");
        avs slider = menu.buttons().stream().filter(b -> b.k == 200).findFirst().orElseThrow();
        if (!slider.c(ave.A(), slider.h + 5, slider.i + 20) || !opacity.invoke(null, -1).equals(0)) throw new AssertionError("slider press must preview immediately");
        Path settings = Path.of(args[0], "state/settings.properties");
        if (Files.exists(settings) && Files.readString(settings).contains("backgroundOpacity")) throw new AssertionError("preview wrote to disk before release");
        slider.a(ave.A(), slider.h + slider.width / 2, slider.i + 20);
        if (!opacity.invoke(null, -1).equals(50)) throw new AssertionError("slider drag midpoint");
        slider.a(slider.h + slider.width + 100, slider.i);
        if (!opacity.invoke(null, -1).equals(100) || !Files.readString(settings).contains("backgroundOpacity=100")) throw new AssertionError("slider right endpoint/save");
        slider.c(ave.A(), slider.h + 5, slider.i + 20); slider.a(slider.h - 100, slider.i);
        if (!opacity.invoke(null, -1).equals(0) || !Files.readString(settings).contains("backgroundOpacity=0")) throw new AssertionError("slider left clamp/save");
        slider.c(ave.A(), slider.h + slider.width / 2, slider.i + 20);
        menu.key(1); // Closing while holding the slider also commits the live preview.
        if (!Files.readString(settings).contains("backgroundOpacity=50")) throw new AssertionError("Escape lost slider change");
        chat.submit("/config", 28); menu = ave.A().m;
        if (!menu.buttons().stream().filter(b -> b.k == 200).findFirst().orElseThrow().j.contains("50 %")) throw new AssertionError("reopened menu forgot opacity");
        // Disable the four default columns, then verify the live callback has no suffix.
        for (int category : new int[]{0, 1, 2, 9, 10}) menu.click(category);
        if (!tab.a(player).equals("Player")) throw new AssertionError("disabled categories still visible");
        java.util.Properties saved = new java.util.Properties();
        try (var reader = Files.newBufferedReader(Path.of(args[0], "state/settings.properties"))) { saved.load(reader); }
        if (!"false".equals(saved.getProperty("showStars")) || !"false".equals(saved.getProperty("showBlacklist")) || !"50".equals(saved.getProperty("backgroundOpacity"))) throw new AssertionError("selection/opacity not preserved");
        menu.click(9);
        if (tab.a(player).contains("BW:") || !tab.a(player).contains("BL:")) throw new AssertionError("blacklist-only selection");
        menu.click(100); if (ave.A().m != null) throw new AssertionError("Done did not close");
        chat.submit("/bwconfig", 156); if (ave.A().m == null || chat.forwarded != 1) throw new AssertionError("alias/numpad Enter");
        ave.A().m.key(1); if (ave.A().m != null) throw new AssertionError("Escape did not close");
        Boot.agentmain("attach|" + args[0], TestAgent.instrumentation);
        if (!opacity.invoke(null, -1).equals(50)) throw new AssertionError("agent reload forgot saved opacity");
        String second = tab.a(player);
        if (second.contains("BW:") || !second.contains("BL:") || second.indexOf("BL:") != second.lastIndexOf("BL:")) throw new AssertionError("reload/persisted selection");
        Boot.agentmain("detach|" + args[0], TestAgent.instrumentation);
        if (Class.forName("local.bedwarstab.bridge.Bridge", false, null).getField("tickHandler").get(null) != null) throw new AssertionError("tick callback not removed");
        if (!tab.a(player).equals("Player")) throw new AssertionError("detach did not restore original method");
        chat.submit("/config", 28); if (chat.forwarded != 2) throw new AssertionError("chat hook not removed");
        // Fail after one class has been transformed; rollback must restore both hooks.
        java.util.concurrent.atomic.AtomicInteger transforms = new java.util.concurrent.atomic.AtomicInteger();
        java.lang.instrument.Instrumentation failing = (java.lang.instrument.Instrumentation) java.lang.reflect.Proxy.newProxyInstance(
            IntegrationTest.class.getClassLoader(), new Class<?>[]{java.lang.instrument.Instrumentation.class}, (proxy, method, arguments) -> {
                if (method.getName().equals("retransformClasses") && transforms.incrementAndGet() == 2) throw new VerifyError("simulated incompatible bytecode");
                try { return method.invoke(TestAgent.instrumentation, arguments); }
                catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
            });
        try { Boot.agentmain("attach|" + args[0], failing); throw new AssertionError("failed transform accepted"); }
        catch (IllegalStateException expected) { if (!(expected.getCause() instanceof VerifyError)) throw expected; }
        if (!tab.a(player).equals("Player")) throw new AssertionError("failed activation left Tab hook");
        chat.submit("/config", 28);
        if (chat.forwarded != 3 || ave.A().m != null) throw new AssertionError("failed activation left chat hook");
        Boot.agentmain("attach|" + args[0], TestAgent.instrumentation);
        if (!tab.a(player).contains("BL:")) throw new AssertionError("cannot recover after failed activation");
        Boot.agentmain("detach|" + args[0], TestAgent.instrumentation);
        System.out.println("PASS: isolated loading, Tab/chat hooks, local /config, real screen class, toggles, persistence, reload, rollback and detach in test JVM");
    }
}
