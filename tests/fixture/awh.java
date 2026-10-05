public class awh extends avp {
    private static final java.util.Comparator<bdc> a = java.util.Comparator.comparing(p -> p.a().getName());
    private eu i = () -> "You are playing on MC.HYPIXEL.NET";
    private eu h = () -> "Kills: 3   Final Kills: 2   Beds Broken: 1";
    public static int vanillaRenders;
    public String header = "original header";
    public awh() { }
    public awh(ave minecraft, avo hud) { }
    public String a(bdc player) { if (player == null) return "null"; return player.a().getName(); }
    public void a(int width, auo scoreboard, auk objective) { vanillaRenders++; }
}
