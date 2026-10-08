public class aul extends auq {
    public String prefix = "", suffix = "";
    public String registeredName = "red";
    public String b() { return registeredName; }
    public String e() { return prefix; }
    public static String a(auq team, String name) {
        return team == null ? name : ((aul) team).prefix + name + ((aul) team).suffix;
    }
}
