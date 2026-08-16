# Exact list of patches applied

Applied by `transform/MainClassTransformer.java` and
`transform/PaperEventsTransformer.java`, directly to the compiled classes
inside the jar downloaded from Modrinth. Nothing below reproduces any of
the original method bodies - each edit is either a one-line allocation
swap, an access-flag tweak, or a redirect to a small helper class that
ships with this project (see the "New classes added" list below).

## `Geyser_Recipe_Fix.class`

| # | Where | Change |
|---|-------|--------|
| 1 | static initializer | `new HashMap<>()` -> `new ConcurrentHashMap<>()` for the `openMenus` field. Safe because every other reference to it uses the declared type `java.util.Map`. |
| 2 | `onEnable()`, right after `logger = getLogger();` | Inserts one call: `FoliaRuntimeSupport.onEnable()`. That helper (shipped with this plugin, not upstream) detects Folia via a harmless `Class.forName` check and logs which scheduler mode is active. Nothing else in `onEnable()` is touched. |

## `PaperEvents.class`

| # | Where | Change |
|---|-------|--------|
| 1 | `forwardSkips` field | Access flag widened from `private` to package-private, so our injected `FoliaAnvilBridge` class (deliberately placed in the same package, `net.sideways_sky.geyserrecipefix.events`) can read/write the same field instance the rest of the class already checks. Type and semantics unchanged. |
| 2 | static initializer | `new HashSet<>()` -> `ConcurrentHashMap.newKeySet()` for `forwardSkips`. Both leave one `Set` reference on the stack, so nothing else in the initializer changes. |
| 3 | `openForward(HumanEntity)` | Entire body replaced with a single call: `FoliaAnvilBridge.openForward(player)`. Method name, descriptor, and access are untouched, so the existing (unmodified) caller elsewhere in the jar keeps resolving it exactly as before. |

## `plugin.yml`

Adds one line, `folia-supported: true`, if not already present.

## `AnvilSim.class`

| # | Where | Change |
|---|-------|--------|
| 1 | `setCost(int, Player)` | The original method is renamed to `setCost$original` (identical body - none of it is modified or reproduced elsewhere), and a new `setCost` is installed that calls the renamed original first, then `AnvilRenameFix.afterSetCost(this, player)` (shipped with this project). |

This fixes upstream issue [#19](https://github.com/AnsonEyre/Geyser-Recipe-Fix/issues/19) - "Enchanting items reset the item name." After every cost recompute, if the anvil's result item is missing a custom name that the input item already has, and the player hasn't typed anything into the rename field, `AnvilRenameFix` copies the input's custom name back onto the result and re-sends that one corrected slot, using the same `ContainerSynchronizer#sendSlotChange` technique `AnvilSim` already uses for its cost indicator.

This one reads a Minecraft-internal field (`AnvilMenu#itemName`) via reflection to check whether the player is actively typing a rename, since its exact name couldn't be verified against a live decompile in the environment this patcher was authored in. If that guess is wrong for a given Minecraft version, the fix fails safe: it logs one warning and no-ops rather than risk breaking the anvil GUI.

## New classes added to the jar (not upstream's - see `src/main/java/net/sideways_sky/geyserrecipefix/`)

- `net/sideways_sky/geyserrecipefix/FoliaRuntimeSupport.class` - Folia
  detection + startup log line.
- `net/sideways_sky/geyserrecipefix/events/FoliaAnvilBridge.class` -
  Folia-safe replacement for the old "close inventory, wait a tick, open
  the real anvil" flow: prefers the per-entity scheduler (via reflection,
  so it still runs fine on plain Paper/Spigot where that API doesn't
  exist) and falls back to the plain Bukkit scheduler otherwise.
- `net/sideways_sky/geyserrecipefix/inventories/AnvilRenameFix.class` -
  restores a dropped custom name onto the anvil result (issue #19, see
  above).

## Compile-only stand-ins (never packaged)

Three tiny classes (`Geyser_Recipe_Fix`, `PaperEvents`, `AnvilSim` under
`src/main/java/net/sideways_sky/geyserrecipefix/`) reproducing only a
handful of field *declarations* (name + type, not any logic) so the helper
classes above compile against the exact name/descriptor the real classes
use, and therefore link correctly against the genuine downloaded classes at
runtime. They're ordinary members of the `main` source set (see the build
layout note below for why), and are kept out of the shipped jar the same
way the real helper classes are.

## Build layout note

All of `FoliaRuntimeSupport`, `FoliaAnvilBridge`, `AnvilRenameFix`, and the
three compile-only stand-ins above are ordinary classes in the `main`
source set. That's the only way found to reliably get paperweight's
Mojang-mapped NMS classpath (needed for e.g. `AnvilMenu`,
`AbstractContainerMenu`) applied to them - a separate custom source set
doesn't reliably pick it up, even when the dev bundle dependency is added
directly to its own `compileOnly` configuration; this was tried twice (once
for an `injected` source set, once for a `stubs` source set) and failed the
same way both times. After compiling, `build.gradle`'s
`bundleInjectedClasses` task copies just the three real helper `.class`
files into `resources/main/injected/`, which is what `JarPatcher` actually
reads and splices into the target jar. `shadowJar` then excludes the whole
`net/sideways_sky/geyserrecipefix/**` path from the shipped plugin jar -
covering the helper classes' own path *and* the stand-ins - so none of it
ever exists as directly loadable classes on GeyserRecipeFixPatcher's own
runtime classpath; only the `injected/` byte copies do, and those are only
ever read, never loaded, by our own plugin.
