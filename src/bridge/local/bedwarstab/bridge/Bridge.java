package local.bedwarstab.bridge;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.IntFunction;

/** The only class made visible to the game class loader. No game dependencies. */
public final class Bridge {
    public static final int API_VERSION = 8;
    public static volatile java.util.function.IntUnaryOperator backgroundHandler;
    /** -1 reads, 0..100 previews, 1000..1100 saves the background strength. */
    public static int background(int action) {
        try {
            var fn = backgroundHandler;
            return fn == null ? 100 : Math.max(0, Math.min(100, fn.applyAsInt(action)));
        } catch (Throwable failure) { lastFailure = "background: " + failure.getClass().getName(); return 100; }
    }
    public static int backgroundColor(int color, int strength) {
        int alpha = Math.round((color >>> 24) * Math.max(0, Math.min(100, strength)) / 100f);
        return (color & 0xFFFFFF) | (alpha << 24);
    }
    public static volatile java.util.function.Function<Object[], String[][]> tableHandler;
    /** Header plus one row per player. Null requests the original game's rendering. */
    public static String[][] table(Object[] players) {
        tabCalls.incrementAndGet();
        try {
            var fn = tableHandler;
            return fn == null ? null : fn.apply(players);
        } catch (Throwable failure) { lastFailure = "table: " + failure.getClass().getName(); return null; }
    }
    private static final ThreadLocal<Integer> originalTabNameDepth = ThreadLocal.withInitial(() -> 0);
    public static void beginOriginalTabName() { originalTabNameDepth.set(originalTabNameDepth.get() + 1); }
    public static void endOriginalTabName() { int depth = originalTabNameDepth.get() - 1; if (depth <= 0) originalTabNameDepth.remove(); else originalTabNameDepth.set(depth); }
    public static final java.util.concurrent.atomic.AtomicLong tickCalls = new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong tabCalls = new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong chatCalls = new java.util.concurrent.atomic.AtomicLong();
    public static volatile String lastFailure = "";
    public static volatile java.util.function.Consumer<Object> tickHandler;
    public static void tick(Object minecraft) {
        tickCalls.incrementAndGet();
        try {
            java.util.function.Consumer<Object> fn = tickHandler;
            if (fn != null) fn.accept(minecraft);
        } catch (Throwable failure) { lastFailure = "tick: " + failure.getClass().getName(); }
    }
    public static volatile BiFunction<String, Object, String> decorator;
    public static volatile java.util.function.Function<Object[], String> nametagHandler;
    public static final java.util.concurrent.atomic.AtomicLong nametagCalls = new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong nametagRows = new java.util.concurrent.atomic.AtomicLong();
    public static volatile String nametagState = "not_seen";
    private static final ThreadLocal<Boolean> drawingNametagStats = ThreadLocal.withInitial(() -> false);
    private static Object lastNametagEntity;
    private static long lastNametagNanos;
    public static synchronized void resetNametagDedup() { lastNametagEntity = null; lastNametagNanos = 0L; }
    /** Called after the vanilla name render; the callback may draw one independent line. */
    public static void nameTag(Object renderer, Object entity, String name, double x, double y, double z, int distance) {
        if (drawingNametagStats.get()) return;
        long now = System.nanoTime();
        synchronized (Bridge.class) {
            if (entity == lastNametagEntity && now - lastNametagNanos < 5_000_000L) return;
            lastNametagEntity = entity; lastNametagNanos = now;
        }
        nametagCalls.incrementAndGet();
        try {
            var handler = nametagHandler;
            if (handler == null) { nametagState = "no_handler"; return; }
            String line = handler.apply(new Object[]{renderer, entity, name, x, y, z, distance});
            if (line == null || line.isBlank()) { nametagState = "no_line"; return; }
            if (drawStatsLine(renderer, entity, line, x, y, z, distance)) {
                nametagRows.incrementAndGet(); nametagState = "row_drawn";
                synchronized (Bridge.class) { hookDrawnFrame.put(entity, worldFrame); }
            } else nametagState = "draw_method_missing";
        } catch (Throwable failure) { nametagState = "error:" + failure.getClass().getSimpleName(); lastFailure = "nametag: " + failure.getClass().getName(); }
    }
    /** Draws one label line below the vanilla name with the renderer's own label method. */
    public static boolean drawStatsLine(Object renderer, Object entity, String line, double x, double y, double z, int distance) throws ReflectiveOperationException {
        drawingNametagStats.set(true);
        try {
            for (Class<?> type = renderer.getClass(); type != null; type = type.getSuperclass()) {
                for (var method : type.getDeclaredMethods()) {
                    Class<?>[] p = method.getParameterTypes();
                    if (p.length == 6 && p[0].isInstance(entity) && p[1] == String.class && p[2] == double.class &&
                        p[3] == double.class && p[4] == double.class && p[5] == int.class) {
                        method.setAccessible(true);
                        method.invoke(renderer, entity, line, x, y - 0.25d, z, distance);
                        return true;
                    }
                }
            }
            return false;
        } finally { drawingNametagStats.remove(); }
    }
    /**
     * Called once per world render pass where Forge fires RenderWorldLastEvent. Clients such as Badlion
     * draw player names without the vanilla renderer methods, so this pass draws the stats line itself.
     */
    public static volatile java.util.function.DoubleConsumer worldNametagHandler;
    public static final java.util.concurrent.atomic.AtomicLong worldNametagCalls = new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong worldNametagRows = new java.util.concurrent.atomic.AtomicLong();
    public static volatile String worldNametagState = "not_seen";
    private static final java.util.Map<Object, Long> hookDrawnFrame = new java.util.WeakHashMap<>();
    private static long worldFrame;
    public static void worldNameTags(float partialTicks) {
        worldNametagCalls.incrementAndGet();
        try {
            var handler = worldNametagHandler;
            if (handler == null) worldNametagState = "no_handler";
            else handler.accept(partialTicks);
        } catch (Throwable failure) { worldNametagState = "error:" + failure.getClass().getSimpleName(); lastFailure = "worldNametag: " + failure.getClass().getName(); }
        finally { synchronized (Bridge.class) { worldFrame++; hookDrawnFrame.clear(); } }
    }
    /** True when a renderer hook already drew this entity's stats in the current frame. */
    public static synchronized boolean drawnByRendererHook(Object entity) {
        Long frame = hookDrawnFrame.get(entity);
        return frame != null && frame == worldFrame;
    }
    public static volatile BiPredicate<Object, Integer> chatHandler;
    public static volatile IntFunction<String[]> configHandler;
    public static boolean handleChat(Object screen, int key) {
        chatCalls.incrementAndGet();
        try {
            BiPredicate<Object, Integer> fn = chatHandler;
            return fn != null && fn.test(screen, key);
        } catch (Throwable failure) { lastFailure = "chat: " + failure.getClass().getName(); return false; }
    }
    public static String[] config(int action) {
        try {
            IntFunction<String[]> fn = configHandler;
            if (fn != null) return fn.apply(action);
        } catch (Throwable ignored) { }
        return new String[]{"§cErweiterung nicht aktiv"};
    }
    public static String decorate(String original, Object playerInfo) {
        if (originalTabNameDepth.get() > 0) return original;
        tabCalls.incrementAndGet();
        try {
            BiFunction<String, Object, String> fn = decorator;
            return fn == null ? original : fn.apply(original, playerInfo);
        } catch (Throwable failure) {
            lastFailure = "tab: " + failure.getClass().getName();
            return original; // Never break the game's render loop.
        }
    }
}
