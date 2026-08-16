package net.sideways_sky.geyserrecipefixpatcher.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Applies one edit to AnvilSim.class: renames the original
 * {@code setCost(int, Player)} to {@code setCost$original} (identical body,
 * new name only - none of AnvilSim's own logic, including its private
 * cost-indicator rendering, is touched or reproduced), then installs a new
 * {@code setCost} that calls the renamed original first and
 * {@code AnvilRenameFix.afterSetCost(this, player)} (shipped with this
 * project, see PATCHES.md) second.
 *
 * Uses ASM's tree API rather than the streaming visitor API used elsewhere
 * in this project, since "duplicate a method under a new name, then replace
 * the original" is much more naturally expressed that way.
 */
public final class AnvilSimTransformer {

    private static final String OWNER = "net/sideways_sky/geyserrecipefix/inventories/AnvilSim";
    private static final String SET_COST_DESC = "(ILorg/bukkit/entity/Player;)V";
    private static final String FIX_OWNER = "net/sideways_sky/geyserrecipefix/inventories/AnvilRenameFix";
    private static final String FIX_DESC = "(L" + OWNER + ";Lorg/bukkit/entity/Player;)V";

    private boolean applied = false;

    /** True only if setCost(int, Player) was actually found and wrapped. */
    public boolean applied() {
        return applied;
    }

    public byte[] transform(byte[] originalClassBytes) {
        ClassReader reader = new ClassReader(originalClassBytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);

        MethodNode original = null;
        for (MethodNode m : classNode.methods) {
            if ("setCost".equals(m.name) && SET_COST_DESC.equals(m.desc)) {
                original = m;
                break;
            }
        }
        if (original == null) {
            return originalClassBytes; // caller checks applied() and aborts the install
        }

        // Duplicate the original method's full body under a new name.
        MethodNode renamed = new MethodNode(original.access, "setCost$original", original.desc,
                original.signature, original.exceptions.toArray(new String[0]));
        original.accept(renamed);
        classNode.methods.add(renamed);

        // Replace the original method's body with: this.setCost$original(cost, player);
        // AnvilRenameFix.afterSetCost(this, player); return;
        InsnList insns = new InsnList();
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insns.add(new VarInsnNode(Opcodes.ILOAD, 1));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 2));
        insns.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, OWNER, "setCost$original", SET_COST_DESC, false));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 2));
        insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, FIX_OWNER, "afterSetCost", FIX_DESC, false));
        insns.add(new InsnNode(Opcodes.RETURN));

        original.instructions.clear();
        original.tryCatchBlocks.clear();
        original.localVariables = null; // would reference now-discarded labels
        original.instructions.add(insns);
        original.maxStack = 3;
        original.maxLocals = Math.max(original.maxLocals, 3);

        applied = true;

        ClassWriter writer = new ClassWriter(0); // no COMPUTE_FRAMES/COMPUTE_MAXS needed: straight-line code, no branches
        classNode.accept(writer);
        return writer.toByteArray();
    }
}
