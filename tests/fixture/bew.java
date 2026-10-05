public class bew {
    public final java.util.List<String> sent = new java.util.ArrayList<>();
    public Thread senderThread;
    public void e(String message) { senderThread = Thread.currentThread(); sent.add(message); }
}
