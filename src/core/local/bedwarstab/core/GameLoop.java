package local.bedwarstab.core;

import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import local.bedwarstab.bridge.Bridge;

/** All game-object changes run through Minecraft's own main-thread task queue. */
final class GameLoop implements AutoCloseable {
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "BedwarsTab-GameQueue"); t.setDaemon(true); return t;
    });
    private final Method instance, submit, display;
    private final Field hud, screen;
    private final Class<?> tabType, chatType;
    private final Constructor<?> tabConstructor, chatConstructor;
    private final GameConfig config;
    private final AtomicBoolean pending = new AtomicBoolean();
    final AtomicLong submitted = new AtomicLong(), executed = new AtomicLong();
    volatile String failure = "";
    private volatile boolean closed;
    private Object installedHud, originalTab, replacementTab;
    private Field tabField;

    GameLoop(ClassLoader loader, GameConfig config) throws ReflectiveOperationException {
        this.config = config;
        Class<?> mc = Class.forName("ave", false, loader), gui = Class.forName("axu", false, loader), ingame = Class.forName("avo", false, loader);
        tabType = Class.forName("awh", false, loader); chatType = Class.forName("awv", false, loader);
        instance = mc.getMethod("A"); submit = mc.getMethod("a", Runnable.class); display = mc.getMethod("a", gui);
        hud = mc.getField("q"); screen = mc.getField("m");
        tabConstructor = config.uiClass("BedwarsTabPlayerTabOverlay").getConstructor(mc, ingame);
        chatConstructor = config.uiClass("BedwarsTabChatScreen").getConstructor();
    }
    void start() { worker.scheduleWithFixedDelay(this::enqueue, 0, 50, TimeUnit.MILLISECONDS); }
    private void enqueue() {
        if (closed || !pending.compareAndSet(false, true)) return;
        try {
            Object mc = instance.invoke(null);
            if (mc == null) { pending.set(false); return; }
            submitted.incrementAndGet();
            submit.invoke(mc, (Runnable) () -> {
                try { update(mc); }
                finally { pending.set(false); }
            });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { pending.set(false); report(e); }
    }
    private synchronized void update(Object mc) {
        if (closed) return;
        executed.incrementAndGet();
        try {
            Object currentHud = hud.get(mc);
            if (currentHud != null) {
                if (installedHud != currentHud) { restoreTab(); installTab(mc, currentHud); }
                else if (tabField != null && tabField.get(currentHud) != replacementTab) {
                    // Respect another mod replacing the overlay; only wrap the exact vanilla type.
                    originalTab = replacementTab = null; installedHud = null; installTab(mc, currentHud);
                }
            }
            Object currentScreen = screen.get(mc);
            if (currentScreen != null && (currentScreen.getClass() == chatType || currentScreen.getClass().getName().equals("net.optifine.gui.GuiChatOF"))) {
                Object replacement = currentScreen.getClass() == chatType ? chatConstructor.newInstance() :
                    config.uiClass("BedwarsTabOptifineChatScreen").getConstructor(chatType).newInstance(currentScreen);
                display.invoke(mc, replacement);
                // Preserve the actual textbox (including selection), history cursor and autocomplete state.
                if (screen.get(mc) == replacement) { copyMutableFields(chatType, currentScreen, replacement); Agent.log("Local config chat installed: " + replacement.getClass().getName()); }
            }
            Bridge.tick(mc);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { report(e); }
    }
    private void installTab(Object mc, Object currentHud) throws ReflectiveOperationException {
        for (Class<?> c = currentHud.getClass(); c != null; c = c.getSuperclass()) for (Field field : c.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != tabType) continue;
            field.setAccessible(true); Object current = field.get(currentHud);
            if (current == null || current.getClass() != tabType) return;
            Object replacement = tabConstructor.newInstance(mc, currentHud);
            copyMutableFields(tabType, current, replacement);
            field.set(currentHud, replacement);
            tabField = field; installedHud = currentHud; originalTab = current; replacementTab = replacement;
            Agent.log("Direct Tab overlay installed"); return;
        }
    }
    private void restoreTab() throws ReflectiveOperationException {
        if (installedHud != null && tabField != null && tabField.get(installedHud) == replacementTab) {
            copyMutableFields(tabType, replacementTab, originalTab); tabField.set(installedHud, originalTab);
        }
        installedHud = originalTab = replacementTab = null; tabField = null;
    }
    private static void copyMutableFields(Class<?> type, Object from, Object to) throws IllegalAccessException {
        for (Field field : type.getDeclaredFields()) if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
            field.setAccessible(true); field.set(to, field.get(from));
        }
    }
    private void report(Throwable e) {
        Throwable cause = e instanceof InvocationTargetException && e.getCause() != null ? e.getCause() : e;
        String name = cause.getClass().getName();
        if (!name.equals(failure)) { failure = name; Agent.log("Game task queue failed: " + name); }
    }
    public synchronized void close() {
        closed = true; worker.shutdownNow();
        try {
            Object mc = instance.invoke(null);
            if (mc != null) submit.invoke(mc, (Runnable) () -> {
                synchronized (GameLoop.this) {
                    try { restoreTab(); }
                    catch (ReflectiveOperationException | RuntimeException e) { report(e); }
                }
            });
        } catch (ReflectiveOperationException | RuntimeException e) { report(e); }
    }
}
