import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import javax.imageio.ImageIO;

/**
 * Regenerates the app's brand assets from the source artwork in assets/brand.
 *   java tools/BrandAssets.java <project root>
 *
 * statsrader-logo.png  neon mark with its glow, background removed (shown inside the app)
 * statsrader-icon.png  flat mark on a transparent background (window/taskbar icon)
 * statsrader.ico       the flat mark at Windows icon sizes (EXE icon)
 *
 * If assets/brand/statsrader-icon-source.png exists (flat mark, transparent), it is used for the icon;
 * otherwise the flat mark is derived from the neon artwork.
 */
public class BrandAssets {
    static final int FLAT_GREEN = 0x05E28C;

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length > 0 ? args[0] : ".");
        Path brand = root.resolve("assets/brand"), assets = root.resolve("assets");
        BufferedImage neon = ImageIO.read(brand.resolve("statsrader-neon-source.png").toFile());

        // The mark is centred at (572, 617) in the 1173x1341 source; the glow fits in an 860 px square.
        BufferedImage logo = unblend(neon, 572, 617, 860);
        ImageIO.write(scale(logo, 512), "png", assets.resolve("statsrader-logo.png").toFile());

        Path flatSource = brand.resolve("statsrader-icon-source.png");
        BufferedImage flat = Files.isRegularFile(flatSource) ? trim(clean(ImageIO.read(flatSource.toFile()))) : flatMark(neon, 572, 617, 700);
        ImageIO.write(scale(flat, 512), "png", assets.resolve("statsrader-icon.png").toFile());
        writeIco(flat, new int[]{16, 20, 24, 32, 40, 48, 64, 128, 256}, assets.resolve("statsrader.ico"));
        System.out.println("Brand assets written to " + assets.toAbsolutePath());
    }

    /** Removes the dark gradient background but keeps the glow as partial transparency. */
    static BufferedImage unblend(BufferedImage source, int cx, int cy, int side) {
        BufferedImage out = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < side; y++) {
            int sy = cy - side / 2 + y;
            int[] bg = background(source, sy);
            for (int x = 0; x < side; x++) {
                int p = source.getRGB(cx - side / 2 + x, sy);
                int[] c = {(p >> 16) & 255, (p >> 8) & 255, p & 255};
                double alpha = 0;
                for (int i = 0; i < 3; i++) if (c[i] > bg[i]) alpha = Math.max(alpha, (c[i] - bg[i]) / (255.0 - bg[i]));
                // The artwork's background has a slight vignette (about 3 % brightness); fade it out softly.
                double visible = Math.min(1, (alpha - 0.06) / 0.94);
                if (visible <= 0) continue;
                int argb = (int) Math.round(visible * 255) << 24;
                for (int i = 0; i < 3; i++) {
                    int value = (int) Math.round(bg[i] + (c[i] - bg[i]) / alpha);
                    argb |= Math.max(0, Math.min(255, value)) << (16 - 8 * i);
                }
                out.setRGB(x, y, argb);
            }
        }
        return out;
    }
    /** Background of one row, read from the empty left margin of the artwork. */
    static int[] background(BufferedImage source, int y) {
        int[] sum = new int[3];
        for (int x = 4; x < 36; x++) {
            int p = source.getRGB(x, y);
            sum[0] += (p >> 16) & 255; sum[1] += (p >> 8) & 255; sum[2] += p & 255;
        }
        return new int[]{sum[0] / 32, sum[1] / 32, sum[2] / 32};
    }
    /** The solid strokes of the neon mark, without glow, in one flat colour. */
    static BufferedImage flatMark(BufferedImage source, int cx, int cy, int side) {
        BufferedImage out = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < side; y++) for (int x = 0; x < side; x++) {
            int green = (source.getRGB(cx - side / 2 + x, cy - side / 2 + y) >> 8) & 255;
            double alpha = Math.max(0, Math.min(1, (green - 0xB4) / (double) (0xE8 - 0xB4)));
            if (alpha > 0) out.setRGB(x, y, ((int) Math.round(alpha * 255) << 24) | FLAT_GREEN);
        }
        return out;
    }
    /** Drops near-invisible noise (alpha below 32) and makes nearly opaque pixels fully opaque. */
    static BufferedImage clean(BufferedImage image) {
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int p = image.getRGB(x, y), alpha = p >>> 24;
            if (alpha < 32) continue;
            out.setRGB(x, y, alpha >= 250 ? p | 0xFF000000 : p);
        }
        return out;
    }
    /** Crops transparent margins and centres the result in a square with a small border. */
    static BufferedImage trim(BufferedImage image) {
        int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            if ((image.getRGB(x, y) >>> 24) > 8) { minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); }
        if (maxX < 0) throw new IllegalArgumentException("Icon source is fully transparent");
        int side = (int) (Math.max(maxX - minX, maxY - minY) * 1.04) + 1;
        BufferedImage out = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(image, (side - (maxX - minX)) / 2 - minX, (side - (maxY - minY)) / 2 - minY, null);
        g.dispose();
        return out;
    }
    /** High-quality downscale by repeated halving, then one bicubic step. */
    static BufferedImage scale(BufferedImage image, int size) {
        BufferedImage current = image;
        while (current.getWidth() / 2 >= size) current = resize(current, current.getWidth() / 2);
        return current.getWidth() == size ? current : resize(current, size);
    }
    static BufferedImage resize(BufferedImage image, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image, 0, 0, size, size, null);
        g.dispose();
        return out;
    }
    /** Windows .ico with PNG-compressed images (supported since Windows Vista). */
    static void writeIco(BufferedImage image, int[] sizes, Path target) throws IOException {
        byte[][] pngs = new byte[sizes.length][];
        for (int i = 0; i < sizes.length; i++) {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(scale(image, sizes[i]), "png", png);
            pngs[i] = png.toByteArray();
        }
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(target)))) {
            out.writeShort(0); out.writeShort(Short.reverseBytes((short) 1)); out.writeShort(Short.reverseBytes((short) sizes.length));
            int offset = 6 + 16 * sizes.length;
            for (int i = 0; i < sizes.length; i++) {
                out.writeByte(sizes[i] >= 256 ? 0 : sizes[i]); out.writeByte(sizes[i] >= 256 ? 0 : sizes[i]);
                out.writeByte(0); out.writeByte(0);
                out.writeShort(Short.reverseBytes((short) 1)); out.writeShort(Short.reverseBytes((short) 32));
                out.writeInt(Integer.reverseBytes(pngs[i].length)); out.writeInt(Integer.reverseBytes(offset));
                offset += pngs[i].length;
            }
            for (byte[] png : pngs) out.write(png);
        }
    }
}
