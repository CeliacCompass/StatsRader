package local.bedwarstab.core;

import java.lang.reflect.*;
import java.util.*;
import local.bedwarstab.config.ServerHosts;

/** Called only at the end of Minecraft's client tick, never from an API worker. */
final class AutoWho implements AutoCloseable {
    private final Field world, player, address;
    private final Method server, scoreboard, objective, title, scores, scoreName, team, format, send;
    private final RoundStartTracker rounds = new RoundStartTracker();
    private long nextCheck;
    private boolean closed, reportedFailure;
    private final ServerHosts hosts;

    AutoWho(ClassLoader loader) throws ReflectiveOperationException {
        this(loader, ServerHosts.parse(""));
    }
    AutoWho(ClassLoader loader, ServerHosts hosts) throws ReflectiveOperationException {
        this.hosts = hosts;
        Class<?> mc = Class.forName("ave", false, loader);
        world = mc.getField("f"); player = mc.getField("h");
        server = mc.getMethod("D"); address = server.getReturnType().getField("b");
        scoreboard = world.getType().getMethod("Z");
        Class<?> board = scoreboard.getReturnType(), obj = Class.forName("auk", false, loader);
        objective = board.getMethod("a", int.class); title = obj.getMethod("d");
        scores = board.getMethod("i", obj);
        scoreName = Class.forName("aum", false, loader).getMethod("e");
        team = board.getMethod("h", String.class);
        format = Class.forName("aul", false, loader).getMethod("a", Class.forName("auq", false, loader), String.class);
        send = player.getType().getMethod("e", String.class);
    }
    synchronized void tick(Object minecraft) {
        if (closed) return;
        long now = System.nanoTime() / 1_000_000;
        if (now < nextCheck) return;
        nextCheck = now + 250;
        try {
            Object currentWorld = world.get(minecraft), currentPlayer = player.get(minecraft), data = server.invoke(minecraft);
            boolean hypixel = data != null && hosts.allows(String.valueOf(address.get(data)));
            if (!hypixel || currentWorld == null || currentPlayer == null) {
                rounds.observe(null, false, RoundStartTracker.Phase.OTHER, now); return;
            }
            Object board = scoreboard.invoke(currentWorld), sidebar = objective.invoke(board, 1);
            List<String> lines = new ArrayList<>();
            String heading = "";
            if (sidebar != null) {
                heading = (String) title.invoke(sidebar);
                for (Object score : (Collection<?>) scores.invoke(board, sidebar)) {
                    String name = (String) scoreName.invoke(score);
                    if (name == null || name.startsWith("#")) continue;
                    lines.add((String) format.invoke(null, team.invoke(board, name), name));
                }
                if (lines.size() > 15) lines = lines.subList(lines.size() - 15, lines.size());
            }
            if (rounds.observe(currentWorld, true, RoundStartTracker.phase(heading, lines), now)) {
                send.invoke(currentPlayer, "/who");
                Agent.log("Auto /who sent after Bedwars match sidebar detected");
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!reportedFailure) { reportedFailure = true; Agent.log("Auto /who failed: " + e.getClass().getSimpleName()); }
        }
    }
    public synchronized void close() { closed = true; }
}
