public class bjl<T extends pr> extends biv<T> {
    /** canRenderName: vanilla hides invisible players, hidden team names and the viewer. */
    protected boolean a(T entity) { return !entity.nameHidden; }
    @SuppressWarnings("unchecked")
    public void a(pk entity, double x, double y, double z, String text, float scale, double distance) {
        a((T) entity, text, x, y, z, 64);
    }
}
