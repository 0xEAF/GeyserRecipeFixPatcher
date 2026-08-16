# Exact list of patches applied

Applied by `transform/MainClassTransformer.java` and
`transform/PaperEventsTransformer.java`, directly to the compiled classes
inside the jar downloaded from Modrinth. Nothing below reproduces any of
the original method bodies - each edit is either a one-line allocation
swap, an access-flag tweak, or a redirect to a small helper class that
ships with this project (see `src/injected/java/`).

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

## New classes added to the jar (not upstream's - see `src/injected/java/`)

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

## Compile-only stubs (`src/stubs/java/`, never packaged)

Two tiny classes reproducing only two field *declarations* (name + type,
not any logic) so the helper classes above compile against the exact
name/descriptor the real classes use, and therefore link correctly against
the genuine downloaded classes at runtime. `stubs.output` is only ever
added as a `compileOnly` dependency of `main` in `build.gradle`, so it
never ends up in the built plugin jar.

## Build layout note

`FoliaRuntimeSupport` and `FoliaAnvilBridge` are ordinary classes in the
`main` source set (that's the only way to reliably get paperweight's
Mojang-mapped NMS classpath applied to them - a separate custom source set
doesn't pick it up). After compiling, `build.gradle`'s `bundleInjectedClasses`
task copies just those two `.class` files into `resources/main/injected/`,
which is what `JarPatcher` actually reads and splices into the target jar.
`shadowJar` then excludes their original path
(`net/sideways_sky/geyserrecipefix/**`) from the shipped plugin jar, so they
never exist as directly loadable classes on GeyserRecipeFixPatcher's own
runtime classpath - only the `injected/` byte copies do, and those are only
ever read, never loaded, by our own plugin.
