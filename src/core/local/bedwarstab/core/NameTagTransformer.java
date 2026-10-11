package local.bedwarstab.core;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.*;

/** Calls the stats renderer directly after Minecraft draws the original nametag text. */
public final class NameTagTransformer implements ClassFileTransformer {
    public final AtomicInteger changed = new AtomicInteger();
    /** Per class: how often the JVM offered it to us and how often the hook matched. Shows if a client restores classes. */
    public final Map<String, String> classStates = new ConcurrentSkipListMap<>();
    private final Map<String, int[]> counts = new ConcurrentHashMap<>();
    public static boolean target(String name) { return "biu".equals(name) || "biv".equals(name) || "bln".equals(name) || "bfk".equals(name); }

    public byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytes) {
        if (name == null || !target(name)) return null;
        try { return patch(bytes); }
        catch (RuntimeException e) { Agent.log("Nametag transformation failed: " + e.getClass().getSimpleName()); return null; }
    }

    public byte[] patch(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        String name = reader.getClassName();
        byte[] result = switch (name) {
            case "biu" -> patchRenderManager(bytes);
            case "biv" -> patchTextRenderer(bytes);
            case "bln" -> patchBadlionPlayerRenderer(bytes);
            case "bfk" -> patchWorldPass(bytes);
            default -> null;
        };
        int[] count = counts.computeIfAbsent(name, ignored -> new int[2]);
        synchronized (count) {
            count[0]++; if (result != null) count[1]++;
            classStates.put(name, "offered=" + count[0] + ",patched=" + count[1] + ",last=" + (result != null ? "patched" : "no_match"));
        }
        return result;
    }

    /** Inserts Bridge.worldNameTags(partialTicks) right after the profiler enters the "hand" section of the world pass. */
    private byte[] patchWorldPass(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        AtomicInteger calls = new AtomicInteger();
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, desc, signature, exceptions);
                if (!desc.equals("(IFJ)V") || (access & Opcodes.ACC_STATIC) != 0) return base;
                return new MethodVisitor(Opcodes.ASM9, base) {
                    private boolean afterHand;
                    @Override public void visitLdcInsn(Object value) {
                        afterHand = "hand".equals(value);
                        super.visitLdcInsn(value);
                    }
                    @Override public void visitMethodInsn(int opcode, String owner, String called, String calledDesc, boolean isInterface) {
                        super.visitMethodInsn(opcode, owner, called, calledDesc, isInterface);
                        if (!afterHand) return;
                        afterHand = false;
                        if (!calledDesc.equals("(Ljava/lang/String;)V")) return;
                        calls.incrementAndGet();
                        super.visitVarInsn(Opcodes.FLOAD, 2);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "local/bedwarstab/bridge/Bridge", "worldNameTags", "(F)V", false);
                    }
                    @Override public void visitVarInsn(int opcode, int var) { afterHand = false; super.visitVarInsn(opcode, var); }
                    @Override public void visitFieldInsn(int opcode, String owner, String field, String fieldDesc) { afterHand = false; super.visitFieldInsn(opcode, owner, field, fieldDesc); }
                    @Override public void visitInsn(int opcode) { afterHand = false; super.visitInsn(opcode); }
                    @Override public void visitJumpInsn(int opcode, Label label) { afterHand = false; super.visitJumpInsn(opcode, label); }
                };
            }
        }, 0);
        if (calls.get() != 1) return null;
        changed.incrementAndGet();
        return writer.toByteArray();
    }

    private byte[] patchTextRenderer(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        AtomicInteger methods = new AtomicInteger(), draws = new AtomicInteger();
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, desc, signature, exceptions);
                if (!name.equals("a") || !desc.equals("(Lpk;Ljava/lang/String;DDDI)V") || (access & Opcodes.ACC_STATIC) != 0) return base;
                methods.incrementAndGet();
                return new MethodVisitor(Opcodes.ASM9, base) {
                    @Override public void visitMethodInsn(int opcode, String owner, String called, String calledDesc, boolean isInterface) {
                        super.visitMethodInsn(opcode, owner, called, calledDesc, isInterface);
                        if (!owner.equals("avn") || !calledDesc.equals("(Ljava/lang/String;III)I")) return;
                        // This font draw occurs twice: shadow first, then the visible foreground label.
                        if (draws.incrementAndGet() != 2) return;
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitVarInsn(Opcodes.ALOAD, 1);
                        super.visitVarInsn(Opcodes.ALOAD, 2);
                        super.visitVarInsn(Opcodes.DLOAD, 3);
                        super.visitVarInsn(Opcodes.DLOAD, 5);
                        super.visitVarInsn(Opcodes.DLOAD, 7);
                        super.visitVarInsn(Opcodes.ILOAD, 9);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "local/bedwarstab/bridge/Bridge", "nameTag",
                            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;DDDI)V", false);
                    }
                };
            }
        }, 0);
        if (methods.get() != 1 || draws.get() != 2) return null;
        changed.incrementAndGet();
        return writer.toByteArray();
    }

    private byte[] patchBadlionPlayerRenderer(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        AtomicInteger methods = new AtomicInteger(), calls = new AtomicInteger();
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, desc, signature, exceptions);
                if (!name.equals("a") || !desc.equals("(Lbet;DDDLjava/lang/String;FD)V") || (access & Opcodes.ACC_STATIC) != 0) return base;
                methods.incrementAndGet();
                return new MethodVisitor(Opcodes.ASM9, base) {
                    @Override public void visitMethodInsn(int opcode, String owner, String called, String calledDesc, boolean isInterface) {
                        super.visitMethodInsn(opcode, owner, called, calledDesc, isInterface);
                        if (!(owner.equals("bjl") || owner.equals("net/minecraft/client/renderer/entity/RenderLivingEntity")) ||
                            !calledDesc.equals("(Lpk;DDDLjava/lang/String;FD)V")) return;
                        calls.incrementAndGet();
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitVarInsn(Opcodes.ALOAD, 1);
                        super.visitVarInsn(Opcodes.ALOAD, 8);
                        super.visitVarInsn(Opcodes.DLOAD, 2);
                        super.visitVarInsn(Opcodes.DLOAD, 4);
                        super.visitVarInsn(Opcodes.DLOAD, 6);
                        super.visitIntInsn(Opcodes.BIPUSH, 64);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "local/bedwarstab/bridge/Bridge", "nameTag",
                            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;DDDI)V", false);
                    }
                };
            }
        }, 0);
        if (methods.get() != 1 || calls.get() != 1) return null;
        changed.incrementAndGet();
        return writer.toByteArray();
    }

    private byte[] patchRenderManager(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        AtomicInteger methods = new AtomicInteger(), calls = new AtomicInteger();
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, name, desc, signature, exceptions);
                if (!desc.equals("(Lpk;DDDFFZ)Z") || !(name.equals("a") || name.equals("renderEntity") || name.equals("func_147939_a"))) return base;
                methods.incrementAndGet();
                return new MethodVisitor(Opcodes.ASM9, base) {
                    @Override public void visitMethodInsn(int opcode, String owner, String called, String calledDesc, boolean isInterface) {
                        super.visitMethodInsn(opcode, owner, called, calledDesc, isInterface);
                        if (!owner.equals("biv") || !calledDesc.equals("(Lpk;DDDFF)V")) return;
                        calls.incrementAndGet();
                        super.visitVarInsn(Opcodes.ALOAD, 11);
                        super.visitVarInsn(Opcodes.ALOAD, 1);
                        super.visitLdcInsn("");
                        super.visitVarInsn(Opcodes.DLOAD, 2);
                        super.visitVarInsn(Opcodes.DLOAD, 4);
                        super.visitVarInsn(Opcodes.DLOAD, 6);
                        super.visitIntInsn(Opcodes.BIPUSH, 64);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "local/bedwarstab/bridge/Bridge", "nameTag",
                            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;DDDI)V", false);
                    }
                };
            }
        }, 0);
        if (methods.get() < 1 || calls.get() < 1) return null;
        changed.incrementAndGet();
        return writer.toByteArray();
    }
}
