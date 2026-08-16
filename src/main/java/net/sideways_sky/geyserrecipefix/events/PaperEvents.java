package net.sideways_sky.geyserrecipefix.events;

import org.bukkit.entity.HumanEntity;

import java.util.Set;

/**
 * Compile-only stub - see Geyser_Recipe_Fix.java in this same source set
 * for why this exists. Declares only the one field
 * (`forwardSkips: Set<HumanEntity>`) our injected FoliaAnvilBridge needs to
 * reference by name, so it links correctly against the real, official
 * PaperEvents.class (whose `forwardSkips` field is widened from private to
 * package-private by JarPatcher at patch time) at runtime. Never packaged.
 */
class PaperEvents {
    static Set<HumanEntity> forwardSkips;
}
