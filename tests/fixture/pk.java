public class pk {
    private final java.util.UUID id;
    public pk() { this(java.util.UUID.randomUUID()); }
    public pk(java.util.UUID id) { this.id = id; }
    public java.util.UUID aK() { return id; }
    /** Render positions: last tick (P,Q,R) and current (s,t,u), as in Minecraft 1.8.9. */
    public double P, Q, R, s, t, u;
    public boolean sneaking, nameHidden;
    public boolean av() { return sneaking; }
}
