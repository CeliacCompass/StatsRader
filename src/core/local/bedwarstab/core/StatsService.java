package local.bedwarstab.core;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;
import local.bedwarstab.config.DisplaySettings;
import local.bedwarstab.config.DisplaySettings.Category;

/** API calls happen exclusively on a daemon worker, never in the render loop. */
public final class StatsService implements AutoCloseable {
    private static final long[] WIN_COLORS = {150, 300, 450, 1500, 2250, 4500, 7500, 15000, 30000};
    private static final long[] FINAL_DEATH_COLORS = {500, 1000, 2500, 5000, 7500, 15000, 25000, 50000, 100000};
    private static final long[] BED_COLORS = {250, 500, 1250, 2500, 3750, 7500, 12500, 25000, 50000};
    private static final String[] TIER_COLORS = {"§f", "§a", "§2", "§e", "§6", "§c", "§4", "§d", "§5"};
    private volatile String hypixelKey, urchinKey;
    private volatile String mode;
    private final Path settingsRoot;
    private volatile DisplaySettings display;
    private final ScheduledExecutorService worker;
    private final ThreadPoolExecutor requests;
    private final boolean scheduled;
    private final LinkedHashMap<UUID, Entry> cache = new LinkedHashMap<>(64, .75f, true);
    private final LinkedBlockingQueue<UUID> queue = new LinkedBlockingQueue<>(512);
    private final Gate hypixel = new Gate(), urchin = new Gate();
    private volatile boolean closed;
    private long nextCredentialCheck;
    private final Transport transport;
    private final LongSupplier clock;
    private final Set<HttpURLConnection> connections = ConcurrentHashMap.newKeySet();
    @FunctionalInterface interface Transport { Reply fetch(String address, String header, String key); }

