import com.sun.tools.attach.VirtualMachine;
public class AttachProbe {
    public static void main(String[] args) throws Exception {
        VirtualMachine vm = VirtualMachine.attach(args[0]);
        try {
            var p = vm.getSystemProperties();
            System.out.println("Attach OK; Java " + p.getProperty("java.version"));
        } finally { vm.detach(); }
    }
}
