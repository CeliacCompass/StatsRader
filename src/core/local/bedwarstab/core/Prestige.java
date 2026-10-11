package local.bedwarstab.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Hypixel Bed Wars star formatting. Each prestige is stored exactly as Hypixel shows its first level
 * (for example 1000); any other level of that prestige keeps the colour of every position and only the digits change.
 */
final class Prestige {
    private Prestige() { }

    /** Single colour for levels 0-999, one entry per hundred (0-99 is light grey). */
    private static final String[] UNIFORM = {"§7", "§f", "§6", "§b", "§2", "§3", "§4", "§d", "§9", "§5"};

    /** Templates for 1000, 1100, ... 9900 and 10000+ (index = level / 100 - 10). */
    private static final String[] TEMPLATES = {
        "§c[§61§e0§a0§b0§d✪§5]",          // Rainbow
        "§7[§f1100§7✪]",                    // Iron Prime
        "§7[§e1200§6✪§7]",                  // Gold Prime
        "§7[§b1300§3✪§7]",                  // Diamond Prime
        "§7[§a1400§2✪§7]",                  // Emerald Prime
        "§7[§31500§9✪§7]",                  // Sapphire Prime
        "§7[§c1600§4✪§7]",                  // Ruby Prime
        "§7[§d1700§5✪§7]",                  // Crystal Prime
        "§7[§91800§1✪§7]",                  // Opal Prime
        "§7[§51900§8✪§7]",                  // Amethyst Prime
        "§8[§72§f00§70⚝§8]",               // Mirror
        "§f[2§e10§60⚝]",                    // Light
        "§6[2§f20§b0§3⚝]",                  // Dawn
        "§5[2§d30§60§e⚝]",                  // Dusk
        "§b[2§f40§70⚝§8]",                  // Air
        "§f[2§a50§20⚝]",                    // Wind
        "§4[2§c60§d0⚝§5]",                  // Nebula
        "§e[2§f70§80⚝]",                    // Thunder
        "§a[2§280§60⚝§e]",                  // Earth
        "§b[2§390§90⚝§1]",                  // Water
        "§e[3§600§c0✥§4]",                  // Fire
        "§9[3§310§60✥§e]",                  // Sunrise
        "§c[§43§720§40§c✥]",               // Eclipse
        "§9[33§d0§c0✥§4]",                  // Gamma
        "§2[§a3§d40§50✥§2]",               // Majestic
        "§c[3§450§20§a✥]",                  // Andesine
        "§a[36§b0§90✥§1]",                  // Marine
        "§4[3§c70§b0§3✥]",                  // Element
        "§1[3§98§500§d✥§1]",               // Galaxy
        "§c[3§a90§30§9✥]",                  // Atomic
        "§5[4§c00§60✭§e]",                  // Sunset
        "§e[4§61§c0§d0✭§5]",               // Time
        "§1[§94§32§b0§f0§7✭]",             // Winter
        "§0[§54§830§50✭§0]",               // Obsidian
        "§2[4§a4§e0§60§5✭§d]",             // Spring
        "§f[4§b50§30✭]",                    // Ice
        "§3[§b4§e6§600§d✭§5]",             // Summer
        "§f[§44§c70§90§1✭§9]",             // Spinel
        "§5[4§c8§600§b✭§3]",               // Autumn
        "§2[§a4§f900§a✭§2]",               // Mystic
        "§4[5§50§900§1✭§0]",               // Eternal
        "§4[§c51§60§e0§f✭§4]",             // Burnout
        "§1[§95§32§b0§f0§e✭§1]",           // Cooldown
        "§5[§d5§e3§f0§e0§d✭§5]",           // Obliteration
        "§3[§a5§24§80§20§a✭§3]",           // Ender
        "§2[§a5§e5§f0§b0§d✭§5]",           // Brust
        "§4[§c5§e6§f0§e0§c✭§4]",           // Comical
        "§4[§65§27§30§90§5✭§8]",           // Lusterlost
        "§5[§c5§68§f0§b0§3✭§9]",           // Maelstrom
        "§7[§05§89§70§f0✭§7]",             // Time Undone
        "§c[§f6000§c✭§f]",                  // Umbrella
        "§6[§e6§f100§b✭§3]",               // Luminous
        "§e[§f6§e2§600§f✭§e]",             // Tortilla
        "§a[§e6300§a✭§2]",                  // Corn
        "§b[6§c400§a✭]",                    // Bittersweet
        "§3[6§a50§f0§a✭§3]",               // Sweetsour
        "§9[§d6600§b✭§9]",                  // Pop
        "§5[§d6700§f✭§5]",                  // Bubblegum
        "§0[§668§e00§f✭]",                  // Contrast
        "§a[690§20✭§8]",                    // Blended
        "§3[§b7000§f✭§3]",                  // Allay
        "§4[§c7§61§e0§c0§6✭§e]",           // Blaze
        "§2[§a7§f2§20§a0§f✭§8]",           // Creeper
        "§2[§373§b00§a✭§2]",               // Drowned
        "§8[7400§d✭§8]",                    // Enderman
        "§6[7§250§f0✭]",                    // Frog
        "§f[76§700§c✭§8]",                  // Ghast
        "§d[§c7700§6✭§d]",                  // Hoglin
        "§8[§77§f800§e✭§8]",               // Iron Golem
        "§6[§f7§29§60§20§f✭§6]",           // Jerry
        "§2[§a800§c0§4✭§2]",               // Kringle
        "§8[§78§f1§b0§30§9✭§1]",           // Liquid
        "§f[8200§a✭§f]",                    // Mint
        "§8[8§430§c0✭§8]",                  // Neglected
        "§f[§d840§a0✭§f]",                  // Onion
        "§3[§68500§e✭§3]",                  // Poser
        "§d[§f8600§e✭§d]",                  // Quartz
        "§8[§68700✭§8]",                    // Rich
        "§4[88§c00§f✭]",                    // Sanguine
        "§9[§b890§30✭§9]",                  // Titanic
        "§d[9000§5✭§8]",                    // Unorthodox
        "§0[§c9§610§c0✭§4]",               // Volcanic
        "§2[§d9200§a✭§2]",                  // Weeping Cherry
        "§f[§89300§f✭]",                    // X-ray
        "§e[§69§44§800✭]",                  // Yearn
        "§0[9§850§70✭§f]",                  // Zebra
        "§e[96§000§e✭§0]",                  // Caution
        "§d[97§e00§b✭§e]",                  // Indescribable
        "§0[§89800✭§0]",                    // Forgotten
        "§8[§79§f900§e✭§f]",               // Fuse
        "§9[§b1§f0000§c✭§4]",              // Prestigious (10000+)
    };

