import java.awt.geom.AffineTransform;
public class bfl {
    public static final java.util.Deque<AffineTransform> transforms = new java.util.ArrayDeque<>();
    public static void E() { transforms.push(avp.graphics.getTransform()); }
    public static void F() { avp.graphics.setTransform(transforms.pop()); }
    public static void b(float x, float y, float z) { avp.graphics.translate(x, y); }
    public static void a(float x, float y, float z) { avp.graphics.scale(x, y); }
    public static void c(float r, float g, float b, float a) { }
}
