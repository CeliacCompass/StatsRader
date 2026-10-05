package local.bedwarstab;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import local.bedwarstab.config.DisplaySettings;

public class SettingsConcurrencyTest {
    public static void main(String[] args) throws Exception {
        if (args.length == 2) {
            for (int n = 0; n < 15; n++) DisplaySettings.update(Path.of(args[0]), p -> {
                p.setProperty(args[1], Integer.toString(Integer.parseInt(p.getProperty(args[1], "0")) + 1));
                try { Thread.sleep(2); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            return;
        }
        Path root = Files.createTempDirectory(Path.of(args[0]), "settings-race-");
        String java = Path.of(System.getProperty("java.home"), "bin/java.exe").toString();
        List<Process> children = new ArrayList<>();
        try {
            for (String key : new String[]{"launcher", "menu"}) children.add(new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                SettingsConcurrencyTest.class.getName(), root.toString(), key).redirectErrorStream(true).redirectOutput(root.resolve(key + ".log").toFile()).start());
            for (Process child : children) if (!child.waitFor(15, TimeUnit.SECONDS) || child.exitValue() != 0) throw new AssertionError("settings writer failed; test logs: " + root);
        } finally { for (Process child : children) if (child.isAlive()) child.destroyForcibly(); }
        Properties saved = DisplaySettings.read(root);
        if (!"15".equals(saved.getProperty("launcher")) || !"15".equals(saved.getProperty("menu"))) throw new AssertionError("concurrent update lost");
        Path file = root.resolve("settings.properties"); Files.writeString(file, "broken=\\uZZZZ");
        try { DisplaySettings.update(root, p -> p.setProperty("overwrite", "no")); throw new AssertionError("corrupt settings silently replaced"); }
        catch (java.io.IOException expected) { }
        if (!Files.readString(file).equals("broken=\\uZZZZ")) throw new AssertionError("corrupt file changed");
        System.out.println("PASS: settings updates across two JVMs; corrupt settings preserved");
    }
}
