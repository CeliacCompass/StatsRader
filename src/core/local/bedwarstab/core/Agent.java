package local.bedwarstab.core;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import local.bedwarstab.bridge.Bridge;
import local.bedwarstab.config.DataPaths;
import local.bedwarstab.config.DisplaySettings;
import local.bedwarstab.config.ServerHosts;
import local.bedwarstab.config.DisplaySettings.Category;

public final class Agent {
    private static TabTransformer transformer;
    private static ChatTransformer chatTransformer;
    private static TickTransformer tickTransformer;
    private static AutoWho autoWho;
    private static RuntimeDiagnostics diagnostics;
    private static GameLoop gameLoop;
    private static GameConfig gameConfig;
    private static volatile StatsService service;
    private static Path root;
    private static Instrumentation inst;
    private static volatile ServerGuard serverGuard;
    private static final List<Class<?>> patched = new ArrayList<>();
    private static final Object logLock = new Object();
    static volatile String tabOutcome = "not_requested";
    private static final ClassValue<Method> profileGetters = new ClassValue<>() {
        protected Method computeValue(Class<?> type) {
            for (Method method : type.getMethods()) {
                if (method.getParameterCount() == 0 && method.getReturnType().getName().equals("com.mojang.authlib.GameProfile")) return method;
            }
            throw new IllegalArgumentException("No GameProfile accessor");
        }
    };
    public static synchronized void run(String command, String directory, Instrumentation instrumentation) throws Exception {
        root = DataPaths.directory(); inst = instrumentation;
        if (command.equals("detach")) { stop(); status("inactive", "Erweiterung deaktiviert"); return; }
        if (transformer != null || chatTransformer != null || tickTransformer != null || service != null || !patched.isEmpty()) stop();
        tabOutcome = "not_requested";
        try {
            if (!inst.isRetransformClassesSupported()) throw new IllegalStateException("JVM erlaubt keine Klassen-Neutransformation");
            service = new StatsService(root);
            ServerHosts hosts = ServerHosts.from(DisplaySettings.read(root));
            ClassLoader gameLoader = null;
            for (Class<?> type : inst.getAllLoadedClasses()) if (TabTransformer.target(type.getName().replace('.', '/'))) {
                gameLoader = type.getClassLoader(); break;
            }
            if (gameLoader == null) throw new IllegalStateException("Keine Minecraft-Tab-Klasse gefunden");
            gameConfig = new GameConfig(gameLoader, Path.of(directory), root, service);
            autoWho = new AutoWho(gameLoader, hosts, service);
            transformer = new TabTransformer();
            chatTransformer = new ChatTransformer();
            tickTransformer = new TickTransformer();
            inst.addTransformer(transformer, true);
            inst.addTransformer(chatTransformer, true);
            inst.addTransformer(tickTransformer, true);
            for (Class<?> type : inst.getAllLoadedClasses()) {
                if (TabTransformer.target(type.getName().replace('.', '/')) && inst.isModifiableClass(type)) {
                    serverGuard = new ServerGuard(type.getClassLoader(), hosts);
                    patched.add(type);
                    inst.retransformClasses(type);
                }
                if (ChatTransformer.target(type.getName().replace('.', '/')) && inst.isModifiableClass(type)) {
                    patched.add(type); inst.retransformClasses(type);
                }
                if (TickTransformer.target(type.getName()) && inst.isModifiableClass(type)) {
                    patched.add(type); inst.retransformClasses(type);
                }
            }
            if (transformer.changed.get() == 0) throw new IllegalStateException("Keine kompatible Minecraft-1.8.9-Tab-Klasse gefunden");
            if (chatTransformer.changed.get() == 0) throw new IllegalStateException("Keine kompatible Chat-Klasse für /config gefunden");
            if (tickTransformer.changed.get() == 0) throw new IllegalStateException("Kein kompatibler Spiel-Tick für Auto /who gefunden");
            Bridge.decorator = Agent::decorate;
            Bridge.tableHandler = Agent::table;
            Bridge.configHandler = gameConfig::action;
            Bridge.backgroundHandler = gameConfig::background;
            Bridge.chatHandler = gameConfig::chat;
            Bridge.tickHandler = autoWho::tick;
            gameLoop = new GameLoop(gameLoader, gameConfig); gameLoop.start();
            diagnostics = new RuntimeDiagnostics(inst, gameLoader, root, gameLoop); diagnostics.start();
            status("installed", "Hooks installiert; tatsächliche Aufrufe stehen in runtime-<PID>.properties");
            log("Hooks installed; transformed classes=" + patched.size() + "; runtime callbacks not yet verified");
        } catch (Exception | LinkageError e) {
            try { stop(); } catch (Exception | LinkageError cleanup) { e.addSuppressed(cleanup); }
            status("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            log("Activation failed: " + e.getClass().getSimpleName() + ": " + e.getMessage()); throw e;
        }
    }
    private static String decorate(String original, Object info) {
        try {
            if (serverGuard == null || !serverGuard.isHypixel()) { tabOutcome = "server_not_allowed"; return original; }
            Object profile = profileGetters.get(info.getClass()).invoke(info);
            UUID uuid = (UUID) profile.getClass().getMethod("getId").invoke(profile);
            if (uuid == null) { tabOutcome = "missing_uuid"; return original; }
            StatsService current = service;
            if (current == null) { tabOutcome = "service_inactive"; return original; }
            if (!current.displaySettings().any()) { tabOutcome = "all_categories_hidden"; return original; }
            if (uuid.version() != 4) { tabOutcome = "nick_or_npc"; return original + " §8[NICK/NPC]"; }
            String suffix = current.suffix(uuid);
            tabOutcome = suffix.isEmpty() ? "empty_suffix" : "suffix_added";
            return original + suffix;
        } catch (ReflectiveOperationException | RuntimeException e) { tabOutcome = "profile_error:" + e.getClass().getSimpleName(); return original; }
    }
    private static String[][] table(Object[] players) {
        StatsService current = service;
        if (current == null || serverGuard == null || !serverGuard.isHypixel()) return null;
        DisplaySettings selection = current.displaySettings();
        if (!selection.any()) return null;
        List<Category> columns = new ArrayList<>();
        for (Category c : new Category[]{Category.FKDR, Category.BBLR, Category.WLR, Category.WINS,
            Category.FINAL_KILLS, Category.FINAL_DEATHS, Category.BEDS_BROKEN, Category.BEDS_LOST, Category.KDR})
            if (selection.enabled(c)) columns.add(c);
        if (selection.enabled(Category.BLACKLIST)) columns.add(Category.BLACKLIST);
        String[][] table = new String[players.length + 1][columns.size() + 2];
        table[0][0] = "Tag"; table[0][1] = "Name";
        for (int i = 0; i < columns.size(); i++) table[0][i + 2] = switch (columns.get(i)) {
            case WINS -> "Wins"; case FINAL_KILLS -> "Finals"; case FINAL_DEATHS -> "F. Deaths";
            case BEDS_BROKEN -> "Beds"; case BEDS_LOST -> "B. Lost"; case BLACKLIST -> "Urchin";
            default -> columns.get(i).name();
        };
        List<Category> fetch = new ArrayList<>();
        if (selection.enabled(Category.STARS)) fetch.add(Category.STARS);
        fetch.addAll(columns);
        for (int r = 0; r < players.length; r++) {
            Arrays.fill(table[r + 1], "§8—");
            try {
                Object profile = profileGetters.get(players[r].getClass()).invoke(players[r]);
                UUID uuid = (UUID) profile.getClass().getMethod("getId").invoke(profile);
                if (uuid == null || uuid.version() != 4) { table[r + 1][0] = "§8NICK/NPC"; continue; }
                String[] values = current.tableCells(uuid, fetch);
                int offset = selection.enabled(Category.STARS) ? 1 : 0;
                if (offset != 0) table[r + 1][0] = values[0];
                for (int c = 0; c < columns.size(); c++) table[r + 1][c + 2] = values[c + offset];
            } catch (ReflectiveOperationException | RuntimeException ignored) { table[r + 1][0] = "§8?"; }
        }
        tabOutcome = "table_added";
        return table;
    }
    private static void stop() throws Exception {
        if (diagnostics != null) { diagnostics.close(); diagnostics = null; }
        if (gameLoop != null) { gameLoop.close(); gameLoop = null; }
        Bridge.decorator = null;
        Bridge.tableHandler = null;
        Bridge.chatHandler = null; Bridge.configHandler = null;
        Bridge.backgroundHandler = null;
        Bridge.tickHandler = null;
        if (autoWho != null) { autoWho.close(); autoWho = null; }
        if (gameConfig != null) { gameConfig.close(); gameConfig = null; }
        if (service != null) { service.close(); service = null; }
        if (chatTransformer != null) { inst.removeTransformer(chatTransformer); chatTransformer = null; }
        if (tickTransformer != null) { inst.removeTransformer(tickTransformer); tickTransformer = null; }
        if (transformer != null) {
            inst.removeTransformer(transformer); transformer = null;
        }
        Exception failure = null;
        for (Iterator<Class<?>> iterator = patched.iterator(); iterator.hasNext();) {
            Class<?> type = iterator.next();
            try { inst.retransformClasses(type); iterator.remove(); }
            catch (Exception | LinkageError e) {
                if (failure == null) failure = new Exception("Hook konnte nicht vollständig entfernt werden; Spielneustart erforderlich", e);
                else failure.addSuppressed(e);
            }
        }
        serverGuard = null;
        if (failure != null) throw failure;
    }
    static void log(String message) {
        synchronized (logLock) {
        if (root == null) return;
        try {
            Files.createDirectories(root);
            Files.writeString(root.resolve("agent.log"), Instant.now() + " " + message + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) { }
        }
    }
    static void status(String state, String message) {
        try {
            Properties p = new Properties(); p.setProperty("state", state); p.setProperty("message", message);
            p.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
            Files.createDirectories(root);
            try (var out = Files.newBufferedWriter(root.resolve("status.properties"))) { p.store(out, "Bedwars Tab status"); }
        } catch (Exception ignored) { }
    }
}
