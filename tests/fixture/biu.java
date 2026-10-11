public class biu {
    private final biv<bet> renderer;
    public biu(biv<bet> renderer) { this.renderer = renderer; }
    private double o, p, q;
    public void camera(double x, double y, double z) { o = x; p = y; q = z; }
    public <T extends pk> biv<T> a(pk entity) { @SuppressWarnings("unchecked") biv<T> selected = (biv<T>) (biv<?>) renderer; return selected; }
    public boolean a(pk entity, double x, double y, double z, float yaw, float partialTicks, boolean hitbox) {
        biv<bet> selectedRenderer = renderer;
        selectedRenderer.a((bet) entity, x, y, z, yaw, partialTicks);
        return true;
    }
}
