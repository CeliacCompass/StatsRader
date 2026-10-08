package local.bedwarstab.core;

import java.nio.file.*;
import java.util.jar.JarFile;
import local.bedwarstab.bridge.Bridge;
import org.objectweb.asm.*;
import java.util.*;
import local.bedwarstab.config.DisplaySettings;
import local.bedwarstab.config.DisplaySettings.Category;
import local.bedwarstab.config.ServerHosts;

public final class CoreTest {
    private static int checks;
    static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        String player = "{\"success\":true,\"player\":{\"achievements\":{\"bedwars_level\":123},\"stats\":{\"Bedwars\":{\"final_kills_bedwars\":30,\"final_deaths_bedwars\":4,\"wins_bedwars\":20,\"eight_two_final_kills_bedwars\":5,\"eight_two_final_deaths_bedwars\":2}}}}";
        String overall = StatsService.parseBedwars(player, "overall", true);
        check(overall.contains("123★") && overall.contains("7.50") && overall.contains("20 W"), "overall calculation");
        check(StatsService.parseBedwars(player, "eight_two", false).contains("2.50"), "mode-specific stats");
        check(!StatsService.parseBedwars(player, "overall", false).contains(" W"), "hide wins");
        check(StatsService.parseBedwars("{\"success\":true,\"player\":null}", "overall", true).contains("NICK/?"), "unknown player");
        check(StatsService.parseBedwars("{\"success\":false}", "overall", true).contains("?"), "API failure distinct from zero");
        check(StatsService.parseBedwars("not-json", "overall", true).contains("?"), "malformed stats");
        for (String invalid : new String[]{"{\"success\":\"true\",\"player\":{}}", "{\"success\":true,\"player\":{\"stats\":null}}", "{\"success\":true,\"player\":{\"stats\":{\"Bedwars\":[]}}}"})
            check(StatsService.parseBedwars(invalid, "overall", true).contains("?"), "invalid structure is not zero stats");
        for (String value : new String[]{"-1", "0.5", "9223372036854775808", "\"12\"", "null"})
            check(StatsService.parseBedwars("{\"success\":true,\"player\":{\"stats\":{\"Bedwars\":{\"wins_bedwars\":" + value + "}}}}", "overall", true).contains("?"), "invalid counter is unknown");
        check(StatsService.parseBedwars("{\"success\":true,\"player\":{}}", "overall", true).contains("0.00"), "new player");
        String zeroDeaths = "{\"success\":true,\"player\":{\"stats\":{\"Bedwars\":{\"final_kills_bedwars\":5,\"final_deaths_bedwars\":0}}}}";
        check(StatsService.parseBedwars(zeroDeaths, "overall", true).contains("∞"), "zero deaths with kills");
        long[] thresholds = {0, 1, 3, 5, 7, 10, 20, 30, 50, 100};
        String[] colors = {"§7", "§f", "§a", "§2", "§e", "§6", "§c", "§4", "§d", "§5"};
        for (int i = 0; i < thresholds.length; i++) {
            String atThreshold = "{\"success\":true,\"player\":{\"stats\":{\"Bedwars\":{\"final_kills_bedwars\":" + thresholds[i] * 100 + ",\"final_deaths_bedwars\":100}}}}";
            check(StatsService.parseBedwars(atThreshold, "overall", new DisplaySettings(Set.of(Category.FKDR))).startsWith(colors[i]), "FKDR color at inclusive threshold " + thresholds[i]);
            if (i > 0) check(StatsService.fkdrColor(thresholds[i] * 100 - 1, 100).equals(colors[i - 1]), "FKDR color just below threshold " + thresholds[i]);
        }
        check(StatsService.fkdrColor(1, 0).equals("§5") && StatsService.fkdrColor(0, 0).equals("§7"), "infinite FKDR is highest tier; zero/zero is gray");
        check(StatsService.fkdrColor(Long.MAX_VALUE - 1, Long.MAX_VALUE).equals("§7"), "exact threshold comparison at large counters");
        long[] wlrTenths = {0, 3, 9, 15, 21, 30, 60, 90, 150, 300};
        for (int i = 0; i < wlrTenths.length; i++) {
            String atThreshold = "{\"success\":true,\"player\":{\"stats\":{\"Bedwars\":{\"wins_bedwars\":" + wlrTenths[i] * 10 + ",\"losses_bedwars\":100}}}}";
            check(StatsService.parseBedwars(atThreshold, "overall", new DisplaySettings(Set.of(Category.WLR))).startsWith(colors[i]), "WLR color at inclusive threshold " + wlrTenths[i] / 10.0);
            if (i > 0) check(StatsService.wlrColor(wlrTenths[i] * 10 - 1, 100).equals(colors[i - 1]), "WLR color just below threshold " + wlrTenths[i] / 10.0);
        }
        check(StatsService.wlrColor(1, 0).equals("§5") && StatsService.wlrColor(0, 0).equals("§7"), "infinite WLR is highest tier; zero/zero is gray");
        check(StatsService.wlrColor((Long.MAX_VALUE / 10) * 3, Long.MAX_VALUE).equals("§7"), "exact fractional WLR boundary without overflow");
        check(StatsService.wlrColor((Long.MAX_VALUE / 10) * 3, (Long.MAX_VALUE / 10) * 10).equals("§f"), "large counters exactly on WLR 0.3");
        check(StatsService.parseBedwars("{\"success\":true,\"player\":{\"stats\":{\"Bedwars\":{\"eight_two_wins_bedwars\":21,\"eight_two_losses_bedwars\":10}}}}", "eight_two", new DisplaySettings(Set.of(Category.WLR))).equals("§e2.10 WLR"), "WLR color uses selected mode");
        long[] bblrTenths = {0, 2, 6, 10, 14, 20, 40, 60, 100, 200};
        for (int i = 0; i < bblrTenths.length; i++) {
            String atThreshold = "{\"success\":true,\"player\":{\"stats\":{\"Bedwars\":{\"beds_broken_bedwars\":" + bblrTenths[i] * 10 + ",\"beds_lost_bedwars\":100}}}}";
            check(StatsService.parseBedwars(atThreshold, "overall", new DisplaySettings(Set.of(Category.BBLR))).startsWith(colors[i]), "BBLR color at inclusive threshold " + bblrTenths[i] / 10.0);
            if (i > 0) check(StatsService.bblrColor(bblrTenths[i] * 10 - 1, 100).equals(colors[i - 1]), "BBLR color just below threshold " + bblrTenths[i] / 10.0);
        }
        check(StatsService.bblrColor(1, 0).equals("§5") && StatsService.bblrColor(0, 0).equals("§7"), "infinite BBLR is highest tier; zero/zero is gray");
        check(StatsService.bblrColor((Long.MAX_VALUE / 10) * 2, Long.MAX_VALUE).equals("§7"), "exact fractional BBLR boundary without overflow");
        check(StatsService.bblrColor((Long.MAX_VALUE / 10) * 2, (Long.MAX_VALUE / 10) * 10).equals("§f"), "large counters exactly on BBLR 0.2");
        check(StatsService.parseBedwars("{\"success\":true,\"player\":{\"stats\":{\"Bedwars\":{\"eight_two_beds_broken_bedwars\":14,\"eight_two_beds_lost_bedwars\":10}}}}", "eight_two", new DisplaySettings(Set.of(Category.BBLR))).equals("§e1.40 BBLR"), "BBLR color uses selected mode");
        Category[] countCategories = {Category.WINS, Category.FINAL_DEATHS, Category.BEDS_BROKEN, Category.BEDS_LOST};
        String[] countFields = {"wins_bedwars", "final_deaths_bedwars", "beds_broken_bedwars", "beds_lost_bedwars"};
        long[][] countThresholds = {
            {150, 300, 450, 1500, 2250, 4500, 7500, 15000, 30000},
            {500, 1000, 2500, 5000, 7500, 15000, 25000, 50000, 100000},
            {250, 500, 1250, 2500, 3750, 7500, 12500, 25000, 50000},
            {250, 500, 1250, 2500, 3750, 7500, 12500, 25000, 50000}
        };
        for (int c = 0; c < countCategories.length; c++) {
            Category category = countCategories[c];
            for (int tier = 0; tier < countThresholds[c].length; tier++) {
                long boundary = countThresholds[c][tier];
                for (long value : new long[]{boundary - 1, boundary}) {
                    String sample = "{\"success\":true,\"player\":{\"stats\":{\"Bedwars\":{\"" + countFields[c] + "\":" + value + "}}}}";
                    String expectedColor = colors[tier + (value == boundary ? 1 : 0)];
                    check(StatsService.parseBedwars(sample, "overall", new DisplaySettings(Set.of(category))).startsWith(expectedColor), category + " boundary " + value);
                }
            }
            check(StatsService.countColor(category, 0).equals("§7") && StatsService.countColor(category, Long.MAX_VALUE).equals("§5"), category + " zero and large counter");
            String modeSample = "{\"success\":true,\"player\":{\"stats\":{\"Bedwars\":{\"eight_two_" + countFields[c] + "\":" + countThresholds[c][0] + "}}}}";
            check(StatsService.parseBedwars(modeSample, "eight_two", new DisplaySettings(Set.of(category))).startsWith("§f"), category + " selected mode color");
        }
        long[] kdrTenths = {0, 5, 10, 20, 30, 40, 50, 60, 70, 80};
        for (int i = 0; i < kdrTenths.length; i++) {
            String atThreshold = "{\"success\":true,\"player\":{\"stats\":{\"Bedwars\":{\"kills_bedwars\":" + kdrTenths[i] * 10 + ",\"deaths_bedwars\":100}}}}";
            check(StatsService.parseBedwars(atThreshold, "overall", new DisplaySettings(Set.of(Category.KDR))).startsWith(colors[i]), "KDR color at threshold " + kdrTenths[i] / 10.0);
            if (i > 0) check(StatsService.kdrColor(kdrTenths[i] * 10 - 1, 100).equals(colors[i - 1]), "KDR just below threshold " + kdrTenths[i] / 10.0);
        }
        check(StatsService.kdrColor(1, 0).equals("§5") && StatsService.kdrColor(0, 0).equals("§7"), "KDR infinity and zero/zero");
        check(StatsService.kdrColor(Long.MAX_VALUE / 2, Long.MAX_VALUE).equals("§7"), "KDR below 0.5 without rounding or overflow");
        check(StatsService.kdrColor(Long.MAX_VALUE / 2, (Long.MAX_VALUE / 2) * 2).equals("§f"), "KDR exactly 0.5 with large counts");
        check(StatsService.parseBedwars("{\"success\":true,\"player\":{\"stats\":{\"Bedwars\":{\"eight_two_kills_bedwars\":7,\"eight_two_deaths_bedwars\":1,\"final_kills_bedwars\":100}}}}", "eight_two", new DisplaySettings(Set.of(Category.KDR))).equals("§d7.00 KDR"), "KDR uses normal kills/deaths in selected mode");
        check(StatsService.level(487000) == 100 && StatsService.level(487500) == 101 && StatsService.level(6999) == 3, "prestige experience thresholds");
        check(StatsService.parseUrchinTags("{\"uuid\":\"x\",\"tags\":[{\"type\":\"cheating_closet\",\"reason\":\"private reason\"}]}").contains("U:CHEAT"), "Urchin cheater tag");
        check(StatsService.parseUrchinTags("{\"uuid\":\"x\",\"tags\":[{\"type\":\"SNIPER\",\"reason\":\"private reason\"}]}").contains("U:SNIPER"), "Urchin sniper tag");
        check(!StatsService.parseUrchinTags("{\"tags\":[{\"type\":\"CHEAT\",\"reason\":\"private reason\"}]}").contains("private reason"), "free-form tag reasons are never rendered");
        check(StatsService.parseUrchinTags("{\"uuid\":\"x\",\"tags\":[]}").contains("U:–"), "explicitly unlisted");
        for (String malformed : new String[]{"{}", "{\"tags\":null}", "{\"tags\":[{\"reason\":\"no type\"}]}", "no"})
            check(StatsService.parseUrchinTags(malformed).contains("U:?"), "unknown never unlisted: " + malformed);
        StatsService.Gate gate = new StatsService.Gate();
        StatsService.updateGate(gate, new StatsService.Reply(429, "", "", "", "120"));
        check(gate.until > System.currentTimeMillis() + 119000, "Retry-After honored");
        StatsService.updateGate(gate, new StatsService.Reply(403, "", "", "", "")); check(gate.invalidKey, "invalid key stops requests");
        check(ServerGuard.matchesHost("mc.hypixel.net:25565") && !ServerGuard.matchesHost("hypixel.net.example.org") && !ServerGuard.matchesHost("evilhypixel.net"), "server boundary");
        ServerHosts aliases = ServerHosts.parse("free.stopthelag.lol");
        check(!ServerGuard.matchesHost("free.stopthelag.lol"), "third-party address requires explicit configuration");
        check(aliases.allows(" FREE.STOPTHELAG.LOL.:25565 ") && aliases.allows("mc.hypixel.net"), "configured alias and normal Hypixel permitted");
        for (String blocked : new String[]{"stopthelag.lol", "other.stopthelag.lol", "sub.free.stopthelag.lol", "free.stopthelag.lol.example.org", "https://free.stopthelag.lol", "free.stopthelag.lol:99999", "hypixel.net@evil.org"})
            check(!aliases.allows(blocked), "no alias wildcard or malformed address accepted");
        check(ServerHosts.parse("FREE.STOPTHELAG.LOL,free.stopthelag.lol:25565").aliases.size() == 1, "alias normalization and deduplication");
        for (String malformed : new String[]{"*.stopthelag.lol", "https://hypixel.net", "hypixel..net", "-hypixel.net"}) {
            boolean rejected = false; try { ServerHosts.parse(malformed); } catch (IllegalArgumentException expectedError) { rejected = true; }
            check(rejected, "invalid alias rejected when saving");
        }
        for (String command : new String[]{"/config", " /CONFIG ", "/bwconfig"}) check(GameConfig.isCommand(command), "local command match");
        for (String command : new String[]{"/configure", "/config other", "hello /config", "", "/party"}) check(!GameConfig.isCommand(command), "ordinary chat untouched");
        String detailed = "{\"success\":true,\"player\":{\"stats\":{\"Bedwars\":{\"wins_bedwars\":8,\"losses_bedwars\":4,\"final_kills_bedwars\":15,\"final_deaths_bedwars\":3,\"beds_broken_bedwars\":6,\"beds_lost_bedwars\":2,\"kills_bedwars\":9,\"deaths_bedwars\":6}}}}";
        String[] expected = {"0★", "5.00 FKDR", "8 W", "2.00 WLR", "15 FK", "3 FD", "6 BB", "2 BLost", "1.50 KDR"};
        check(StatsService.parseBedwars(detailed, "overall", new DisplaySettings(Set.of(Category.BBLR))).contains("3.00 BBLR"), "beds broken/lost ratio");
        for (int i = 0; i < expected.length; i++) {
            String one = StatsService.parseBedwars(detailed, "overall", new DisplaySettings(Set.of(Category.values()[i])));
            check(one.replaceAll("§.", "").equals(expected[i]), "independent category: " + Category.values()[i]);
        }
        check(StatsService.parseBedwars("invalid", "overall", new DisplaySettings(Set.of())).isEmpty(), "all stats disabled");
        Path configTest = Files.createTempDirectory(Path.of(args[1]), "settings-");
        Properties saved = new Properties(); saved.setProperty("hypixelKey", "dummy-local-test"); saved.setProperty("mode", "eight_two"); saved.setProperty("unrelated", "preserve");
        DisplaySettings.write(configTest, saved);
        DisplaySettings.defaults().toggle(Category.WINS).save(configTest);
        Properties loaded = DisplaySettings.read(configTest);
        check(loaded.getProperty("hypixelKey").equals("dummy-local-test") && loaded.getProperty("mode").equals("eight_two") && loaded.getProperty("unrelated").equals("preserve"), "menu saves preserve credentials and mode");
        check(!DisplaySettings.from(loaded).enabled(Category.WINS), "legacy showWins preference persists");
        check(DisplaySettings.backgroundOpacity(new Properties()) == 100, "existing configuration retains original background");
        for (String[] sample : new String[][]{{"0", "0"}, {"50", "50"}, {"100", "100"}, {"-4", "0"}, {"400", "100"}, {"broken", "100"}}) {
            Properties opacity = new Properties(); opacity.setProperty(DisplaySettings.BACKGROUND_OPACITY, sample[0]);
            check(DisplaySettings.backgroundOpacity(opacity) == Integer.parseInt(sample[1]), "opacity read/default/clamp");
        }
        Bridge.decorator = (original, info) -> { throw new IllegalStateException(); };
        check(Bridge.decorate("unchanged", new Object()).equals("unchanged"), "render fail-safe"); Bridge.decorator = null;
        // Check the actual installed Badlion class; no game process is accessed.
        try (JarFile game = new JarFile(args[0])) {
            byte[] bytes = game.getInputStream(game.getJarEntry("awh.class")).readAllBytes();
            TabTransformer t = new TabTransformer(); byte[] patched = t.patch(bytes);
            check(patched != null && t.changed.get() == 1, "installed Tab class matches transformer");
            final int[] bridgeCalls = {0};
            new ClassReader(patched).accept(new ClassVisitor(Opcodes.ASM9) {
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                            if (owner.equals("local/bedwarstab/bridge/Bridge")) bridgeCalls[0]++;
                        }
                    };
                }
            }, 0);
            check(bridgeCalls[0] > 0, "bridge callback inserted at return");
            check(t.transform(null, "unrelated", null, null, bytes) == null, "unrelated classes untouched");
            byte[] chat = game.getInputStream(game.getJarEntry("awv.class")).readAllBytes();
            ChatTransformer chatTransformer = new ChatTransformer();
            check(chatTransformer.patch(chat) != null && chatTransformer.changed.get() == 1, "installed chat class matches hook");
            byte[] minecraft = game.getInputStream(game.getJarEntry("ave.class")).readAllBytes();
            TickTransformer tick = new TickTransformer();
            check(tick.patch(minecraft) != null && tick.changed.get() == 1, "installed client tick matches auto /who hook");
        }
        System.out.println("PASS: " + checks + " core checks");
    }
}
