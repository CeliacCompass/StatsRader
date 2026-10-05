package local.bedwarstab.core;

import java.lang.reflect.*;
import java.net.URLClassLoader;
import java.nio.file.Path;
import local.bedwarstab.config.DisplaySettings;
import local.bedwarstab.config.DisplaySettings.Category;

final class GameConfig {
    private final StatsService service;
    private final Path dataRoot;
    private final URLClassLoader screens;
    private final Method minecraft, display;
    private final Field input;
    private final Method inputText;
    private final Constructor<?> screen;
    private int backgroundOpacity, savedOpacity;
    private String message = "§7Änderungen werden sofort übernommen und gespeichert";
    GameConfig(ClassLoader game, Path projectRoot, Path dataRoot, StatsService service) throws Exception {
        this.service = service; this.dataRoot = dataRoot;
        backgroundOpacity = savedOpacity = DisplaySettings.backgroundOpacity(DisplaySettings.read(dataRoot));
        Class<?> mc = Class.forName("ave", false, game);
        Class<?> gui = Class.forName("axu", false, game);
        Class<?> chat = Class.forName("awv", false, game);
        input = chat.getDeclaredField("a"); input.setAccessible(true);
        inputText = input.getType().getMethod("b");
        minecraft = mc.getMethod("A"); display = mc.getMethod("a", gui);
        screens = new URLClassLoader(new java.net.URL[]{projectRoot.resolve("dist/screens.jar").toUri().toURL()}, game);
        try { screen = Class.forName("BedwarsTabConfigScreen", false, screens).getConstructor(); }
        catch (ReflectiveOperationException | LinkageError e) { screens.close(); throw e; }
    }
    static boolean isCommand(String text) {
        return text != null && (text.trim().equalsIgnoreCase("/config") || text.trim().equalsIgnoreCase("/bwconfig"));
    }
    Class<?> uiClass(String name) throws ClassNotFoundException { return Class.forName(name, false, screens); }
    boolean chat(Object chatScreen, int key) {
        if (key != 28 && key != 156) return false;
        boolean recognized = false;
        try {
            recognized = isCommand((String) inputText.invoke(input.get(chatScreen)));
            if (!recognized) return false;
            display.invoke(minecraft.invoke(null), screen.newInstance());
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Agent.log("Config menu failed: " + e.getClass().getSimpleName());
            return recognized; // Never send a recognized local command on failure.
        }
    }
    synchronized String[] action(int id) {
        if (id >= 0 && id < Category.values().length) {
            DisplaySettings next = service.displaySettings().toggle(Category.values()[id]);
            try {
                next.save(dataRoot); service.applyDisplaySettings(next);
                message = "§aGespeichert – Tab-Anzeige aktualisiert";
            } catch (java.io.IOException e) {
                message = "§cSpeichern fehlgeschlagen – Auswahl unverändert";
                Agent.log("Config save failed: " + e.getClass().getSimpleName());
            }
        }
        String[] labels = new String[Category.values().length + 1];
        for (Category category : Category.values()) labels[category.ordinal()] = category.label + ": " +
            (service.displaySettings().enabled(category) ? "§aAN" : "§cAUS");
        labels[labels.length - 1] = message; return labels;
    }
    synchronized int background(int action) {
        if (action >= 0 && action <= 100) backgroundOpacity = action;
        else if (action >= 1000 && action <= 1100) {
            int next = action - 1000;
            try {
                DisplaySettings.update(dataRoot, p -> p.setProperty(DisplaySettings.BACKGROUND_OPACITY, Integer.toString(next)));
                backgroundOpacity = savedOpacity = next;
                message = "§aHintergrund gespeichert";
            } catch (java.io.IOException e) {
                backgroundOpacity = savedOpacity;
                message = "§cSpeichern fehlgeschlagen – Hintergrund zurückgesetzt";
                Agent.log("Background save failed: " + e.getClass().getSimpleName());
            }
        }
        return backgroundOpacity;
    }
    void close() { try { screens.close(); } catch (java.io.IOException ignored) { } }
}
