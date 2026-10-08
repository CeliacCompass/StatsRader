package local.bedwarstab.core;

import java.lang.reflect.*;
import java.util.*;
import local.bedwarstab.config.ServerHosts;

/** Called only at the end of Minecraft's client tick, never from an API worker. */
final class AutoWho implements AutoCloseable {
    private final Field world, player, address, hud;
    private final Method server, scoreboard, objective, title, scores, scoreName, team, format, send;
    private final Method network, playerInfoList, profile, profileName, profileId, localChat, printChat;
    private final Method infoTeam, teamName, teamPrefix;
    private final RoundStartTracker rounds = new RoundStartTracker();
    private final ServerHosts hosts;
    private final StatsService stats;
    private final Set<UUID> evaluated = new HashSet<>();
    private Object alertWorld;
    private boolean alertRoundActive;
    private long nextCheck;
    private boolean closed, reportedFailure;

    AutoWho(ClassLoader loader) throws ReflectiveOperationException {
        this(loader, ServerHosts.parse(""), null);
    }
    AutoWho(ClassLoader loader, ServerHosts hosts) throws ReflectiveOperationException {
        this(loader, hosts, null);
    }
    AutoWho(ClassLoader loader, ServerHosts hosts, StatsService stats) throws ReflectiveOperationException {
        this.hosts = hosts;
        this.stats = stats;
        Class<?> mc = Class.forName("ave", false, loader);
        world = mc.getField("f"); player = mc.getField("h");
        server = mc.getMethod("D"); address = server.getReturnType().getField("b");
        scoreboard = world.getType().getMethod("Z");
        Class<?> board = scoreboard.getReturnType(), obj = Class.forName("auk", false, loader);
        objective = board.getMethod("a", int.class); title = obj.getMethod("d");
        scores = board.getMethod("i", obj);
        scoreName = Class.forName("aum", false, loader).getMethod("e");
        team = board.getMethod("h", String.class);
        format = Class.forName("aul", false, loader).getMethod("a", Class.forName("auq", false, loader), String.class);
        send = player.getType().getMethod("e", String.class);

        network = mc.getMethod("u");
        Class<?> networkType = network.getReturnType(), infoType = Class.forName("bdc", false, loader);
        playerInfoList = networkType.getMethod("d");
        profile = infoType.getMethod("a");
        Class<?> profileType = profile.getReturnType();
        profileName = profileType.getMethod("getName"); profileId = profileType.getMethod("getId");
        infoTeam = infoType.getMethod("i");
        Class<?> teamType = infoTeam.getReturnType();
        teamName = teamType.getMethod("b"); teamPrefix = teamType.getMethod("e");
        hud = mc.getField("q");
        Method chatGetter = hud.getType().getMethod("d");
        Class<?> chatType = chatGetter.getReturnType();
        localChat = chatGetter; printChat = chatType.getMethod("a", String.class);
    }
    synchronized void tick(Object minecraft) {
        if (closed) return;
        long now = System.nanoTime() / 1_000_000;
        if (now < nextCheck) return;
        nextCheck = now + 250;
        try {
            Object currentWorld = world.get(minecraft), currentPlayer = player.get(minecraft), data = server.invoke(minecraft);
            boolean hypixel = data != null && hosts.allows(String.valueOf(address.get(data)));
            if (!hypixel || currentWorld == null || currentPlayer == null) {
                rounds.observe(null, false, RoundStartTracker.Phase.OTHER, now);
                clearAlerts();
                return;
            }
            Object board = scoreboard.invoke(currentWorld), sidebar = objective.invoke(board, 1);
            List<String> lines = new ArrayList<>();
            String heading = "";
            if (sidebar != null) {
                heading = (String) title.invoke(sidebar);
                for (Object score : (Collection<?>) scores.invoke(board, sidebar)) {
                    String name = (String) scoreName.invoke(score);
                    if (name == null || name.startsWith("#")) continue;
                    lines.add((String) format.invoke(null, team.invoke(board, name), name));
                }
                if (lines.size() > 15) lines = lines.subList(lines.size() - 15, lines.size());
            }
            RoundStartTracker.Phase phase = RoundStartTracker.phase(heading, lines);
            boolean started = rounds.observe(currentWorld, true, phase, now);
            if (phase == RoundStartTracker.Phase.WAITING) clearAlerts();
            if (started) {
                send.invoke(currentPlayer, "/who");
                alertWorld = currentWorld;
                alertRoundActive = true;
                evaluated.clear();
                Agent.log("Auto /who sent after Bedwars match sidebar detected");
            }
            if (alertRoundActive && alertWorld == currentWorld) checkPlayers(minecraft);
            else if (alertWorld != null && alertWorld != currentWorld) clearAlerts();
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!reportedFailure) { reportedFailure = true; Agent.log("Auto /who or lobby alert failed: " + e.getClass().getSimpleName()); }
        }
    }
    private void checkPlayers(Object minecraft) throws ReflectiveOperationException {
        if (stats == null) return;
        Object connection = network.invoke(minecraft);
        for (Object info : (Collection<?>) playerInfoList.invoke(connection)) {
            Object gameProfile = profile.invoke(info);
            UUID uuid = (UUID) profileId.invoke(gameProfile);
            if (uuid == null || evaluated.contains(uuid)) continue;
            stats.requestAlertStats(uuid);
            StatsService.AlertStats values = stats.alertStats(uuid);
            if (values == null) continue;
            evaluated.add(uuid);
            if (!values.qualifies()) continue;
            String name = String.valueOf(profileName.invoke(gameProfile));
            String message = "§b[" + name + "] §f" + values.fkdr() + "/" + values.stars() + " §7[" + teamLabel(info) + "]";
            Object gui = localChat.invoke(playerHud(minecraft));
            printChat.invoke(gui, message);
        }
    }
    private Object playerHud(Object minecraft) throws IllegalAccessException {
        return hud.get(minecraft);
    }
    private String teamLabel(Object info) throws ReflectiveOperationException {
        Object team = infoTeam.invoke(info);
        if (team == null) return "?";
        String registered = String.valueOf(teamName.invoke(team));
        String prefix = String.valueOf(teamPrefix.invoke(team));
        String[] colors = {"red", "blue", "green", "yellow", "aqua", "white", "pink", "gray"};
        for (String color : colors) {
            if (registered.toLowerCase(Locale.ROOT).contains(color)) return titleCase(color);
            if (prefix.toLowerCase(Locale.ROOT).contains(color)) return titleCase(color);
        }
        for (int i = 0; i < colors.length; i++) {
            String code = switch (i) { case 0 -> "§c"; case 1 -> "§9"; case 2 -> "§a"; case 3 -> "§e"; case 4 -> "§b"; case 5 -> "§f"; case 6 -> "§d"; default -> "§7"; };
            if (prefix.toLowerCase(Locale.ROOT).contains(code)) return titleCase(colors[i]);
        }
        return registered.isBlank() ? "?" : registered;
    }
    private static String titleCase(String value) { return Character.toUpperCase(value.charAt(0)) + value.substring(1); }
    private void clearAlerts() { alertWorld = null; alertRoundActive = false; evaluated.clear(); }
    public synchronized void close() { closed = true; clearAlerts(); }
}
