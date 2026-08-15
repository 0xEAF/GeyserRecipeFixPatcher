package net.sideways_sky.geyserrecipefixpatcher.transform;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.function.Consumer;

/**
 * Discards whatever instructions the original method body contained and
 * emits a brand new one instead. Method name/descriptor/access flags (the
 * purely functional signature) are left untouched, so every existing caller
 * compiled against the original method keeps resolving it correctly - only
 * the body (the original author's expression) is removed and replaced with
 * new instructions supplied by the emitter.
 */
final class MethodBodyReplacer extends MethodVisitor {

    private final Consumer<MethodVisitor> emitter;
    private final int maxStack;
    private final int maxLocals;
    private boolean emitted = false;

    MethodBodyReplacer(MethodVisitor delegate, Consumer<MethodVisitor> emitter, int maxStack, int maxLocals) {
        super(Opcodes.ASM9, null);
        this.mv = delegate;
        this.emitter = emitter;
        this.maxStack = maxStack;
        this.maxLocals = maxLocals;
    }

    @Override
    public void visitCode() {
        mv.visitCode();
        emitter.accept(mv);
        emitted = true;
        mv.visitMaxs(maxStack, maxLocals);
        mv.visitEnd();
    }

    // Swallow every part of the original body - we already emitted a
    // complete replacement in visitCode().
    @Override public void visitInsn(int opcode) { }
    @Override public void visitIntInsn(int opcode, int operand) { }
    @Override public void visitVarInsn(int opcode, int var) { }
    @Override public void visitTypeInsn(int opcode, String type) { }
    @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) { }
    @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) { }
    @Override public void visitInvokeDynamicInsn(String name, String descriptor, org.objectweb.asm.Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) { }
    @Override public void visitJumpInsn(int opcode, org.objectweb.asm.Label label) { }
    @Override public void visitLabel(org.objectweb.asm.Label label) { }
    @Override public void visitLdcInsn(Object value) { }
    @Override public void visitIincInsn(int var, int increment) { }
    @Override public void visitTableSwitchInsn(int min, int max, org.objectweb.asm.Label dflt, org.objectweb.asm.Label... labels) { }
    @Override public void visitLookupSwitchInsn(org.objectweb.asm.Label dflt, int[] keys, org.objectweb.asm.Label[] labels) { }
    @Override public void visitMultiANewArrayInsn(String descriptor, int numDimensions) { }
    @Override public void visitTryCatchBlock(org.objectweb.asm.Label start, org.objectweb.asm.Label end, org.objectweb.asm.Label handler, String type) { }
    @Override public void visitLocalVariable(String name, String descriptor, String signature, org.objectweb.asm.Label start, org.objectweb.asm.Label end, int index) { }
    @Override public void visitLineNumber(int line, org.objectweb.asm.Label start) { }
    @Override public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) { }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        // Already emitted by us in visitCode(); ignore whatever the reader
        // computed for the original body.
    }

    @Override
    public void visitEnd() {
        if (!emitted) {
            // Defensive fallback, shouldn't normally happen.
            mv.visitMaxs(maxStack, maxLocals);
        }
        mv.visitEnd();
    }
}
