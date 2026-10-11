package local.bedwarstab.core;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import local.bedwarstab.config.DisplaySettings;
import local.bedwarstab.config.ServerHosts;

public class AutoWhoAlertTest {
    private static final UUID STARS = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID FKDR = UUID.fromString("10000000-0000-4000-8000-000000000002");
    private static final UUID BORDER = UUID.fromString("10000000-0000-4000-8000-000000000003");
    private static final UUID NICK = UUID.fromString("10000000-0000-4000-8000-000000000004");
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of(args[0]), "alert-");
        Properties settings = new Properties(); settings.setProperty("hypixelKey", "test-only");
        for (DisplaySettings.Category category : DisplaySettings.Category.values()) settings.setProperty(category.key, "false");
        DisplaySettings.write(root, settings);
        AtomicInteger apiCalls = new AtomicInteger();
        ClassLoader loader = AutoWhoAlertTest.class.getClassLoader();
        try (StatsService stats = new StatsService(root, (url, header, key) -> {
            apiCalls.incrementAndGet();
            String json = url.contains(STARS.toString().replace("-", "")) ? player(450, 20, 10)
                : url.contains(FKDR.toString().replace("-", "")) ? player(300, 60, 10)
                : url.contains(NICK.toString().replace("-", "")) ? "{\"success\":true,\"player\":null}"
                : player(400, 50, 10);
            return new StatsService.Reply(200, json, "", "", "");
        }, System::currentTimeMillis, false)) {
            AutoWho who = new AutoWho(loader, ServerHosts.parse(""), stats);
            Class<?> mcType = Class.forName("ave", true, loader);
            Object mc = mcType.getMethod("A").invoke(null);
            Object server = mcType.getField("serverData").get(mc); server.getClass().getField("b").set(server, "mc.hypixel.net");
            Object network = mcType.getField("network").get(mc);
            @SuppressWarnings("unchecked") List<Object> players = (List<Object>) network.getClass().getField("players").get(network);
            players.clear();
            players.add(info(loader, "StarPlayer", STARS, "aqua"));
            players.add(info(loader, "HighFkdr", FKDR, "red"));
            players.add(info(loader, "ExactlyAtLimit", BORDER, "pink"));
            players.add(info(loader, "HiddenPlayer", NICK, "green"));
            Object world = mcType.getField("f").get(mc);
            Object board = world.getClass().getField("board").get(world);
            @SuppressWarnings("unchecked") List<Object> scores = (List<Object>) board.getClass().getField("scores").get(board);
            Class<?> scoreType = Class.forName("aum", true, loader);
            scores.add(scoreType.getConstructor(String.class).newInstance("Starting in 5s"));
            tickFor(who, mc, stats, 1400);
            check(messages(mc).isEmpty() && apiCalls.get() == 0, "no alert or API call before match start");
            scores.clear();
            Object timer = newTeam(loader, "§bDiamond II in ");
            @SuppressWarnings("unchecked") Map<String,Object> teams = (Map<String,Object>) board.getClass().getField("teams").get(board);
            teams.put("6:00", timer); scores.add(scoreType.getConstructor(String.class).newInstance("6:00"));
            tickFor(who, mc, stats, 2600);
            List<String> messages = messages(mc);
            Object player = mcType.getField("h").get(mc);
            @SuppressWarnings("unchecked") List<String> sent = (List<String>) player.getClass().getField("sent").get(player);
            check(sent.equals(List.of("/who")), "the round start still sends only /who");
            check(messages.size() == 3, "qualifying players and nick are alerted: " + messages);
            check(messages.stream().anyMatch(s -> s.contains("[StarPlayer]") && s.contains("2.00/450") && s.contains("[Aqua]")), "stars threshold alert includes FKDR, stars, and team");
            check(messages.stream().anyMatch(s -> s.contains("[HighFkdr]") && s.contains("6.00/300") && s.contains("[Red]")), "FKDR threshold alert includes stars and team");
            check(messages.stream().noneMatch(s -> s.contains("ExactlyAtLimit")), "strict thresholds exclude exactly 400 stars and FKDR 5");
            check(messages.stream().anyMatch(s -> s.contains("[HiddenPlayer]") && s.contains("ist genickt")), "Hypixel's missing player profile triggers a nick chat message");
            tickFor(who, mc, stats, 700);
            check(messages(mc).size() == 3, "stats and nick alerts are each announced only once in a round");
            who.close();
        }
        System.out.println("PASS: match-only chat alerts, nick detection, thresholds, team and deduplication");
    }
    private static Object info(ClassLoader loader, String name, UUID uuid, String teamName) throws Exception {
        Object team = newTeam(loader, ""); team.getClass().getField("registeredName").set(team, teamName);
        return Class.forName("bdc", true, loader).getConstructor(String.class, UUID.class, team.getClass()).newInstance(name, uuid, team);
    }
    private static Object newTeam(ClassLoader loader, String prefix) throws Exception {
        Object team = Class.forName("aul", true, loader).getConstructor().newInstance();
        team.getClass().getField("prefix").set(team, prefix); return team;
    }
    private static List<String> messages(Object mc) throws Exception {
        Object hud = mc.getClass().getField("q").get(mc), chat = hud.getClass().getMethod("d").invoke(hud);
        @SuppressWarnings("unchecked") List<String> messages = (List<String>) chat.getClass().getField("messages").get(chat);
        return messages;
    }
    private static String player(long stars, long kills, long deaths) {
        return "{\"success\":true,\"player\":{\"achievements\":{\"bedwars_level\":" + stars +
            "},\"stats\":{\"Bedwars\":{\"final_kills_bedwars\":" + kills +
            ",\"final_deaths_bedwars\":" + deaths + "}}}}";
    }
    private static void tickFor(AutoWho who, Object mc, StatsService stats, long millis) throws Exception {
        long deadline = System.nanoTime() + millis * 1_000_000;
        while (System.nanoTime() < deadline) { who.tick(mc); stats.pump(); Thread.sleep(50); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
