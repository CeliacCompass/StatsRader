package local.bedwarstab.core;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Detect a stable Bedwars match sidebar, independently of Tab and chat input. */
final class RoundStartTracker {
    enum Phase { OTHER, WAITING, PLAYING }
    private static final Pattern COLORS = Pattern.compile("§[0-9a-fk-or]", Pattern.CASE_INSENSITIVE);
    private static final Pattern EVENT = Pattern.compile("^(diamond|emerald|diamant(?:en)?|smaragd(?:e)?|beds? gone|betten zerstört|sudden death|game end|spielende)\\b.*\\b[0-9]{1,2}:[0-9]{2}\\b.*");
    private Object world;
    private boolean sent;
    private long activeSince = -1, waitingSince = -1, lastSent = Long.MIN_VALUE;

    static String clean(String text) { return COLORS.matcher(text).replaceAll("").trim().toLowerCase(Locale.ROOT); }
    static Phase phase(String title, List<String> lines) {
        if (!clean(title).replace(" ", "").startsWith("bedwars")) return Phase.OTHER;
        boolean playing = false;
        for (String line : lines) {
            String text = clean(line);
            if (text.startsWith("starting in") || text.startsWith("waiting") || text.startsWith("startet in") || text.startsWith("beginnt in") || text.startsWith("warten")) return Phase.WAITING;
            if (EVENT.matcher(text).matches()) playing = true;
        }
        return playing ? Phase.PLAYING : Phase.OTHER;
    }
    boolean observe(Object currentWorld, boolean hypixel, Phase phase, long now) {
        if (!hypixel || currentWorld == null) {
            world = null; sent = false; activeSince = waitingSince = -1; return false;
        }
        if (world != currentWorld) {
            world = currentWorld; sent = false; activeSince = waitingSince = -1;
        }
        if (phase == Phase.WAITING) {
            activeSince = -1;
            if (waitingSince < 0) waitingSince = now;
            // A single transient waiting line must not rearm an already running round.
            if (now - waitingSince >= 1000) sent = false;
            return false;
        }
        waitingSince = -1;
        if (phase != Phase.PLAYING) { activeSince = -1; return false; }
        if (activeSince < 0) activeSince = now;
        if (sent || now - activeSince < 1000 || (lastSent != Long.MIN_VALUE && now - lastSent < 15000)) return false;
        sent = true; lastSent = now; return true;
    }
}
