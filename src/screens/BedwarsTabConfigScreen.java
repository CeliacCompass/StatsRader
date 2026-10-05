import local.bedwarstab.bridge.Bridge;

/** Small GuiScreen compiled against the installed Minecraft 1.8.9 mappings. */
public final class BedwarsTabConfigScreen extends axu {
    private int top;
    private int footerY;
    private BedwarsTabOpacitySlider opacitySlider;
    private String footer = "";
    public void b() {
        if (opacitySlider != null) opacitySlider.commit();
        n.clear();
        int width = Math.min(360, l - 20), column = (width - 8) / 2;
        int left = (l - width) / 2;
        String[] labels = Bridge.config(-1);
        int rows = (labels.length - 1 + 1) / 2;
        top = Math.max(24, (m - (rows * 22 + 80)) / 2);
        for (int index = 0; index < labels.length - 1; index++)
            n.add(new avs(index, left + (index % 2) * (column + 8), top + (index / 2) * 22, column, 20, labels[index]));
        int sliderY = top + rows * 22 + 4;
        opacitySlider = new BedwarsTabOpacitySlider(left, sliderY, width); n.add(opacitySlider);
        footerY = sliderY + 32;
        n.add(new avs(100, l / 2 - 80, sliderY + 46, 160, 20, "Fertig"));
        footer = labels[labels.length - 1];
    }
    public void a(int mouseX, int mouseY, float partialTicks) {
        c();
        a(q, "§b§lSTATSRADER §f– Einstellungen", l / 2, top - 20, 0xFFFFFF);
        String[] current = Bridge.config(-1); footer = current[current.length - 1];
        a(q, footer, l / 2, footerY, 0xAAAAAA);
        super.a(mouseX, mouseY, partialTicks);
    }
    protected void a(avs button) {
        if (button.k == 200) return;
        if (button.k == 100) { j.a((axu) null); return; }
        String[] labels = Bridge.config(button.k);
        for (avs item : n) if (item.k >= 0 && item.k < labels.length - 1) item.j = labels[item.k];
        footer = labels[labels.length - 1];
    }
    protected void a(char character, int key) {
        if (key == 1) j.a((axu) null);
    }
    public boolean d() { return false; } // The menu does not pause the game.
    public void m() { if (opacitySlider != null) opacitySlider.commit(); super.m(); }
}
