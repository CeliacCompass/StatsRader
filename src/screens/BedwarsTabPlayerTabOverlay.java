import local.bedwarstab.bridge.Bridge;
import java.util.*;
import java.lang.reflect.Field;

/** Virtual override also works when another transformer replaces vanilla method bodies. */
public final class BedwarsTabPlayerTabOverlay extends awh {
    private final ave minecraft;
    private static final int ROW = 13, GAP = 12, PAD = 6;
    public BedwarsTabPlayerTabOverlay(ave minecraft, avo hud) { super(minecraft, hud); this.minecraft = minecraft; }
    public String a(bdc player) {
        String original;
        Bridge.beginOriginalTabName();
        try { original = super.a(player); }
        finally { Bridge.endOriginalTabName(); }
        return Bridge.decorate(original, player);
    }
    private String originalName(bdc player) {
        Bridge.beginOriginalTabName();
        try { return super.a(player); } finally { Bridge.endOriginalTabName(); }
    }
    /** Draw separate cells, rather than aligning variable-width text with spaces. */
    public void a(int screenWidth, auo scoreboard, auk objective) {
        try {
            if (minecraft.u() == null) { super.a(screenWidth, scoreboard, objective); return; }
            List<bdc> players = new ArrayList<>(minecraft.u().d());
            Field ordering = awh.class.getDeclaredField("a"); ordering.setAccessible(true);
            @SuppressWarnings("unchecked") Comparator<bdc> comparator = (Comparator<bdc>) ordering.get(null);
            players.sort(comparator);
            if (players.size() > 80) players = new ArrayList<>(players.subList(0, 80));
            String[][] supplied = Bridge.table(players.toArray());
            if (supplied == null || players.isEmpty()) { super.a(screenWidth, scoreboard, objective); return; }
            renderTable(screenWidth, scoreboard, objective, players, supplied);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            Bridge.lastFailure = "table render: " + failure.getClass().getName();
            super.a(screenWidth, scoreboard, objective);
        }
    }
    private void renderTable(int screenWidth, auo scoreboard, auk objective, List<bdc> players, String[][] supplied) throws ReflectiveOperationException {
        avn font = minecraft.k;
        boolean score = objective != null;
        int last = supplied[0].length - 1;
        boolean seraph = "Seraph".equals(supplied[0][last]);
        int extra = score ? 2 : 1, insert = seraph ? last : last + 1;
        int columns = supplied[0].length + extra;
        String[][] cells = new String[supplied.length][columns];
        for (int r = 0; r < cells.length; r++) {
            System.arraycopy(supplied[r], 0, cells[r], 0, insert);
            System.arraycopy(supplied[r], insert, cells[r], insert + 1, supplied[r].length - insert);
        }
        int scoreColumn = columns - 1;
        if (score) cells[0][scoreColumn] = "HEARTS".equals(objective.e().name()) ? "HP" : "Score";
        int ping = insert;
        cells[0][ping] = "Ping";
        for (int r = 1; r < cells.length; r++) {
            bdc player = players.get(r - 1);
            cells[r][1] = originalName(player);
            if (player.b() == adp.a.e) cells[r][1] = "§7§o" + cells[r][1];
            if (score) cells[r][scoreColumn] = player.b() == adp.a.e ? "§8—" : "§e" + scoreboard.c(player.a().getName(), objective).c();
            cells[r][ping] = player.c() < 0 ? "§8?" : "§7" + player.c();
        }
        int[] widths = new int[columns];
        for (int c = 0; c < columns; c++) {
            int cap = c == 1 ? 190 : 86;
            for (int r = 0; r < cells.length; r++) {
                String value = cells[r][c] == null ? "§8—" : cells[r][c];
                cells[r][c] = fit(font, value, cap);
                widths[c] = Math.max(widths[c], font.a(cells[r][c]) + (c == 1 ? 11 : 0));
            }
            widths[c] += PAD * 2;
        }
        List<String> header = serverLines("i", font, Math.max(240, screenWidth - 30));
        List<String> footer = serverLines("h", font, Math.max(240, screenWidth - 30));
        int textWidth = 0;
        for (String line : header) textWidth = Math.max(textWidth, font.a(line) + 12);
        for (String line : footer) textWidth = Math.max(textWidth, font.a(line) + 12);
        int panelWidth = Arrays.stream(widths).sum();
        int overhead = (header.size() + footer.size()) * 11 + 12;
        int screenHeight = new avr(minecraft).b();
        int panels = 1, rows = players.size();
        float scale = 0;
        // Choose the most readable arrangement that keeps up to the vanilla limit of 80 players visible.
        for (int candidate = 1; candidate <= Math.min(4, players.size()); candidate++) {
            int candidateRows = (players.size() + candidate - 1) / candidate;
            int candidateWidth = Math.max(textWidth, panelWidth * candidate + GAP * (candidate - 1));
            float candidateScale = Math.min(1f, Math.min((screenWidth - 20f) / candidateWidth,
                (screenHeight - 30f) / ((candidateRows + 1) * ROW + overhead)));
            if (candidateScale > scale + .001f) { scale = candidateScale; panels = candidate; rows = candidateRows; }
        }
        int bodyWidth = panelWidth * panels + GAP * (panels - 1);
        int totalWidth = Math.max(textWidth, bodyWidth);
        int height = (rows + 1) * ROW + overhead;
        int strength = Bridge.background(-1);
        bfl.E();
        try {
            bfl.b((screenWidth - totalWidth * scale) / 2f, 10f, 0f);
            bfl.a(scale, scale, 1f);
            background(-2, -2, totalWidth + 2, height + 2, 0xDD101923, strength);
            int y = 4;
            for (String line : header) { text(font, line, (totalWidth - font.a(line)) / 2, y, 0xD3DFE9); y += 11; }
            if (!header.isEmpty()) y += 3;
            int tableTop = y, left = (totalWidth - bodyWidth) / 2;
            for (int panel = 0; panel < panels; panel++) {
                int px = left + panel * (panelWidth + GAP);
                background(px, tableTop, px + panelWidth, tableTop + ROW, 0xF0273B4D, strength);
                background(px, tableTop + ROW - 1, px + panelWidth, tableTop + ROW, 0xFF51BED0, strength);
                drawCells(font, cells[0], widths, px, tableTop + 2, true, null, strength);
                for (int row = 0; row < rows; row++) {
                    int index = panel * rows + row;
                    if (index >= players.size()) break;
                    int ry = tableTop + (row + 1) * ROW;
                    background(px, ry, px + panelWidth, ry + ROW, row % 2 == 0 ? 0x90212D3A : 0x90192430, strength);
                    drawCells(font, cells[index + 1], widths, px, ry + 2, false, players.get(index), strength);
                }
            }
            y = tableTop + (rows + 1) * ROW + 4;
            for (String line : footer) { text(font, line, (totalWidth - font.a(line)) / 2, y, 0xBBCAD8); y += 11; }
        } finally { bfl.c(1f, 1f, 1f, 1f); bfl.F(); }
    }
    private void drawCells(avn font, String[] cells, int[] widths, int x, int y, boolean header, bdc player, int strength) {
        for (int c = 0; c < cells.length; c++) {
            if (c > 0) background(x, y - 1, x + 1, y + ROW - 2, 0x183F667D, strength);
            int tx = c == 1 ? x + PAD + 11 : x + widths[c] - PAD - font.a(cells[c]);
            if (c == 1 && player != null) {
                bfl.c(1f, 1f, 1f, 1f); minecraft.P().a(player.g());
                avp.a(x + PAD, y, 8f, 8f, 8, 8, 8, 8, 64f, 64f);
                avp.a(x + PAD, y, 40f, 8f, 8, 8, 8, 8, 64f, 64f);
            }
            text(font, cells[c], tx, y, header ? 0x82DCE8 : 0xE4EDF4);
            x += widths[c];
        }
    }
    private static void background(int x1, int y1, int x2, int y2, int color, int strength) {
        int tinted = Bridge.backgroundColor(color, strength);
        if ((tinted >>> 24) != 0) avp.a(x1, y1, x2, y2, tinted);
    }
    private static void text(avn font, String value, int x, int y, int color) { font.a(value, (float) x, (float) y, color); }
    private static String fit(avn font, String value, int width) {
        return font.a(value) <= width ? value : font.a(value, width - font.a("…")) + "§r…";
    }
    private List<String> serverLines(String name, avn font, int width) throws ReflectiveOperationException {
        Field field = awh.class.getDeclaredField(name); field.setAccessible(true);
        eu component = (eu) field.get(this);
        return component == null ? List.of() : font.c(component.d(), width);
    }
}
