public class ave {
    private static final ave INSTANCE = new ave();
    public static ave A() { return INSTANCE; }
    public bde serverData = new bde();
    public bde D() { return serverData; }
    public bdb f = new bdb();
    public bew h = new bew();
    public avo q = new avo();
    public avn k = new avn();
    public bcy network = new bcy();
    public int screenHeight = 480;
    public bcy u() { return network; }
    public bmj P() { return new bmj(); }
    private final java.util.Queue<Runnable> tasks = new java.util.concurrent.ConcurrentLinkedQueue<>();
    public void a(Runnable task) { tasks.add(task); }
    public void s() { Runnable task; int count = 0; while (count++ < 100 && (task = tasks.poll()) != null) task.run(); }
    public axu m;
    public void a(axu screen) { if (m != null) m.m(); m = screen; if (screen != null) screen.b(); }
}
