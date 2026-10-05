public class AutoWhoScenario {
    public static void run() throws Exception {
        ave mc = ave.A();
        mc.f.board.scores.add(new aum("Starting in 5s"));
        tickFor(mc, 1200);
        if (!mc.h.sent.isEmpty()) throw new AssertionError("/who sent before round start");
        mc.f.board.scores.clear();
        aul team = new aul(); team.prefix = "§bDiamond II in "; team.suffix = "§r";
        mc.f.board.teams.put("6:00", team); mc.f.board.scores.add(new aum("6:00"));
        tickFor(mc, 1800);
        if (!mc.h.sent.equals(java.util.List.of("/who"))) throw new AssertionError("/who not sent exactly once: " + mc.h.sent);
        if (mc.h.senderThread != Thread.currentThread()) throw new AssertionError("command sent outside game tick thread");
        tickFor(mc, 600);
        if (mc.h.sent.size() != 1) throw new AssertionError("duplicate /who");
        String original = mc.serverData.b; mc.serverData.b = "hypixel.net.example.org";
        mc.f = new bdb(); mc.f.board.scores.add(new aum("Diamond II in 6:00"));
        tickFor(mc, 1300);
        if (mc.h.sent.size() != 1) throw new AssertionError("/who sent on another server");
        mc.serverData.b = original; mc.f = new bdb();
    }
    private static void tickFor(ave mc, long millis) throws InterruptedException {
        long deadline = System.nanoTime() + millis * 1_000_000;
        while (System.nanoTime() < deadline) { mc.s(); Thread.sleep(50); }
    }
}
