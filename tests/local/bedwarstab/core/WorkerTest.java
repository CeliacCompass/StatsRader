package local.bedwarstab.core;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import local.bedwarstab.config.DisplaySettings;
import local.bedwarstab.config.DisplaySettings.Category;

public class WorkerTest {
    static final String PLAYER = "{\"success\":true,\"player\":{\"stats\":{\"Bedwars\":{\"final_kills_bedwars\":12,\"final_deaths_bedwars\":3,\"wins_bedwars\":7}}}}";
    static final String CLEAR = "{\"success\":true,\"data\":{\"blacklist\":{\"tagged\":false}}}";
    static final UUID FIRST = UUID.fromString("c06f8906-4c8a-4911-9c29-ea1dbd1aab82"), SECOND = UUID.randomUUID();
    static int checks;
    static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
    static StatsService.Reply reply(int code, String body) { return new StatsService.Reply(code, body, "", "", ""); }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of(args[0]), "worker-");
        Properties properties = new Properties(); properties.setProperty("hypixelKey", "test-only"); properties.setProperty("seraphKey", "test-only");
        DisplaySettings.write(root, properties);
        AtomicLong clock = new AtomicLong(1000000);
        AtomicInteger hypixelCalls = new AtomicInteger(), seraphCalls = new AtomicInteger();
        AtomicReference<StatsService.Reply> response = new AtomicReference<>(reply(200, PLAYER));
        StatsService.Transport transport = (url, header, key) -> {
            if (header.equals("API-Key")) { hypixelCalls.incrementAndGet(); return response.get(); }
            seraphCalls.incrementAndGet(); return reply(200, CLEAR);
        };
        try (StatsService service = new StatsService(root, transport, clock::get, false)) {
            service.suffix(FIRST); service.pump();
            check(service.suffix(FIRST).contains("4.00 FKDR"), "successful background fetch");
            String[] cells = service.tableCells(FIRST, List.of(Category.STARS, Category.FKDR, Category.BBLR, Category.WINS, Category.BLACKLIST));
            check(cells[1].equals("§a4.00") && cells[2].equals("§70.00") && cells[3].equals("§77") && cells[4].equals("§8–"), "table cells contain values without repeated labels and use ratio/count colors");
            check(Arrays.stream(service.tableCells(UUID.fromString("00000000-0000-1000-8000-000000000001"), List.of(Category.FKDR))).allMatch(s -> s.equals("§8—")), "nick identifiers do not fetch fake statistics");
            service.applyDisplaySettings(new DisplaySettings(Set.of(Category.WINS)));
            check(service.suffix(FIRST).contains("7 W") && !service.suffix(FIRST).contains("FKDR"), "reformat cached counters immediately");
            service.pump(); check(hypixelCalls.get() == 1 && seraphCalls.get() == 1, "toggle does not refetch");
            service.applyDisplaySettings(new DisplaySettings(Set.of()));
            check(service.suffix(SECOND).isEmpty(), "all hidden leaves no suffix"); service.pump();
            check(hypixelCalls.get() == 1 && seraphCalls.get() == 1, "hidden providers do not query");
            service.applyDisplaySettings(DisplaySettings.defaults());
            response.set(reply(403, "")); clock.addAndGet(301000); service.suffix(FIRST); service.pump();
            check(service.suffix(FIRST).contains("BW:AUTH") && service.suffix(FIRST).contains("BL:–"), "Hypixel auth failure does not suppress blacklist");
            service.applyDisplaySettings(new DisplaySettings(Set.of(Category.FKDR)));
            check(service.suffix(FIRST).contains("BW:AUTH") && !service.suffix(FIRST).contains("4.00"), "toggle cannot resurrect old successful stats");
            service.suffix(SECOND); service.pump(); check(hypixelCalls.get() == 2, "invalid key stops further player queries");
        }
        hypixelCalls.set(0); seraphCalls.set(0);
        response.set(new StatsService.Reply(429, "", "", "", "120"));
        try (StatsService service = new StatsService(root, transport, clock::get, false)) {
            service.suffix(FIRST); service.pump();
            service.suffix(SECOND); service.pump();
            check(hypixelCalls.get() == 1 && seraphCalls.get() == 2, "429 pauses the affected provider across players");
            clock.addAndGet(61000); service.suffix(FIRST); service.pump(); check(hypixelCalls.get() == 1, "no early retry");
            clock.addAndGet(61000); response.set(reply(200, PLAYER)); service.suffix(FIRST); service.pump();
            check(hypixelCalls.get() == 2 && service.suffix(FIRST).contains("4.00"), "rate limit recovers");
        }
        response.set(reply(200, "{\"success\":true,\"player\":{\"stats\":\"broken\"}}")); hypixelCalls.set(0);
        try (StatsService service = new StatsService(root, transport, clock::get, false)) {
            service.suffix(FIRST); service.pump(); check(service.suffix(FIRST).contains("BW:?"), "malformed 200 is unknown");
            clock.addAndGet(61000); response.set(reply(200, PLAYER)); service.suffix(FIRST); service.pump();
            check(hypixelCalls.get() == 2 && service.suffix(FIRST).contains("4.00"), "malformed response retries after error TTL");
            service.suffix(SECOND); clock.addAndGet(16000); service.pump(); check(hypixelCalls.get() == 2, "old lobby entries skipped");
        }
        StatsService closed = new StatsService(root, transport, clock::get, false); closed.suffix(FIRST); closed.close(); closed.pump();
        check(closed.suffix(FIRST).isEmpty(), "closed service cannot queue work");
        StatsService.Gate gate = new StatsService.Gate();
        String date = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(java.time.Instant.ofEpochMilli(clock.get() + 120000).atZone(java.time.ZoneOffset.UTC));
        StatsService.updateGate(gate, new StatsService.Reply(429, "", "", "", date), clock.get());
        check(gate.until == clock.get() + 120000, "Retry-After HTTP-date supported");
        liveCredentials(root, clock);
        System.out.println("PASS: " + checks + " worker lifecycle, cache and provider recovery checks (fake transport; no network)");
    }
    static void liveCredentials(Path root, AtomicLong clock) throws Exception {
        DisplaySettings.update(root, p -> { p.setProperty("hypixelKey", "old-test-key"); p.setProperty("seraphKey", ""); });
        AtomicInteger requests = new AtomicInteger(), blacklistRequests = new AtomicInteger();
        AtomicReference<String> lastKey = new AtomicReference<>();
        try (StatsService service = new StatsService(root, (url, header, key) -> {
            if (header.equals("API-Key")) {
                requests.incrementAndGet(); lastKey.set(key);
                return key.equals("old-test-key") ? reply(403, "") : reply(200, PLAYER);
            }
            blacklistRequests.incrementAndGet(); return reply(200, CLEAR);
        }, clock::get, false)) {
            service.suffix(FIRST); service.pump();
            check(service.suffix(FIRST).contains("AUTH") && service.suffix(FIRST).contains("BL:KEY"), "initial missing/invalid credentials");
            DisplaySettings.update(root, p -> p.setProperty("mode", "eight_one")); service.pump();
            service.suffix(FIRST); service.pump();
            check(requests.get() == 1 && service.suffix(FIRST).contains("AUTH"), "mode change cannot bypass rejected key");
            DisplaySettings.update(root, p -> { p.setProperty("hypixelKey", "replacement-test-key"); p.setProperty("seraphKey", "new-seraph-test-key"); p.setProperty("mode", "overall"); });
            service.pump(); service.suffix(FIRST); service.pump();
            check(service.suffix(FIRST).contains("4.00 FKDR") && service.suffix(FIRST).contains("BL:–"), "key change recovers both providers without restart");
            check("replacement-test-key".equals(lastKey.get()), "new key used for requests");
            DisplaySettings.update(root, p -> p.setProperty("mode", "eight_one"));
            service.pump(); service.suffix(FIRST); service.pump();
            check(service.suffix(FIRST).contains("0.00 FKDR") && blacklistRequests.get() == 1, "mode refresh leaves blacklist cache intact");
            int before = requests.get();
            DisplaySettings.update(root, p -> p.setProperty("showWins", "false")); service.pump(); service.suffix(FIRST); service.pump();
            check(requests.get() == before, "category writes do not flush provider cache");
            Files.writeString(root.resolve("settings.properties"), "hypixelKey=\\uZZZZ");
            service.pump(); check(service.suffix(FIRST).contains("0.00 FKDR"), "corrupt file preserves current working configuration");
        }
    }
}
