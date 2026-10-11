package local.bedwarstab.core;

import java.lang.reflect.*;
import java.util.*;
import local.bedwarstab.bridge.Bridge;

/**
 * Draws the stats line for every visible player from the world render pass (where Forge fires
 * RenderWorldLastEvent). Badlion renders player names without reaching the vanilla label methods,
 * so the renderer hooks alone never run there. Runs only on the render thread and never waits on the API.
 */
final class WorldNameTags {
    private final Method getMinecraft, renderManagerGetter, rendererFor, canRenderName, sneaking, glIsEnabled, disableLighting;
    private final Field world, players, renderX, renderY, renderZ;
    private final Field posX, posY, posZ, lastX, lastY, lastZ;
    private final Class<?> livingRenderer;
    private final Map<Class<?>, String> rendererShapes = new HashMap<>();
    volatile String outcome = "not_seen";
    volatile String playerRenderer = "unknown";

    /** Stats lines come from {@link Bridge#nametagHandler}, the same source the renderer hooks use. */
    WorldNameTags(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> mc = Class.forName("ave", false, loader), manager = Class.forName("biu", false, loader);
        Class<?> entity = Class.forName("pk", false, loader), living = Class.forName("pr", false, loader);
        getMinecraft = mc.getMethod("A");
        renderManagerGetter = mc.getMethod("af");
        world = mc.getField("f");
        players = Class.forName("adm", false, loader).getField("j");
        renderX = accessible(manager.getDeclaredField("o"));
        renderY = accessible(manager.getDeclaredField("p"));
        renderZ = accessible(manager.getDeclaredField("q"));
        rendererFor = manager.getMethod("a", entity);
        livingRenderer = Class.forName("bjl", false, loader);
        canRenderName = accessible(livingRenderer.getDeclaredMethod("a", living));
        sneaking = entity.getMethod("av");
        posX = entity.getField("s"); posY = entity.getField("t"); posZ = entity.getField("u");
        lastX = entity.getField("P"); lastY = entity.getField("Q"); lastZ = entity.getField("R");
        glIsEnabled = Class.forName("org.lwjgl.opengl.GL11", false, loader).getMethod("glIsEnabled", int.class);
        disableLighting = Class.forName("bfl", false, loader).getMethod("f");
    }
    private static <T extends AccessibleObject> T accessible(T member) { member.setAccessible(true); return member; }

    void render(double partialTicks) {
        try {
            Object minecraft = getMinecraft.invoke(null);
            Object currentWorld = minecraft == null ? null : world.get(minecraft);
            if (currentWorld == null) { outcome = "no_world"; return; }
            Object manager = renderManagerGetter.invoke(minecraft);
            double cameraX = renderX.getDouble(manager), cameraY = renderY.getDouble(manager), cameraZ = renderZ.getDouble(manager);
            boolean lightingWasOn = (Boolean) glIsEnabled.invoke(null, 2896); // GL_LIGHTING
            int drawn = 0, candidates = 0;
            try {
                for (Object player : new ArrayList<>((List<?>) players.get(currentWorld))) {
                    Object renderer = rendererFor.invoke(manager, player);
                    if (renderer == null || !livingRenderer.isInstance(renderer)) continue;
                    describe(renderer);
                    // Same visibility rules as vanilla names: hides self, invisible players and hidden team names.
                    if (!(Boolean) canRenderName.invoke(renderer, player) || (Boolean) sneaking.invoke(player)) continue;
                    candidates++;
                    if (Bridge.drawnByRendererHook(player)) continue;
                    double x = interpolate(lastX, posX, player, partialTicks) - cameraX;
                    double y = interpolate(lastY, posY, player, partialTicks) - cameraY;
                    double z = interpolate(lastZ, posZ, player, partialTicks) - cameraZ;
                    var handler = Bridge.nametagHandler;
                    String line = handler == null ? null : handler.apply(new Object[]{renderer, player, "", x, y, z, 64});
                    if (line == null || line.isBlank()) continue;
                    if (Bridge.drawStatsLine(renderer, player, line, x, y, z, 64)) { drawn++; Bridge.worldNametagRows.incrementAndGet(); }
                }
            } finally {
                if (!lightingWasOn) disableLighting.invoke(null); // the vanilla label method re-enables lighting
            }
            outcome = drawn > 0 ? "rows_drawn" : candidates > 0 ? "no_stats_line" : "no_visible_players";
            Bridge.worldNametagState = outcome;
        } catch (ReflectiveOperationException | RuntimeException e) {
            outcome = "error:" + e.getClass().getSimpleName();
            Bridge.worldNametagState = outcome;
        }
    }
    private static double interpolate(Field last, Field now, Object entity, double partialTicks) throws IllegalAccessException {
        double previous = last.getDouble(entity);
        return previous + (now.getDouble(entity) - previous) * partialTicks;
    }
    /** Records which renderer class players use and which name methods it replaces; class names only. */
    private void describe(Object renderer) {
        Class<?> type = renderer.getClass();
        String shape = rendererShapes.get(type);
        if (shape == null) {
            List<String> overrides = new ArrayList<>();
            for (Class<?> c = type; c != null && !Set.of("bln", "bjl", "biv").contains(c.getName()); c = c.getSuperclass()) {
                for (Method method : c.getDeclaredMethods()) {
                    Class<?>[] p = method.getParameterTypes();
                    boolean label = p.length == 6 && p[1] == String.class && p[5] == int.class;
                    boolean offsetLabel = p.length == 7 && p[4] == String.class;
                    boolean name = p.length == 4 && p[1] == double.class && method.getReturnType() == void.class;
                    if (label || offsetLabel || name) overrides.add(c.getName() + "." + method.getName() + "/" + p.length);
                }
            }
            shape = type.getName() + (overrides.isEmpty() ? "" : " overrides=" + String.join(",", overrides));
            rendererShapes.put(type, shape);
        }
        playerRenderer = shape;
    }
}
