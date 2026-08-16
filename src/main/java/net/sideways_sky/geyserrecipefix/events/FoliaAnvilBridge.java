package net.sideways_sky.geyserrecipefix.events;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.sideways_sky.geyserrecipefix.Geyser_Recipe_Fix;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Original code contributed by GeyserRecipeFixPatcher.
 *
 * This class is NOT part of the official Geyser Recipe Fix plugin. At patch
 * time, GeyserRecipeFixPatcher rewrites the body of the original, unchanged
 * {@code PaperEvents.openForward(HumanEntity)} method (same name, same
 * signature, so every existing caller keeps working unmodified) to do
 * nothing but call {@link #openForward(HumanEntity)} here, and widens
 * {@code PaperEvents.forwardSkips} from private to package-private so this
 * class - placed in the same package on purpose - can register skips in it
 * exactly like the original inline lambda used to.
 *
 * The goal is functionally identical to what the original single-threaded
 * scheduler call did, just safe to run on Folia: instead of assuming the
 * global/main-thread scheduler may touch the player, it prefers the
 * per-entity scheduler (via reflection, so this still compiles and runs
 * fine on plain Paper/Spigot where that API doesn't exist) and falls back
 * to the plain Bukkit scheduler otherwise.
 */
public final class FoliaAnvilBridge {

    private static volatile Method getSchedulerMethod;
    private static volatile Method runDelayedMethod;
    private static volatile boolean entitySchedulerUnavailable = false;

    private FoliaAnvilBridge() {
    }

    public static void openForward(HumanEntity humanPlayer) {
        if (!(humanPlayer instanceof Player player)) {
            // Original behavior only ever made sense for real players; bail
            // out quietly for any other HumanEntity implementation.
            return;
        }
        runOneTickLater(player, () -> {
            player.closeInventory();
            PaperEvents.forwardSkips.add(player);
            openRealAnvil(player);
        });
    }

    private static void runOneTickLater(Player player, Runnable task) {
        if (!entitySchedulerUnavailable) {
            try {
                if (getSchedulerMethod == null) {
                    getSchedulerMethod = player.getClass().getMethod("getScheduler");
                }
                Object scheduler = getSchedulerMethod.invoke(player);
                if (runDelayedMethod == null) {
                    runDelayedMethod = scheduler.getClass().getMethod(
                            "runDelayed",
                            org.bukkit.plugin.Plugin.class,
                            Consumer.class,
                            Runnable.class,
                            long.class
                    );
                }
                //noinspection unchecked
                Object scheduled = runDelayedMethod.invoke(
                        scheduler,
                        Geyser_Recipe_Fix.instance,
                        (Consumer<Object>) scheduledTask -> task.run(),
                        (Runnable) null,
                        1L
                );
                if (scheduled != null) {
                    return;
                }
                // Scheduling failed (e.g. entity already retired) - fall
                // through to the plain Bukkit scheduler as a best effort.
            } catch (Throwable t) {
                // No getScheduler() (plain Spigot), or the API shape
                // differs from what we expect - stop trying reflection
                // and just use the ordinary Bukkit scheduler from now on.
                entitySchedulerUnavailable = true;
            }
        }
        Bukkit.getScheduler().scheduleSyncDelayedTask((Plugin) Geyser_Recipe_Fix.instance, task, 1L);
    }

    /**
     * Opens the real, unmodified vanilla anvil menu for the player, built
     * directly via NMS rather than Paper's {@code Player#openAnvil(...)}
     * convenience method, so this works identically on Spigot, Paper and
     * Folia.
     */
    private static void openRealAnvil(Player player) {
        ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
        serverPlayer.openMenu(new SimpleMenuProvider(
                (containerId, inv, p) -> new AnvilMenu(containerId, inv, ContainerLevelAccess.NULL),
                Component.translatable("container.repair")
        ));
    }
}