    static final class Gate { volatile long until; long nextRequestAt; volatile boolean invalidKey; }
    static final class Entry {
        volatile String bw = "§8BW:…", blacklist = " §8U:…";
        ParsedStats stats;
        volatile long statsDue, blacklistDue, seen = System.currentTimeMillis();
        boolean queued;
        int pendingRequests;
    }
    public StatsService(Path root) throws IOException {
        this(root, null, System::currentTimeMillis, true);
    }
    StatsService(Path root, Transport transport, LongSupplier clock, boolean schedule) throws IOException {
        this.settingsRoot = root;
        this.clock = clock;
        this.scheduled = schedule;
        this.transport = transport == null ? this::fetch : transport;
        Properties p = DisplaySettings.read(root);
        hypixelKey = p.getProperty("hypixelKey", "").trim();
        urchinKey = p.getProperty("urchinKey", "").trim();
        mode = validMode(p.getProperty("mode", "overall"));
        display = DisplaySettings.from(p);
        worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "BedwarsTab-API"); thread.setDaemon(true); return thread;
        });
        requests = new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(4), r -> { Thread thread = new Thread(r, "BedwarsTab-Request"); thread.setDaemon(true); return thread; });
        if (schedule) worker.scheduleWithFixedDelay(this::dispatch, 100, 100, TimeUnit.MILLISECONDS);
    }
    static String validMode(String value) {
        return Set.of("overall", "eight_one", "eight_two", "four_three", "four_four", "two_four").contains(value) ? value : "overall";
    }
    public String suffix(UUID uuid) {
        synchronized (cache) {
            DisplaySettings selected = display;
            if (closed || !selected.any()) return "";
            Entry entry = cache.get(uuid);
            if (entry == null) {
                if (cache.size() >= 512) { Iterator<UUID> iterator = cache.keySet().iterator(); iterator.next(); iterator.remove(); }
                entry = new Entry(); cache.put(uuid, entry);
            }
            long now = clock.getAsLong(); entry.seen = now;
            if (!entry.queued && ((selected.anyStats() && now >= entry.statsDue) ||
                (selected.enabled(Category.BLACKLIST) && now >= entry.blacklistDue))) entry.queued = queue.offer(uuid);
            return " §8│ " + (selected.anyStats() ? entry.bw : "") +
                (selected.enabled(Category.BLACKLIST) ? entry.blacklist : "") + "§r";
        }
    }
    DisplaySettings displaySettings() { return display; }
    /** Cells carry values only; category labels are rendered once in the table header. */
    String[] tableCells(UUID uuid, List<Category> columns) {
        synchronized (cache) {
            String[] cells = new String[columns.size()];
            if (uuid == null || uuid.version() != 4) { Arrays.fill(cells, "§8—"); return cells; }
            suffix(uuid); // Queue the same bounded, asynchronous requests used by the legacy display.
            Entry entry = cache.get(uuid);
            for (int i = 0; i < columns.size(); i++) {
                Category c = columns.get(i);
                if (entry == null) cells[i] = "§8…";
                else if (c == Category.BLACKLIST) cells[i] = entry.blacklist.trim().replace("U:", "").replace("[", "").replace("]", "");
                else if (entry.stats != null && entry.stats.status == null) {
                    String value = entry.stats.fields.getOrDefault(c, "§8—");
                    int unit = value.indexOf(' ');
                    cells[i] = unit < 0 ? value : value.substring(0, unit);
                } else cells[i] = (entry.stats == null ? entry.bw : entry.stats.status).replace("BW:", "");
            }
            return cells;
        }
    }
    void applyDisplaySettings(DisplaySettings selection) {
        synchronized (cache) {
            display = selection;
            for (Entry entry : cache.values()) if (entry.stats != null)
                entry.bw = entry.stats.format(selection);
        }
    }
    void pump() {
        if (closed) return;
        reloadCredentials();
        UUID uuid = queue.poll();
        if (uuid == null || closed) return;
        Entry entry;
        synchronized (cache) { entry = cache.get(uuid); }
        if (entry == null) return;
        processPlayer(uuid, entry);
        synchronized (cache) { entry.queued = false; }
    }
    private void dispatch() {
        if (closed) return;
        long now = clock.getAsLong();
        if (now >= nextCredentialCheck) { reloadCredentials(); nextCredentialCheck = now + 1000; }
        if (requests.getActiveCount() + requests.getQueue().size() >= 3) return;
        UUID uuid = queue.poll();
        if (uuid == null) return;
        Entry entry;
        int count = 0;
        boolean stats, tags;
        synchronized (cache) {
            entry = cache.get(uuid);
            if (entry == null) return;
            now = clock.getAsLong();
            if (now - entry.seen <= 15000) {
                if (display.anyStats() && now >= entry.statsDue) count++;
                if (display.enabled(Category.BLACKLIST) && now >= entry.blacklistDue) count++;
            }
            if (count == 0) { entry.queued = false; return; }
            entry.pendingRequests = count;
            stats = display.anyStats() && now >= entry.statsDue;
            tags = display.enabled(Category.BLACKLIST) && now >= entry.blacklistDue;
        }
        if (stats) submitRequest(uuid, entry, true);
        if (tags) submitRequest(uuid, entry, false);
    }
    private void submitRequest(UUID uuid, Entry entry, boolean stats) {
        try {
            requests.execute(() -> {
                try {
                    if (stats) fetchStats(uuid, entry);
                    else fetchUrchin(uuid, entry);
                } finally {
                    synchronized (cache) {
                        if (--entry.pendingRequests <= 0) entry.queued = false;
                    }
                }
            });
        } catch (RejectedExecutionException rejected) {
            synchronized (cache) { entry.pendingRequests = 0; entry.queued = false; }
        }
    }
    private void processPlayer(UUID uuid, Entry entry) {
        if (closed || clock.getAsLong() - entry.seen > 15000) return;
        if (display.anyStats() && clock.getAsLong() >= entry.statsDue) fetchStats(uuid, entry);
        if (!closed && display.enabled(Category.BLACKLIST) && clock.getAsLong() >= entry.blacklistDue) fetchUrchin(uuid, entry);
    }
    private void fetchStats(UUID uuid, Entry entry) {
        long now = clock.getAsLong();
        if (closed || now - entry.seen > 15000 || !display.anyStats() || now < entry.statsDue) return;
        String key = hypixelKey, selectedMode = mode;
        if (key.isEmpty()) { statsStatus(entry, "§8BW:KEY"); entry.statsDue = Long.MAX_VALUE; return; }
        if (hypixel.invalidKey) { statsStatus(entry, "§cBW:AUTH"); entry.statsDue = Long.MAX_VALUE; return; }
        if (now < hypixel.until) { statsStatus(entry, "§eBW:LIMIT"); entry.statsDue = hypixel.until; return; }
        try {
            if (!awaitPermit(hypixel, 500)) return;
            Reply r = transport.fetch("https://api.hypixel.net/v2/player?uuid=" + uuid.toString().replace("-", ""), "API-Key", key);
            if (closed) return;
            long completed = clock.getAsLong(); updateGate(hypixel, r, completed);
            if (!key.equals(hypixelKey)) return;
            synchronized (cache) {
                entry.stats = r.code == 200 ? parseValues(r.body, selectedMode) : null;
                entry.bw = entry.stats != null ? entry.stats.format(display) : "§eBW:" + label(r.code);
            }
            entry.statsDue = nextDue(r, entry.stats != null && entry.stats.status == null, hypixel, completed);
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            if (!closed) { statsStatus(entry, "§eBW:?"); entry.statsDue = clock.getAsLong() + 60000; Agent.log("Hypixel worker: " + e.getClass().getSimpleName()); }
        }
    }
    private void fetchUrchin(UUID uuid, Entry entry) {
        long now = clock.getAsLong();
        if (closed || now - entry.seen > 15000 || !display.enabled(Category.BLACKLIST) || now < entry.blacklistDue) return;
        String key = urchinKey;
        if (key.isEmpty()) { entry.blacklist = " §8U:KEY"; entry.blacklistDue = Long.MAX_VALUE; return; }
        if (urchin.invalidKey) { entry.blacklist = " §cU:AUTH"; entry.blacklistDue = Long.MAX_VALUE; return; }
        if (now < urchin.until) { entry.blacklist = " §eU:LIMIT"; entry.blacklistDue = urchin.until; return; }
        try {
            if (!awaitPermit(urchin, 1000)) return;
            String address = "https://urchin.ws/player/" + uuid + "?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8) + "&sources=GAME";
            Reply r = transport.fetch(address, "", "");
            if (closed) return;
            long completed = clock.getAsLong(); updateGate(urchin, r, completed);
            if (!key.equals(urchinKey)) return;
            entry.blacklist = r.code == 200 ? parseUrchinTags(r.body) : r.code == 404 ? " §8U:–" : " §eU:" + label(r.code);
            long cacheMillis = r.code == 200 || r.code == 404 ? 300000 : 60000;
            entry.blacklistDue = urchin.invalidKey ? Long.MAX_VALUE : Math.max(completed + cacheMillis, urchin.until);
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            if (!closed) { entry.blacklist = " §eU:?"; entry.blacklistDue = clock.getAsLong() + 60000; Agent.log("Urchin worker: " + e.getClass().getSimpleName()); }
        }
    }
    private boolean awaitPermit(Gate gate, long intervalMillis) throws InterruptedException {
        if (!scheduled) return true;
        synchronized (gate) {
            while (!closed && !gate.invalidKey) {
                long now = clock.getAsLong();
                long ready = Math.max(gate.until, gate.nextRequestAt);
                if (now >= ready) { gate.nextRequestAt = now + intervalMillis; return true; }
                gate.wait(Math.max(1, ready - now));
            }
        }
        return false;
    }
    /** Runs only on the API worker, so old requests finish before credentials and gates change. */
    private void reloadCredentials() {
        try {
            Properties p = DisplaySettings.read(settingsRoot);
            String newHypixel = p.getProperty("hypixelKey", "").trim();
            String newUrchin = p.getProperty("urchinKey", "").trim();
            String newMode = validMode(p.getProperty("mode", "overall"));
            boolean hypixelChanged = !newHypixel.equals(hypixelKey), urchinChanged = !newUrchin.equals(urchinKey);
            boolean modeChanged = !newMode.equals(mode);
            if (!hypixelChanged && !urchinChanged && !modeChanged) return;
            synchronized (cache) {
                hypixelKey = newHypixel; urchinKey = newUrchin; mode = newMode;
                // A display/mode change must not defeat a provider's rate limit or invalid-key gate.
                if (hypixelChanged) synchronized (hypixel) { hypixel.invalidKey = false; hypixel.until = 0; hypixel.notifyAll(); }
                if (urchinChanged) synchronized (urchin) { urchin.invalidKey = false; urchin.until = 0; urchin.notifyAll(); }
                for (Entry entry : cache.values()) {
                    if (hypixelChanged || modeChanged) { entry.stats = null; entry.bw = "§8BW:…"; entry.statsDue = 0; }
                    if (urchinChanged) { entry.blacklist = " §8U:…"; entry.blacklistDue = 0; }
                }
            }
            Agent.log("API settings reloaded (values omitted)");
        } catch (IOException | RuntimeException ignored) { /* Retain working credentials if a file read fails. */ }
    }
    static String label(int code) {
        if (code == 401 || code == 403) return "AUTH";
        if (code == 429) return "LIMIT";
        if (code == 0) return "NET";
        return "HTTP" + code;
    }
    private void statsStatus(Entry entry, String message) {
        synchronized (cache) { entry.stats = null; entry.bw = message; }
    }
    static void updateGate(Gate gate, Reply r) {
        updateGate(gate, r, System.currentTimeMillis());
    }
    static void updateGate(Gate gate, Reply r, long now) {
        synchronized (gate) {
            if (r.code == 401 || r.code == 403) gate.invalidKey = true;
            if (r.code == 429 || "0".equals(r.remaining)) {
                long seconds = 60;
                String delay = r.retryAfter.isEmpty() ? r.reset : r.retryAfter;
                try { seconds = Math.max(1, Long.parseLong(delay)); }
                catch (NumberFormatException ignored) {
                    try { seconds = Math.max(1, (java.time.ZonedDateTime.parse(delay, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - now + 999) / 1000); }
                    catch (java.time.DateTimeException alsoIgnored) { }
                }
                gate.until = now + Math.min(seconds, 86400) * 1000;
            }
            gate.notifyAll();
        }
    }
    private static long nextDue(Reply r, boolean valid, Gate gate, long now) {
        if (gate.invalidKey) return Long.MAX_VALUE;
        return Math.max(now + (r.code == 200 && valid ? 300000 : 60000), gate.until);
    }
    static record Reply(int code, String body, String remaining, String reset, String retryAfter) { }
    private Reply fetch(String address, String header, String key) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(address).toURL().openConnection();
            synchronized (connections) {
                if (closed) return new Reply(0, "", "", "", "");
                connections.add(conn);
            }
            conn.setConnectTimeout(5000); conn.setReadTimeout(5000); conn.setInstanceFollowRedirects(false);
            if (!header.isEmpty()) conn.setRequestProperty(header, key);
            conn.setRequestProperty("User-Agent", "StatsRader/0.3");
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            String body = "";
            if (code == 200) {
                try (InputStream stream = conn.getInputStream()) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] chunk = new byte[8192]; long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                    int count;
                    while ((count = stream.read(chunk)) != -1) {
                        if (closed || System.nanoTime() > deadline || buffer.size() + count > 2 * 1024 * 1024) return new Reply(0, "", "", "", "");
                        buffer.write(chunk, 0, count);
                    }
                    body = buffer.toString(StandardCharsets.UTF_8);
                }
            }
            return new Reply(code, body, header(conn, "RateLimit-Remaining"), header(conn, "RateLimit-Reset"), header(conn, "Retry-After"));
        } catch (IOException | RuntimeException e) { return new Reply(0, "", "", "", ""); }
        finally { if (conn != null) { connections.remove(conn); conn.disconnect(); } }
    }
    private static String header(HttpURLConnection connection, String name) {
        String value = connection.getHeaderField(name); return value == null ? "" : value;
    }
    public static String parseBedwars(String json, String mode, boolean showWins) {
        DisplaySettings settings = DisplaySettings.defaults();
        if (!showWins) settings = settings.toggle(Category.WINS);
        return parseBedwars(json, mode, settings);
    }
    public static String parseBedwars(String json, String mode, DisplaySettings settings) {
        if (!settings.anyStats()) return "";
        return parseValues(json, mode).format(settings);
    }
    /** Store only formatted counters, not complete Hypixel profiles, in the cache. */
    private record ParsedStats(Map<Category, String> fields, String status) {
        String format(DisplaySettings settings) {
            if (!settings.anyStats()) return "";
            if (status != null) return status;
            List<String> visible = new ArrayList<>();
            for (Category c : Category.values()) if (settings.enabled(c) && fields.containsKey(c)) visible.add(fields.get(c));
            return String.join(" ", visible);
        }
    }
    private static ParsedStats unavailable(String message) { return new ParsedStats(Map.of(), message); }
    private static ParsedStats parseValues(String json, String mode) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!success(root)) return unavailable("§eBW:?");
            if (!root.has("player") || root.get("player").isJsonNull()) return unavailable("§8BW:NICK/?");
            JsonObject player = root.getAsJsonObject("player");
            JsonObject bw = object(object(player, "stats"), "Bedwars");
            String prefix = validMode(mode).equals("overall") ? "" : validMode(mode) + "_";
            long kills = number(bw, prefix + "final_kills_bedwars");
            long deaths = number(bw, prefix + "final_deaths_bedwars");
            long wins = number(bw, prefix + "wins_bedwars");
            long losses = number(bw, prefix + "losses_bedwars");
            long bedsBroken = number(bw, prefix + "beds_broken_bedwars");
            long bedsLost = number(bw, prefix + "beds_lost_bedwars");
            long normalKills = number(bw, prefix + "kills_bedwars");
            long normalDeaths = number(bw, prefix + "deaths_bedwars");
            JsonObject achievements = object(player, "achievements");
            long stars = achievements.has("bedwars_level") ? number(achievements, "bedwars_level") : level(number(bw, "Experience"));
            String color = fkdrColor(kills, deaths);
            Map<Category, String> fields = new EnumMap<>(Category.class);
            fields.put(Category.STARS, "§e" + stars + "★");
            fields.put(Category.FKDR, color + ratio(kills, deaths) + " FKDR");
            fields.put(Category.WINS, countColor(Category.WINS, wins) + compact(wins) + " W");
            fields.put(Category.WLR, wlrColor(wins, losses) + ratio(wins, losses) + " WLR");
            fields.put(Category.FINAL_KILLS, "§b" + compact(kills) + " FK");
            fields.put(Category.FINAL_DEATHS, countColor(Category.FINAL_DEATHS, deaths) + compact(deaths) + " FD");
            fields.put(Category.BEDS_BROKEN, countColor(Category.BEDS_BROKEN, bedsBroken) + compact(bedsBroken) + " BB");
            fields.put(Category.BEDS_LOST, countColor(Category.BEDS_LOST, bedsLost) + compact(bedsLost) + " BLost");
            fields.put(Category.KDR, kdrColor(normalKills, normalDeaths) + ratio(normalKills, normalDeaths) + " KDR");
            fields.put(Category.BBLR, bblrColor(bedsBroken, bedsLost) + ratio(bedsBroken, bedsLost) + " BBLR");
            return new ParsedStats(Map.copyOf(fields), null);
        } catch (RuntimeException e) { return unavailable("§eBW:?"); }
    }
    /** Display only the category, never the free-form reason or reporting player's name. */
    public static String parseUrchinTags(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("tags") || !root.get("tags").isJsonArray()) return " §eU:?";
            JsonArray tags = root.getAsJsonArray("tags");
            if (tags.isEmpty()) return " §8U:–";
            JsonElement first = tags.get(0);
            if (!first.isJsonObject()) return " §eU:?";
            JsonElement rawType = first.getAsJsonObject().get("type");
            if (rawType == null || !rawType.isJsonPrimitive() || !rawType.getAsJsonPrimitive().isString()) return " §eU:?";
            String type = rawType.getAsString().trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_ -]", "");
            if (type.isEmpty()) return " §eU:?";
            String label;
            if (type.contains("CHEAT") || type.contains("HACK")) label = "CHEAT";
            else if (type.contains("SNIP")) label = "SNIPER";
            else if (type.contains("CAUTION")) label = "CAUTION";
            else if (type.contains("BOT")) label = "BOT";
            else if (type.contains("ALT") || type.contains("ACCOUNT")) label = "ALT";
            else if (type.contains("INFO")) label = "INFO";
            else label = "TAG";
            return " §c§l[U:" + label + "]§r";
        } catch (RuntimeException e) { return " §eU:?"; }
    }
    static JsonObject object(JsonObject parent, String key) {
        if (!parent.has(key)) return new JsonObject();
        if (!parent.get(key).isJsonObject()) throw new IllegalArgumentException("Invalid object field");
        return parent.getAsJsonObject(key);
    }
    private static boolean success(JsonObject root) {
        return root.has("success") && root.get("success").isJsonPrimitive() && root.getAsJsonPrimitive("success").isBoolean() && root.get("success").getAsBoolean();
    }
    static long number(JsonObject parent, String key) {
        if (!parent.has(key)) return 0;
        JsonElement value = parent.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Invalid numeric field");
        long number = value.getAsBigDecimal().longValueExact();
        if (number < 0) throw new IllegalArgumentException("Negative counter");
        return number;
    }
    static String compact(long n) { return n >= 10000 ? String.format(Locale.ROOT, "%.1fk", n / 1000d) : Long.toString(n); }
    static String countColor(Category category, long count) {
        long[] thresholds = switch (category) {
            case WINS -> WIN_COLORS;
            case FINAL_DEATHS -> FINAL_DEATH_COLORS;
            case BEDS_BROKEN, BEDS_LOST -> BED_COLORS;
            default -> throw new IllegalArgumentException("Unsupported count color category");
        };
        for (int tier = thresholds.length - 1; tier >= 0; tier--)
            if (count >= thresholds[tier]) return TIER_COLORS[tier];
        return "§7";
    }
    static String kdrColor(long kills, long deaths) {
        if (deaths == 0) return kills == 0 ? "§7" : "§5";
        int tenths = java.math.BigInteger.valueOf(kills).multiply(java.math.BigInteger.TEN)
            .divide(java.math.BigInteger.valueOf(deaths)).min(java.math.BigInteger.valueOf(80)).intValue();
        if (tenths >= 80) return "§5";
        if (tenths >= 70) return "§d";
        if (tenths >= 60) return "§4";
        if (tenths >= 50) return "§c";
        if (tenths >= 40) return "§6";
        if (tenths >= 30) return "§e";
        if (tenths >= 20) return "§2";
        if (tenths >= 10) return "§a";
        if (tenths >= 5) return "§f";
        return "§7";
    }
    static String fkdrColor(long kills, long deaths) {
        // Integer thresholds use the exact ratio, without display rounding or floating-point drift.
        long ratio = deaths == 0 ? (kills == 0 ? 0 : Long.MAX_VALUE) : kills / deaths;
        if (ratio >= 100) return "§5";
        if (ratio >= 50) return "§d";
        if (ratio >= 30) return "§4";
        if (ratio >= 20) return "§c";
        if (ratio >= 10) return "§6";
        if (ratio >= 7) return "§e";
        if (ratio >= 5) return "§2";
        if (ratio >= 3) return "§a";
        if (ratio >= 1) return "§f";
        return "§7";
    }
    static String wlrColor(long wins, long losses) {
        if (losses == 0) return wins == 0 ? "§7" : "§5";
        // Compare exact tenths without floating-point rounding or overflowing long counters.
        int tenths = java.math.BigInteger.valueOf(wins).multiply(java.math.BigInteger.TEN)
            .divide(java.math.BigInteger.valueOf(losses)).min(java.math.BigInteger.valueOf(300)).intValue();
        if (tenths >= 300) return "§5";
        if (tenths >= 150) return "§d";
        if (tenths >= 90) return "§4";
        if (tenths >= 60) return "§c";
        if (tenths >= 30) return "§6";
        if (tenths >= 21) return "§e";
        if (tenths >= 15) return "§2";
        if (tenths >= 9) return "§a";
        if (tenths >= 3) return "§f";
        return "§7";
    }
    static String bblrColor(long broken, long lost) {
        if (lost == 0) return broken == 0 ? "§7" : "§5";
        int tenths = java.math.BigInteger.valueOf(broken).multiply(java.math.BigInteger.TEN)
            .divide(java.math.BigInteger.valueOf(lost)).min(java.math.BigInteger.valueOf(200)).intValue();
        if (tenths >= 200) return "§5";
        if (tenths >= 100) return "§d";
        if (tenths >= 60) return "§4";
        if (tenths >= 40) return "§c";
        if (tenths >= 20) return "§6";
        if (tenths >= 14) return "§e";
        if (tenths >= 10) return "§2";
        if (tenths >= 6) return "§a";
        if (tenths >= 2) return "§f";
        return "§7";
    }
    static String ratio(long numerator, long denominator) {
        return denominator == 0 ? (numerator == 0 ? "0.00" : "∞") : String.format(Locale.ROOT, "%.2f", (double) numerator / denominator);
    }
    static long level(long xp) {
        long level = (xp / 487000) * 100; xp %= 487000;
        for (long cost : new long[] {500, 1000, 2000, 3500}) { if (xp < cost) return level; xp -= cost; level++; }
        return level + xp / 5000;
    }
    public void close() {
        closed = true; worker.shutdownNow(); requests.shutdownNow(); queue.clear();
        synchronized (hypixel) { hypixel.notifyAll(); }
        synchronized (urchin) { urchin.notifyAll(); }
        synchronized (connections) { for (HttpURLConnection connection : connections) connection.disconnect(); connections.clear(); }
        synchronized (cache) { cache.clear(); }
    }
}
