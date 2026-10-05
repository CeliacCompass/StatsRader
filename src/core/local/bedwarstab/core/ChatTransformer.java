package local.bedwarstab.core;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.*;

/** Cancel Enter in GuiChat before the command is sent or the new screen closed. */
public final class ChatTransformer implements ClassFileTransformer {
    public final AtomicInteger changed = new AtomicInteger();
    public static boolean target(String name) { return name.equals("awv"); }
    public byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytes) {
        if (name == null || !target(name)) return null;
        try { return patch(bytes); }
        catch (RuntimeException e) { Agent.log("Chat transformation failed: " + e.getClass().getSimpleName()); return null; }
    }
    public byte[] patch(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        AtomicInteger methods = new AtomicInteger();
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                MethodVisitor base = super.visitMethod(access, name, desc, sig, ex);
                if (!name.equals("a") || !desc.equals("(CI)V") || (access & Opcodes.ACC_STATIC) != 0) return base;
                methods.incrementAndGet();
                return new MethodVisitor(Opcodes.ASM9, base) {
                    public void visitCode() {
                        super.visitCode();
                        super.visitVarInsn(Opcodes.ALOAD, 0); super.visitVarInsn(Opcodes.ILOAD, 2);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "local/bedwarstab/bridge/Bridge", "handleChat", "(Ljava/lang/Object;I)Z", false);
                        Label pass = new Label(); super.visitJumpInsn(Opcodes.IFEQ, pass);
                        super.visitInsn(Opcodes.RETURN); super.visitLabel(pass);
                        super.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    }
                };
            }
        }, 0);
        if (methods.get() != 1) return null;
        changed.incrementAndGet(); return writer.toByteArray();
    }
}
