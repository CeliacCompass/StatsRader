import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.util.*;

/** Software drawing fixture for layout assertions and an illustrative preview, not a game screenshot. */
public class avp {
    public record Text(String value, int x, int y, int width) { }
    public static BufferedImage canvas;
    public static Graphics2D graphics;
    public static final java.util.List<Text> texts = new ArrayList<>();
    public static final java.util.List<java.awt.geom.Rectangle2D> bounds = new ArrayList<>();
    public static final java.util.List<Integer> rectangleColors = new ArrayList<>();
    static { reset(854, 480); }
    public static void reset(int width, int height) {
        if (graphics != null) graphics.dispose();
        canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        graphics = canvas.createGraphics();
        graphics.setColor(new Color(0x172634)); graphics.fillRect(0, 0, width, height);
        graphics.setFont(new Font("Consolas", Font.PLAIN, 11));
        texts.clear(); bounds.clear(); rectangleColors.clear();
    }
    public static void a(int x1, int y1, int x2, int y2, int color) {
        rectangleColors.add(color);
        bounds.add(graphics.getTransform().createTransformedShape(new Rectangle(x1, y1, x2-x1, y2-y1)).getBounds2D());
        graphics.setColor(new Color(color, true)); graphics.fillRect(x1, y1, x2-x1, y2-y1);
    }
    public static void a(int x, int y, float u, float v, int uw, int vh, int w, int h, float tw, float th) {
        graphics.setColor(new Color(0xB19170)); graphics.fillRect(x, y, w, h);
        graphics.setColor(new Color(0x624A36)); graphics.fillRect(x + 1, y + 2, 2, 2); graphics.fillRect(x + 5, y + 2, 2, 2);
    }
}
