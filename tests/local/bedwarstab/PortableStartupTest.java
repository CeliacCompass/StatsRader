package local.bedwarstab;

import java.nio.file.*;
import java.util.*;
import local.bedwarstab.config.DisplaySettings;

/** No Badlion process is started; checks portable path handling and local discovery. */
public class PortableStartupTest {
    public static void main(String[] args) throws Exception {
        Path test = Files.createTempDirectory(Path.of(args[0]), "portable startup ü ");
        Path root = test.resolve("App Version ü"); Files.createDirectories(root.resolve("dist"));
        for (String file : List.of("agent.jar", "core.jar", "bridge.jar", "screens.jar")) Files.createFile(root.resolve("dist/" + file));
        String options = BadlionStartup.startupOptions(root, "-Duser.option=yes");
        if (!options.startsWith("-Duser.option=yes ") || !options.contains("\"-Dbedwarstab.manualActivation=true\"") ||
            !options.endsWith("=" + root.toRealPath() + "\"")) throw new AssertionError("portable startup options not quoted/preserved");
        Path badlion = test.resolve("Custom install/Badlion Client.exe"); Files.createDirectories(badlion.getParent()); Files.createFile(badlion);
        Path settings = test.resolve("settings");
        DisplaySettings.update(settings, p -> { p.setProperty("badlionLauncher", badlion.toString()); p.setProperty("hypixelKey", "fake-test-key"); });
        if (!badlion.equals(BadlionStartup.find(settings))) throw new AssertionError("custom installation lookup");
        if (BadlionStartup.validExecutable(root.resolve("dist/agent.jar"))) throw new AssertionError("wrong executable accepted");
        if (!BadlionStartup.isClientProcess("C:\\Custom\\Badlion Client.exe") ||
            !BadlionStartup.isClientProcess("C:\\Data\\Badlion Client\\jdk-17\\bin\\javaw.exe") ||
            BadlionStartup.isClientProcess("C:\\OtherApp\\javaw.exe")) throw new AssertionError("existing-client detection");
        if (!"fake-test-key".equals(DisplaySettings.read(settings).getProperty("hypixelKey"))) throw new AssertionError("discovery modified keys");
        try { BadlionStartup.startupOptions(test, null); throw new AssertionError("incomplete package accepted"); }
        catch (java.io.IOException expected) { }
        System.out.println("PASS: portable startup paths, custom Badlion discovery, existing-client detection and settings preservation");
    }
}
