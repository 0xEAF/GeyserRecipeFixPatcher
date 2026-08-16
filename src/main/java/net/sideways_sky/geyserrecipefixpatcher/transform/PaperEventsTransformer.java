package net.sideways_sky.geyserrecipefixpatcher.transform;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Applies three small, targeted edits to PaperEvents.class:
 *
 * 1. Widens {@code forwardSkips} from private to package-private, so our
 *    injected {@code FoliaAnvilBridge} class (deliberately placed in the
 *    same package) can register skips in the exact same set instance the
 *    rest of this class already checks. The field's type and semantics are
 *    untouched.
 *
 * 2. Rewrites {@code forwardSkips}'s allocation in the static initializer
 *    from `new HashSet<>()` to `ConcurrentHashMap.newKeySet()`, matching
 *    what the field is declared as ({@code java.util.Set}), so it's safe to
 *    touch from multiple region threads on Folia.
 *
 * 3. Replaces the body of {@code openForward(HumanEntity)} with a single
 *    call to {@code FoliaAnvilBridge.openForward(HumanEntity)}. The
 *    method's own name/descriptor/access are left exactly as-is, so the
 *    unmodified caller elsewhere in the jar keeps resolving it normally.
 */
public final class PaperEventsTransformer extends ClassVisitor {

    private static final String HASHSET = "java/util/HashSet";
    private static final String CONCURRENT_HASHMAP = "java/util/concurrent/ConcurrentHashMap";
    // ConcurrentHashMap.newKeySet()'s actual JVM return type is the concrete
    // nested class ConcurrentHashMap.KeySetView (which implements Set) - the
    // generic erasure here keeps the declared return type, it does NOT erase
    // to java.util.Set. Method resolution is by exact name+descriptor match,
    // so using "()Ljava/util/Set;" here is a NoSuchMethodError at runtime.
    // The KeySetView value itself is still assignment-compatible with the
    // Set-typed field it's stored into (PUTSTATIC only needs a subtype).
    private static final String NEW_KEY_SET_DESC = "()Ljava/util/concurrent/ConcurrentHashMap$KeySetView;";
    private static final String BRIDGE_OWNER = "net/sideways_sky/geyserrecipefix/events/FoliaAnvilBridge";
    private static final String HUMAN_ENTITY_DESC = "(Lorg/bukkit/entity/HumanEntity;)V";

    private boolean widenedField = false;
    private boolean replacedMethod = false;
    private HashSetToConcurrentKeySet hashSetVisitor;

    public PaperEventsTransformer(ClassVisitor cv) {
        super(Opcodes.ASM9, cv);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        if ("forwardSkips".equals(name)) {
            if ((access & Opcodes.ACC_PRIVATE) != 0) {
                access = access & ~Opcodes.ACC_PRIVATE; // -> package-private
                widenedField = true;
            }
        }
        return super.visitField(access, name, descriptor, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

        if ("<clinit>".equals(name)) {
            hashSetVisitor = new HashSetToConcurrentKeySet(mv);
            return hashSetVisitor;
        }
        if ("openForward".equals(name) && HUMAN_ENTITY_DESC.equals(descriptor)) {
            replacedMethod = true;
            return new MethodBodyReplacer(mv, target -> {
                target.visitVarInsn(Opcodes.ALOAD, 0); // player
                target.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE_OWNER, "openForward", HUMAN_ENTITY_DESC, false);
                target.visitInsn(Opcodes.RETURN);
            }, 1, 1);
        }
        return mv;
    }

    public boolean appliedFieldWiden() {
        return widenedField;
    }

    public boolean appliedHashSetSwap() {
        return hashSetVisitor != null && hashSetVisitor.applied;
    }

    /** True only if a method matching openForward(HumanEntity) was actually found. */
    public boolean appliedMethodReplace() {
        return replacedMethod;
    }

    /**
     * Replaces the `NEW HashSet / DUP / INVOKESPECIAL <init>()V` triplet
     * with a single `INVOKESTATIC ConcurrentHashMap.newKeySet()` call.
     * Both sequences leave exactly one Set reference on the stack, so no
     * other instruction in the initializer needs to change.
     */
    private static final class HashSetToConcurrentKeySet extends MethodVisitor {
        private boolean sawNewHashSet = false;
        private boolean sawDup = false;
        boolean applied = false;

        HashSetToConcurrentKeySet(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (opcode == Opcodes.NEW && HASHSET.equals(type)) {
                sawNewHashSet = true; // suppress: don't emit NEW
                return;
            }
            sawNewHashSet = false;
            super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitInsn(int opcode) {
            if (sawNewHashSet && opcode == Opcodes.DUP) {
                sawDup = true; // suppress: don't emit DUP
                return;
            }
            sawDup = false;
            super.visitInsn(opcode);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (sawDup && opcode == Opcodes.INVOKESPECIAL && HASHSET.equals(owner)
                    && "<init>".equals(name) && "()V".equals(descriptor)) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, CONCURRENT_HASHMAP, "newKeySet", NEW_KEY_SET_DESC, false);
                applied = true;
                sawDup = false;
                return;
            }
            sawDup = false;
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }
}
