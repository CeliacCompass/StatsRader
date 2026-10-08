public class bdc {
    private final com.mojang.authlib.GameProfile profile;
    public bdc() { this("Player"); }
    public bdc(String name) { profile = new com.mojang.authlib.GameProfile(name); }
    public bdc(String name, java.util.UUID id, aul team) { profile = new com.mojang.authlib.GameProfile(name, id); this.team = team; }
    private aul team = new aul();
    public com.mojang.authlib.GameProfile a() { return profile; }
    public aul i() { return team; }
    public int c() { return 42; }
    public adp.a b() { return adp.a.a; }
    public jy g() { return new jy(); }
}
