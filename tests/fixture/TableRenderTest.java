import java.nio.file.*;
import java.util.*;
import local.bedwarstab.bridge.Bridge;

public class TableRenderTest {
    public static void main(String[] args) throws Exception {
        ave mc = ave.A();
        BedwarsTabPlayerTabOverlay tab = new BedwarsTabPlayerTabOverlay(mc, mc.q);
        Bridge.tableHandler = players -> {
            String[][] rows = new String[players.length + 1][];
            rows[0] = new String[]{"Tag", "Name", "FKDR", "BBLR", "WLR", "Wins", "Seraph"};
            for (int i = 0; i < players.length; i++) rows[i + 1] = new String[]{
                i == 2 ? "§8NICK/NPC" : "§e" + (42 + i * 53) + "★", "", "§b" + (i + 1) + ".25", "§d1.40", "§a2.80", "§a" + (20 + i * 112), i == 4 ? "§cCHEAT" : "§8–"};
            if (players.length > 2) for (int c = 2; c < rows[3].length; c++) rows[3][c] = "§8—";
            return rows;
        };
        for (int[] size : new int[][]{{854,480}, {427,240}, {320,240}}) for (int count : new int[]{8,16,40,80}) {
            avp.reset(size[0], size[1]); mc.screenHeight = size[1]; mc.network.players.clear();
            for (int i = 0; i < count; i++) mc.network.players.add(new bdc(i == 0 ? "§bA LongerPlayerName" : "§aG Player" + String.format("%02d", i)));
            int fallback = awh.vanillaRenders;
            tab.a(size[0], new auo(), new auk());
            if (awh.vanillaRenders != fallback) throw new AssertionError("custom renderer fell back: " + Bridge.lastFailure);
            if (!bfl.transforms.isEmpty()) throw new AssertionError("matrix state leaked");
            long names = avp.texts.stream().filter(t -> t.value().contains("Player")).count();
            if (names != count) throw new AssertionError("players missing: " + names);
            for (var rectangle : avp.bounds) if (rectangle.getMinX() < 0 || rectangle.getMinY() < 0 || rectangle.getMaxX() > size[0] || rectangle.getMaxY() > size[1])
                throw new AssertionError("table outside screen: " + rectangle + " size=" + Arrays.toString(size));
            List<avp.Text> headers = avp.texts.stream().filter(t -> t.value().equals("FKDR")).toList();
            for (avp.Text value : avp.texts) if (value.value().startsWith("§b") && value.value().endsWith(".25"))
                if (headers.stream().noneMatch(h -> h.x() + h.width() == value.x() + value.width())) throw new AssertionError("unaligned numeric column");
            int seraph = avp.texts.stream().filter(t -> t.value().equals("Seraph")).findFirst().orElseThrow().x();
            int ping = avp.texts.stream().filter(t -> t.value().equals("Ping")).findFirst().orElseThrow().x();
            int hp = avp.texts.stream().filter(t -> t.value().equals("HP")).findFirst().orElseThrow().x();
            if (seraph <= ping || hp <= seraph) throw new AssertionError("expected Ping, Seraph, HP at right edge");
            if (size[0] == 854 && count == 8) javax.imageio.ImageIO.write(avp.canvas, "png", Path.of(args[0], "tab-layout-preview.png").toFile());
        }
        mc.network.players.subList(8, mc.network.players.size()).clear(); mc.screenHeight = 480;
        avp.reset(854, 480); Bridge.backgroundHandler = action -> 100; tab.a(854, new auo(), new auk());
        List<avp.Text> fullText = List.copyOf(avp.texts);
        List<Integer> originalColors = List.copyOf(avp.rectangleColors);
        if (originalColors.get(0) != 0xDD101923) throw new AssertionError("100% changed original background");
        avp.reset(854, 480); Bridge.backgroundHandler = action -> 50; tab.a(854, new auo(), new auk());
        if (!avp.texts.equals(fullText)) throw new AssertionError("background dimmed or shifted text");
        for (int i = 0; i < originalColors.size(); i++) {
            int original = originalColors.get(i), actual = avp.rectangleColors.get(i);
            if ((actual >>> 24) != Math.round((original >>> 24) / 2f) || (original & 0xFFFFFF) != (actual & 0xFFFFFF))
                throw new AssertionError("incorrect alpha at 50%");
        }
        avp.reset(854, 480); Bridge.backgroundHandler = action -> 0; tab.a(854, new auo(), new auk());
        if (!avp.bounds.isEmpty() || !avp.texts.equals(fullText)) throw new AssertionError("transparent background must remove all panels/lines and retain text");
        Bridge.backgroundHandler = null;
        Bridge.tableHandler = null; int previous = awh.vanillaRenders;
        tab.a(854, new auo(), null);
        if (awh.vanillaRenders != previous + 1) throw new AssertionError("original Tab not used when inactive");
        System.out.println("PASS: table renderer at 3 resolutions and 8/16/40/80 players; measured alignment, HP last, screen bounds, fallback and matrix restoration");
        System.out.println("PASS: background 0/50/100 percent, original maximum alpha, fully transparent panels/lines and unchanged text");
    }
}
