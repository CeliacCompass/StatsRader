package local.bedwarstab.core;
import java.util.List;
import static local.bedwarstab.core.RoundStartTracker.Phase.*;

public class AutoWhoTest {
    private static int checks;
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        check(RoundStartTracker.phase("§e§lBED WARS", List.of("§bDiamond II in 6:00")) == PLAYING, "formatted match");
        check(RoundStartTracker.phase("BEDWARS", List.of("Smaragd II in 2:00")) == PLAYING, "German timer");
        check(RoundStartTracker.phase("BED WARS", List.of("Starting in 1s", "Diamond II in 6:00")) == WAITING, "waiting takes precedence");
        check(RoundStartTracker.phase("SKYWARS", List.of("Diamond II in 6:00")) == OTHER, "different game");
        check(RoundStartTracker.phase("BED WARS", List.of("Your Level: 123", "Coins: 1200")) == OTHER, "main lobby");
        RoundStartTracker t = new RoundStartTracker(); Object world = new Object();
        check(!t.observe(world, true, WAITING, 0), "no command in queue");
        check(!t.observe(world, true, PLAYING, 500), "debounce initial scoreboard");
        check(t.observe(world, true, PLAYING, 1500), "once at game start");
        check(!t.observe(world, true, PLAYING, 30000), "no command later in same round");
        check(!t.observe(world, true, OTHER, 31000) && !t.observe(world, true, PLAYING, 32000) && !t.observe(world, true, PLAYING, 34000), "sidebar disappearance does not duplicate");
        check(!t.observe(world, true, WAITING, 35000) && !t.observe(world, true, PLAYING, 35500) && !t.observe(world, true, PLAYING, 38000), "brief waiting flicker does not rearm");
        t.observe(world, true, WAITING, 40000); t.observe(world, true, WAITING, 41000); t.observe(world, true, PLAYING, 42000);
        check(t.observe(world, true, PLAYING, 43000), "new round on same world");
        Object nextWorld = new Object(); t.observe(nextWorld, true, PLAYING, 44000);
        check(!t.observe(nextWorld, true, PLAYING, 45500), "rapid world changes obey cooldown");
        check(t.observe(nextWorld, true, PLAYING, 59000), "next game eventually sends once");
        check(!t.observe(new Object(), false, PLAYING, 100000), "never on foreign server");
        check(!t.observe(null, true, PLAYING, 100100), "no world/disconnect");
        System.out.println("PASS: " + checks + " auto /who state and duplicate prevention checks");
    }
}
