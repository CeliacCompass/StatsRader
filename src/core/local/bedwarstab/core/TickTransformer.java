package local.bedwarstab.core;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.*;

public final class TickTransformer implements ClassFileTransformer {
    public final AtomicInteger changed = new AtomicInteger();
    public static boolean target(String name) { return "ave".equals(name); }
    public byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytes) {
        if (!target(name)) return null;
        try { return patch(bytes); }
        catch (RuntimeException e) { Agent.log("Tick transformation failed: " + e.getClass().getSimpleName()); return null; }
    }
    public byte[] patch(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        AtomicInteger methods = new AtomicInteger();
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, desc, signature, exceptions);
                if (!name.equals("s") || !desc.equals("()V") || (access & Opcodes.ACC_STATIC) != 0) return base;
                methods.incrementAndGet();
                return new MethodVisitor(Opcodes.ASM9, base) {
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "local/bedwarstab/bridge/Bridge", "tick", "(Ljava/lang/Object;)V", false);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        }, 0);
        if (methods.get() != 1) return null;
        changed.incrementAndGet(); return writer.toByteArray();
    }
}
