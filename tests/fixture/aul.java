public class aul extends auq {
    public String prefix = "", suffix = "";
    public static String a(auq team, String name) {
        return team == null ? name : ((aul) team).prefix + name + ((aul) team).suffix;
    }
}
