package local.bedwarstab.core;

import java.lang.reflect.*;
import local.bedwarstab.config.ServerHosts;

/** Checks the current connection so another server's UUIDs are not sent to APIs. */
final class ServerGuard {
    private Method instance, server;
    private Field address;
    private long nextCheck;
    private boolean hypixel;
    private final ServerHosts hosts;
    ServerGuard(ClassLoader gameLoader) throws ReflectiveOperationException {
        this(gameLoader, ServerHosts.parse(""));
    }
    ServerGuard(ClassLoader gameLoader, ServerHosts hosts) throws ReflectiveOperationException {
        this.hosts = hosts;
        Class<?> mc;
        try {
            mc = Class.forName("ave", false, gameLoader);
            instance = mc.getMethod("A"); server = mc.getMethod("D");
            address = server.getReturnType().getField("b");
        } catch (ClassNotFoundException e) {
            mc = Class.forName("net.minecraft.client.Minecraft", false, gameLoader);
            try { instance = mc.getMethod("getMinecraft"); server = mc.getMethod("getCurrentServerData"); }
            catch (NoSuchMethodException fallback) { instance = mc.getMethod("func_71410_x"); server = mc.getMethod("func_147104_D"); }
            try { address = server.getReturnType().getField("serverIP"); }
            catch (NoSuchFieldException fallback) { address = server.getReturnType().getField("field_78845_b"); }
        }
    }
    boolean isHypixel() {
        long now = System.currentTimeMillis();
        if (now < nextCheck) return hypixel;
        nextCheck = now + 1000; hypixel = false;
        try {
            Object data = server.invoke(instance.invoke(null));
            if (data != null) hypixel = hosts.allows(String.valueOf(address.get(data)));
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return hypixel;
    }
    static boolean matchesHost(String input) {
        return ServerHosts.parse("").allows(input);
    }
}
