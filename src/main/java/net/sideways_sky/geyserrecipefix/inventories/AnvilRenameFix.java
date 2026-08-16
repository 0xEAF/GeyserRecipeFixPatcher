package net.sideways_sky.geyserrecipefix.inventories;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Original code contributed by GeyserRecipeFixPatcher.
 *
 * Fixes upstream issue https://github.com/AnsonEyre/Geyser-Recipe-Fix/issues/19 -
 * "Enchanting items reset the item name": combining a custom-named item with
 * an enchanted book in GeyserRecipeFix's simulated Bedrock anvil GUI drops
 * the item's existing custom name, even though the player never touched the
 * rename field.
 *
 * This is NOT part of the official Geyser Recipe Fix plugin. At patch time,
 * GeyserRecipeFixPatcher renames the original {@code AnvilSim.setCost(int,
 * Player)} method to {@code setCost$original} (identical body, just a new
 * name) and installs a new {@code setCost} in its place that calls the
 * renamed original first, then calls {@link #afterSetCost} here - so all of
 * AnvilSim's own logic (including its private cost-indicator rendering)
 * still runs completely unmodified, we only ever add a step after it.
 *
 * How the fix works: after every cost recompute, if the anvil's real,
 * server-side result item is missing a custom name that the input item (the
 * item being repaired/enchanted) already has, and the player hasn't typed
 * anything into the rename field for this operation, we copy the input's
 * custom name back onto the result and re-send just that one corrected
 * slot - the same technique AnvilSim itself already uses for its cost
 * indicator (ContainerSynchronizer#sendSlotChange).
 */
public final class AnvilRenameFix {

    // AnvilSim.AnvilSlot.FIRST.backIdx / .RESULT.backIdx - hardcoded here
    // rather than referencing the enum, since they're a stable, tiny detail
    // (0 = the primary input slot, 2 = the anvil's result slot) and this
    // avoids needing a compile-time stub for a nested enum.
    private static final int FIRST_SLOT = 0;
    private static final int RESULT_SLOT = 2;

    private static volatile Field itemNameField;
    private static volatile boolean warnedOnce = false;

    private AnvilRenameFix() {
    }

    public static void afterSetCost(AnvilSim sim, Player player) {
        try {
            if (!(sim.menu instanceof AnvilMenu advMenu)) {
                return;
            }

            String typedName = readTypedRenameText(advMenu);
            if (typedName != null && !typedName.isBlank()) {
                // The player is deliberately renaming this operation -
                // don't override whatever name they're typing.
                return;
            }

            ItemStack input = advMenu.getSlot(FIRST_SLOT).getItem();
            if (!input.has(DataComponents.CUSTOM_NAME)) {
                return; // nothing to restore
            }
            Component expectedName = input.get(DataComponents.CUSTOM_NAME);

            ItemStack result = advMenu.getSlot(RESULT_SLOT).getItem();
            if (result.isEmpty()) {
                return;
            }
            Component currentName = result.get(DataComponents.CUSTOM_NAME);
            if (expectedName.equals(currentName)) {
                return; // already correct
            }

            result.set(DataComponents.CUSTOM_NAME, expectedName);

            ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
            serverPlayer.containerSynchronizer.sendSlotChange(sim.menu, RESULT_SLOT, result);
        } catch (Throwable t) {
            // Fail safe: if anything about this NMS version's shape doesn't
            // match what we expect, silently skip the fix rather than risk
            // breaking the anvil GUI entirely. Log once so it's noticeable
            // in the console without spamming on every anvil interaction.
            if (!warnedOnce) {
                warnedOnce = true;
                Logger.getLogger("GeyserRecipeFixPatcher").log(Level.WARNING,
                        "[GeyserRecipeFixPatcher] Could not apply the anvil custom-name fix "
                        + "(upstream issue #19) - this Minecraft/Paper version's internals may "
                        + "have changed. The rest of the patch is unaffected.", t);
            }
        }
    }

    /**
     * Reads whatever the player has currently typed into the anvil's rename
     * field, via reflection so a wrong guess at the field name fails safe
     * (see the catch-all above) instead of breaking compilation or the
     * whole plugin. Expected to be an empty string (not null) when nothing
     * has been typed.
     */
    private static String readTypedRenameText(AnvilMenu advMenu) throws ReflectiveOperationException {
        if (itemNameField == null) {
            Field f = AnvilMenu.class.getDeclaredField("itemName");
            f.setAccessible(true);
            itemNameField = f;
        }
        Object value = itemNameField.get(advMenu);
        return value == null ? null : value.toString();
    }
}
