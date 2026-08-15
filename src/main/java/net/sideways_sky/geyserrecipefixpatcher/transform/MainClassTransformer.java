package net.sideways_sky.geyserrecipefixpatcher.transform;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Applies two small, targeted edits to Geyser_Recipe_Fix.class:
 *
 * 1. The static {@code openMenus} map is allocated as a plain HashMap in
 *    the class's static initializer. We rewrite that single allocation to
 *    ConcurrentHashMap instead. Every other reference to the field uses the
 *    declared type {@code java.util.Map}, so this is invisible to every
 *    other class in the jar - no caller needs to change.
 *
 * 2. Right after {@code logger = getLogger();} inside {@code onEnable()},
 *    we insert one call to our own injected
 *    {@code FoliaRuntimeSupport.onEnable()} helper.
 *
 * Nothing else in the class is touched; every other method's bytecode
 * passes through unchanged.
 */
public final class MainClassTransformer extends ClassVisitor {

    private static final String HASHMAP = "java/util/HashMap";
    private static final String CONCURRENT_HASHMAP = "java/util/concurrent/ConcurrentHashMap";
    private static final String HELPER_OWNER = "net/sideways_sky/geyserrecipefix/FoliaRuntimeSupport";

    private HashMapToConcurrentHashMap hashMapVisitor;
    private LoggerAssignmentHook loggerHookVisitor;

    public MainClassTransformer(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

        if ("<clinit>".equals(name)) {
            hashMapVisitor = new HashMapToConcurrentHashMap(mv);
            return hashMapVisitor;
        }
        if ("onEnable".equals(name) && "()V".equals(descriptor)) {
            loggerHookVisitor = new LoggerAssignmentHook(mv);
            return loggerHookVisitor;
        }
        return mv;
    }

    /** True only if the expected `new HashMap()` allocation was actually found and swapped. */
    public boolean appliedHashMapSwap() {
        return hashMapVisitor != null && hashMapVisitor.applied;
    }

    /** True only if the `logger = getLogger();` assignment was actually found and hooked. */
    public boolean appliedLoggerHook() {
        return loggerHookVisitor != null && loggerHookVisitor.applied;
    }

    /** Swaps `new HashMap()` for `new ConcurrentHashMap()` wherever it appears in <clinit>. */
    private static final class HashMapToConcurrentHashMap extends MethodVisitor {
        boolean applied = false;

        HashMapToConcurrentHashMap(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (opcode == Opcodes.NEW && HASHMAP.equals(type)) {
                super.visitTypeInsn(opcode, CONCURRENT_HASHMAP);
            } else {
                super.visitTypeInsn(opcode, type);
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKESPECIAL && HASHMAP.equals(owner) && "<init>".equals(name) && "()V".equals(descriptor)) {
                super.visitMethodInsn(opcode, CONCURRENT_HASHMAP, name, descriptor, isInterface);
                applied = true;
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }
    }

    /** Inserts a call to FoliaRuntimeSupport.onEnable() right after `logger = getLogger();`. */
    private static final class LoggerAssignmentHook extends MethodVisitor {
        boolean applied = false;

        LoggerAssignmentHook(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            super.visitFieldInsn(opcode, owner, name, descriptor);
            if (opcode == Opcodes.PUTSTATIC && "logger".equals(name) && "Ljava/util/logging/Logger;".equals(descriptor)) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER_OWNER, "onEnable", "()V", false);
                applied = true;
            }
        }
    }
}
