package local.bedwarstab.core;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.*;

public final class TabTransformer implements ClassFileTransformer {
    public final AtomicInteger changed = new AtomicInteger();
    public static boolean target(String name) {
        return name.equals("awh") || name.equals("net/minecraft/client/gui/GuiPlayerTabOverlay");
    }
    public byte[] transform(ClassLoader loader, String name, Class<?> type,
                            ProtectionDomain domain, byte[] bytes) {
        if (name == null || !target(name)) return null;
        try { return patch(bytes); }
        catch (RuntimeException e) { Agent.log("Tab transformation failed: " + e.getClass().getSimpleName()); return null; }
    }
    public byte[] patch(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        AtomicInteger methods = new AtomicInteger();
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                MethodVisitor base = super.visitMethod(access, name, desc, sig, ex);
                boolean descriptor = desc.equals("(Lbdc;)Ljava/lang/String;") ||
                    desc.equals("(Lnet/minecraft/client/network/NetworkPlayerInfo;)Ljava/lang/String;");
                if (!descriptor || (access & Opcodes.ACC_STATIC) != 0 ||
                    !(name.equals("a") || name.equals("getPlayerName") || name.equals("func_175243_a"))) return base;
                methods.incrementAndGet();
                return new MethodVisitor(Opcodes.ASM9, base) {
                    @Override public void visitInsn(int opcode) {
                        if (opcode == Opcodes.ARETURN) {
                            super.visitVarInsn(Opcodes.ALOAD, 1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "local/bedwarstab/bridge/Bridge",
                                "decorate", "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;", false);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        }, 0);
        if (methods.get() != 1) return null;
        changed.incrementAndGet();
        return writer.toByteArray();
    }
}
