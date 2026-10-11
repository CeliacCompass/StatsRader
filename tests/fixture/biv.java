import java.util.*;
public class biv<T extends pk> {
    public final List<String> labels = new ArrayList<>();
    public final List<Double> yValues = new ArrayList<>();
    private final avn font = new avn();
    public void a(T entity, double x, double y, double z, float yaw, float partialTicks) {
        a(entity, "VanillaName", x, y, z, 64);
    }
    protected void a(T entity, String text, double x, double y, double z, int distance) {
        labels.add(text); yValues.add(y);
        font.a(text, 0, 0, 0);
        font.a(text, 0, 0, -1);
    }
}
