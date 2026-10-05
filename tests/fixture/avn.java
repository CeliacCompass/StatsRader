public class avn {
    public int a = 9;
    public int a(String text) { return avp.graphics.getFontMetrics().stringWidth(text.replaceAll("§.", "")); }
    public String a(String text, int limit) {
        String result = "";
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '§' && i + 1 < text.length()) { result += text.substring(i, i + 2); i++; continue; }
            String next = result + text.charAt(i); if (a(next) > limit) break; result = next;
        }
        return result;
    }
    public java.util.List<String> c(String text, int width) { return java.util.Arrays.asList(text.split("\\n")); }
    public int a(String text, float x, float y, int color) {
        avp.texts.add(new avp.Text(text, (int)x, (int)y, a(text)));
        int[] colors = {0x101010, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
        for (int i = 0; i + 1 < text.length(); i++) if (text.charAt(i) == '§') {
            int c = Character.digit(text.charAt(i + 1), 16); if (c >= 0) { color = colors[c]; break; }
        }
        avp.graphics.setColor(new java.awt.Color(color));
        avp.graphics.drawString(text.replaceAll("§.", ""), x, y + 8);
        return a(text);
    }
}
