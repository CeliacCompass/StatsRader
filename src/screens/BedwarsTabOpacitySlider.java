import local.bedwarstab.bridge.Bridge;

/** Native GuiButton input routing with a continuous slider and a single save on release. */
public final class BedwarsTabOpacitySlider extends avs {
    private int value;
    private boolean dragging, dirty;
    public BedwarsTabOpacitySlider(int x, int y, int width) {
        super(200, x, y, width, 28, "");
        value = Bridge.background(-1); label();
    }
    private void label() {
        j = "Hintergrund: " + (value == 0 ? "Transparent" : value == 100 ? "Standard (100 %)" : value + " %");
    }
    private void update(int mouseX) {
        int next = Math.max(0, Math.min(100, Math.round((mouseX - h - 5) * 100f / Math.max(1, f - 10))));
        if (next != value) { value = Bridge.background(next); dirty = true; label(); }
    }
    public boolean c(ave minecraft, int mouseX, int mouseY) {
        if (!super.c(minecraft, mouseX, mouseY)) return false;
        dragging = true; update(mouseX); return true;
    }
    public void a(ave minecraft, int mouseX, int mouseY) {
        if (!m) return;
        if (dragging) update(mouseX);
        avp.a(h, i, h + f, i + g, 0xDD172634);
        int knob = h + 5 + Math.round((f - 10) * value / 100f);
        avp.a(h + 5, i + 20, h + f - 5, i + 23, 0xFF405464);
        avp.a(h + 5, i + 20, knob, i + 23, 0xFF51BED0);
        avp.a(knob - 3, i + 17, knob + 3, i + 26, dragging ? 0xFFFFFFFF : 0xFF9DE7F0);
        minecraft.k.a(j, (float)(h + (f - minecraft.k.a(j)) / 2), (float)(i + 3), 0xE4EDF4);
    }
    public void a(int mouseX, int mouseY) {
        if (dragging) update(mouseX);
        commit();
    }
    public void commit() {
        dragging = false;
        if (dirty) { value = Bridge.background(1000 + value); dirty = false; label(); }
    }
}
