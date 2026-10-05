import java.lang.instrument.Instrumentation;
public class TestAgent {
    public static Instrumentation instrumentation;
    public static void premain(String options, Instrumentation inst) { instrumentation = inst; }
}
