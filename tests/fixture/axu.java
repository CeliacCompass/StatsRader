import java.util.*;
public class axu {
    protected ave j = ave.A();
    protected avn q = new avn();
    public int l = 427, m = 240;
    protected List<avs> n = new ArrayList<>();
    public void b() { }
    public void c() { }
    public void a(int x, int y, float ticks) { for (avs button : n) button.a(j, x, y); }
    public void a(avn font, String text, int x, int y, int color) { }
    protected void a(avs button) { }
    protected void a(char c, int key) { }
    public boolean d() { return true; }
    public void m() { }
    public void click(int id) { for (avs button : n) if (button.k == id) { a(button); return; } throw new AssertionError("button missing: " + id); }
    public List<avs> buttons() { return n; }
    public void key(int key) { a('\0', key); }
}
