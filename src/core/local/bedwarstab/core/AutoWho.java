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
    private final Set<UUID> requested = new HashSet<>();
    private Object alertWorld;
    private boolean alertRoundActive;
    private long nextCheck;
    private boolean closed, reportedFailure;
    private volatile String alertState = "waiting_server";
    private volatile boolean roundMessageSent;
    private volatile long matchStarts, playersSeen, statsRequested, statsReady, statsPending, statsErrors, qualified, nickedPlayers, messagesSent;

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
                alertState = "waiting_server";
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
            if (phase == RoundStartTracker.Phase.WAITING) { clearAlerts(); alertState = "waiting_match"; }
            if (started) {
                send.invoke(currentPlayer, "/who");
                alertWorld = currentWorld;
                alertRoundActive = true;
                evaluated.clear();
                requested.clear();
                roundMessageSent = false;
                matchStarts++;
                alertState = "match_started";
                Agent.log("Auto /who sent after Bedwars match sidebar detected");
            }
            if (alertRoundActive && alertWorld == currentWorld) checkPlayers(minecraft);
            else if (alertWorld != null && alertWorld != currentWorld) clearAlerts();
        } catch (ReflectiveOperationException | RuntimeException e) {
            alertState = "hook_error_" + e.getClass().getSimpleName();
            if (!reportedFailure) { reportedFailure = true; Agent.log("Auto /who or lobby alert failed: " + e.getClass().getSimpleName()); }
        }
    }
    private void checkPlayers(Object minecraft) throws ReflectiveOperationException {
        if (stats == null) { alertState = "stats_service_unavailable"; return; }
        Object connection = network.invoke(minecraft);
        Collection<?> online = (Collection<?>) playerInfoList.invoke(connection);
        playersSeen = online.size();
        if (online.isEmpty()) { alertState = "no_players_in_tab_list"; return; }
        int ready = 0, pending = 0, errors = 0, matches = 0;
        for (Object info : online) {
            Object gameProfile = profile.invoke(info);
            UUID uuid = (UUID) profileId.invoke(gameProfile);
            if (uuid == null || evaluated.contains(uuid)) continue;
            if (requested.add(uuid)) statsRequested++;
            stats.requestAlertStats(uuid);
            StatsService.AlertStats values = stats.alertStats(uuid);
            if (values == null) {
                String state = stats.alertState(uuid);
                if (state.equals("pending")) pending++;
                else if (state.equals("nick_or_unavailable")) {
                    evaluated.add(uuid);
                    nickedPlayers++;
                    String name = String.valueOf(profileName.invoke(gameProfile));
                    Object gui = localChat.invoke(playerHud(minecraft));
                    printChat.invoke(gui, "§e[" + name + "] §7ist genickt.");
                    messagesSent++;
                    roundMessageSent = true;
                    Agent.log("Lobby nick alert message displayed (player details omitted)");
                }
                else errors++;
                continue;
            }
            ready++;
            evaluated.add(uuid);
            if (!values.qualifies()) continue;
            matches++;
            String name = String.valueOf(profileName.invoke(gameProfile));
            String message = "§b[" + name + "] §f" + values.fkdr() + "/" + values.stars() + " §7[" + teamLabel(info) + "]";
            Object gui = localChat.invoke(playerHud(minecraft));
            printChat.invoke(gui, message);
            messagesSent++;
            roundMessageSent = true;
            Agent.log("Lobby alert message displayed (player details omitted)");
        }
        statsReady = ready; statsPending = pending; statsErrors = errors; qualified = matches;
        if (matches > 0 || roundMessageSent) alertState = "alert_sent";
        else if (pending > 0) alertState = "waiting_for_hypixel_stats";
        else if (errors > 0) alertState = "hypixel_stats_error";
        else if (ready > 0 || !evaluated.isEmpty()) alertState = "no_players_over_threshold";
        else if (!alertState.startsWith("hook_error_")) alertState = "no_unchecked_players";
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
    private void clearAlerts() { alertWorld = null; alertRoundActive = false; evaluated.clear(); requested.clear(); }
    synchronized Properties diagnostics() {
        Properties p = new Properties();
        p.setProperty("alerts.state", alertState);
        p.setProperty("alerts.matchStarts", Long.toString(matchStarts));
        p.setProperty("alerts.playersSeen", Long.toString(playersSeen));
        p.setProperty("alerts.statsRequested", Long.toString(statsRequested));
        p.setProperty("alerts.statsReady", Long.toString(statsReady));
        p.setProperty("alerts.statsPending", Long.toString(statsPending));
        p.setProperty("alerts.statsErrors", Long.toString(statsErrors));
        p.setProperty("alerts.qualifying", Long.toString(qualified));
        p.setProperty("alerts.nickedPlayers", Long.toString(nickedPlayers));
        p.setProperty("alerts.messagesSent", Long.toString(messagesSent));
        p.setProperty("alerts.roundActive", Boolean.toString(alertRoundActive));
        p.setProperty("alerts.messageSentThisRound", Boolean.toString(roundMessageSent));
        return p;
    }
    public synchronized void close() { closed = true; clearAlerts(); }
}
