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
        check(overall.contains("§f[123✫]") && overall.contains("7.50") && overall.contains("20 W"), "overall calculation");
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
        String[] expected = {"[0✫]", "5.00 FKDR", "8 W", "2.00 WLR", "15 FK", "3 FD", "6 BB", "2 BLost", "1.50 KDR"};
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
            byte[] nameRenderer = game.getInputStream(game.getJarEntry("biv.class")).readAllBytes();
            NameTagTransformer nameTag = new NameTagTransformer(); byte[] named = nameTag.patch(nameRenderer);
            check(named != null && nameTag.changed.get() == 1, "Badlion visible nametag text renderer matches hook");
            final int[] nameTagCalls = {0};
            new ClassReader(named).accept(new ClassVisitor(Opcodes.ASM9) {
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                            if (owner.equals("local/bedwarstab/bridge/Bridge") && name.equals("nameTag")) nameTagCalls[0]++;
                        }
                    };
                }
            }, 0);
            check(nameTagCalls[0] == 1, "stats callback follows the visible foreground nametag text");
            byte[] badlionRenderer = game.getInputStream(game.getJarEntry("bln.class")).readAllBytes();
            NameTagTransformer playerTag = new NameTagTransformer(); byte[] playerTagBytes = playerTag.patch(badlionRenderer);
            check(playerTagBytes != null && playerTag.changed.get() == 1, "Badlion player-name method matches fallback hook");
            byte[] renderManager = game.getInputStream(game.getJarEntry("biu.class")).readAllBytes();
            NameTagTransformer managerTag = new NameTagTransformer(); byte[] managerBytes = managerTag.patch(renderManager);
            check(managerBytes != null && managerTag.changed.get() == 1, "Badlion RenderManager matches fallback hook");
            byte[] entityRenderer = game.getInputStream(game.getJarEntry("bfk.class")).readAllBytes();
            NameTagTransformer worldTag = new NameTagTransformer(); byte[] worldBytes = worldTag.patch(entityRenderer);
            check(worldBytes != null && worldTag.changed.get() == 1, "installed world render pass matches nametag hook");
            final int[] worldCalls = {0};
            new ClassReader(worldBytes).accept(new ClassVisitor(Opcodes.ASM9) {
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                            if (owner.equals("local/bedwarstab/bridge/Bridge") && name.equals("worldNameTags") && desc.equals("(F)V")) worldCalls[0]++;
                        }
                    };
                }
            }, 0);
            check(worldCalls[0] == 1, "world pass calls the nametag renderer exactly once per pass");
            check(worldTag.classStates.get("bfk").equals("offered=1,patched=1,last=patched"), "per-class nametag hook state is recorded");
            check(worldTag.patch(game.getInputStream(game.getJarEntry("ave.class")).readAllBytes()) == null, "unrelated classes are not given a world hook");
        }
        check(Prestige.format(7).equals("§7[7✫]") && Prestige.format(99).equals("§7[99✫]"), "stars 0-99 light grey");
        check(Prestige.format(150).equals("§f[150✫]") && Prestige.format(999).equals("§5[999✫]"), "stars below 1000 use one prestige colour");
        check(Prestige.format(1000).equals("§c[§61§e0§a0§b0§d✪§5]"), "rainbow prestige template");
        check(Prestige.format(1050).equals("§c[§61§e0§a5§b0§d✪§5]"), "rainbow colours stay on their positions for other levels");
        check(Prestige.format(1234).equals("§7[§e1234§6✪§7]"), "each hundred is its own prestige");
        check(Prestige.format(2050).equals("§8[§72§f05§70⚝§8]"), "mirror digit positions");
        check(Prestige.format(9999).equals("§8[§79§f999§e✭§f]"), "last regular prestige");
        check(Prestige.format(10000).equals("§9[§b1§f0000§c✭§4]") && Prestige.format(12345).equals("§9[§b1§f2345§c✭§4]"), "prestigious 10000+");
        check(Prestige.format(123456).replaceAll("§.", "").equals("[123456✭]"), "levels with more digits keep every digit");
        // Every prestige from 100 to 10000 reproduces Hypixel's own colour codes at its first level.
        String[] hypixel = {"§f[100✫]", "§6[200✫]", "§b[300✫]", "§2[400✫]", "§3[500✫]", "§4[600✫]", "§d[700✫]", "§9[800✫]", "§5[900✫]",
            "§c[§61§e0§a0§b0§d✪§5]", "§7[§f1100§7✪]", "§7[§e1200§6✪§7]", "§7[§b1300§3✪§7]", "§7[§a1400§2✪§7]", "§7[§31500§9✪§7]",
            "§7[§c1600§4✪§7]", "§7[§d1700§5✪§7]", "§7[§91800§1✪§7]", "§7[§51900§8✪§7]", "§8[§72§f00§70⚝§8]", "§f[2§e10§60⚝]",
            "§6[2§f20§b0§3⚝]", "§5[2§d30§60§e⚝]", "§b[2§f40§70⚝§8]", "§f[2§a50§20⚝]", "§4[2§c60§d0⚝§5]", "§e[2§f70§80⚝]",
            "§a[2§280§60⚝§e]", "§b[2§390§90⚝§1]", "§e[3§600§c0✥§4]", "§9[3§310§60✥§e]", "§c[§43§720§40§c✥]", "§9[33§d0§c0✥§4]",
            "§2[§a3§d40§50✥§2]", "§c[3§450§20§a✥]", "§a[36§b0§90✥§1]", "§4[3§c70§b0§3✥]", "§1[3§98§500§d✥§1]", "§c[3§a90§30§9✥]",
            "§5[4§c00§60✭§e]", "§e[4§61§c0§d0✭§5]", "§1[§94§32§b0§f0§7✭]", "§0[§54§830§50✭§0]", "§2[4§a4§e0§60§5✭§d]", "§f[4§b50§30✭]",
            "§3[§b4§e6§600§d✭§5]", "§f[§44§c70§90§1✭§9]", "§5[4§c8§600§b✭§3]", "§2[§a4§f900§a✭§2]", "§4[5§50§900§1✭§0]",
            "§4[§c51§60§e0§f✭§4]", "§1[§95§32§b0§f0§e✭§1]", "§5[§d5§e3§f0§e0§d✭§5]", "§3[§a5§24§80§20§a✭§3]", "§2[§a5§e5§f0§b0§d✭§5]",
            "§4[§c5§e6§f0§e0§c✭§4]", "§4[§65§27§30§90§5✭§8]", "§5[§c5§68§f0§b0§3✭§9]", "§7[§05§89§70§f0✭§7]", "§c[§f6000§c✭§f]",
            "§6[§e6§f100§b✭§3]", "§e[§f6§e2§600§f✭§e]", "§a[§e6300§a✭§2]", "§b[6§c400§a✭]", "§3[6§a50§f0§a✭§3]", "§9[§d6600§b✭§9]",
            "§5[§d6700§f✭§5]", "§0[§668§e00§f✭]", "§a[690§20✭§8]", "§3[§b7000§f✭§3]", "§4[§c7§61§e0§c0§6✭§e]", "§2[§a7§f2§20§a0§f✭§8]",
            "§2[§373§b00§a✭§2]", "§8[7400§d✭§8]", "§6[7§250§f0✭]", "§f[76§700§c✭§8]", "§d[§c7700§6✭§d]", "§8[§77§f800§e✭§8]",
            "§6[§f7§29§60§20§f✭§6]", "§2[§a800§c0§4✭§2]", "§8[§78§f1§b0§30§9✭§1]", "§f[8200§a✭§f]", "§8[8§430§c0✭§8]", "§f[§d840§a0✭§f]",
            "§3[§68500§e✭§3]", "§d[§f8600§e✭§d]", "§8[§68700✭§8]", "§4[88§c00§f✭]", "§9[§b890§30✭§9]", "§d[9000§5✭§8]",
            "§0[§c9§610§c0✭§4]", "§2[§d9200§a✭§2]", "§f[§89300§f✭]", "§e[§69§44§800✭]", "§0[9§850§70✭§f]", "§e[96§000§e✭§0]",
            "§d[97§e00§b✭§e]", "§0[§89800✭§0]", "§8[§79§f900§e✭§f]", "§9[§b1§f0000§c✭§4]"};
        for (int i = 0; i < hypixel.length; i++) {
            long base = (i + 1) * 100L;
            check(Prestige.format(base).equals(hypixel[i]), "prestige colours match Hypixel at " + base);
        }
        System.out.println("PASS: " + checks + " core checks");
    }
}