    /** One visible character with the colour code in effect for it. */
    private record Glyph(String color, char value) { }
    private static final List<List<Glyph>> PARSED = new ArrayList<>();
    static {
        for (String template : TEMPLATES) {
            List<Glyph> glyphs = new ArrayList<>();
            String color = "";
            for (int i = 0; i < template.length(); i++) {
                char c = template.charAt(i);
                if (c == '§' && i + 1 < template.length()) { color = "§" + template.charAt(++i); continue; }
                glyphs.add(new Glyph(color, c));
            }
            PARSED.add(List.copyOf(glyphs));
        }
    }

    static String format(long level) {
        if (level < 0) level = 0;
        if (level < 1000) return UNIFORM[(int) (level / 100)] + "[" + level + "✫]";
        List<Glyph> glyphs = PARSED.get((int) Math.min(level / 100, 100) - 10);
        String digits = Long.toString(level);
        long templateDigits = glyphs.stream().filter(g -> Character.isDigit(g.value())).count();
        // Levels beyond 99999 have more digits than the template; colour the extra ones like the last digit.
        StringBuilder out = new StringBuilder();
        String current = null;
        int next = 0;
        for (Glyph glyph : glyphs) {
            String text;
            if (Character.isDigit(glyph.value())) {
                boolean last = ++next == templateDigits;
                text = last ? digits.substring(next - 1) : String.valueOf(digits.charAt(next - 1));
            } else text = String.valueOf(glyph.value());
            if (!glyph.color().equals(current)) { out.append(glyph.color()); current = glyph.color(); }
            out.append(text);
        }
        return out.toString();
    }
}
