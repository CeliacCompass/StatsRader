package local.bedwarstab.bridge;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.IntFunction;

/** The only class made visible to the game class loader. No game dependencies. */
public final class Bridge {
    public static final int API_VERSION = 6;
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
