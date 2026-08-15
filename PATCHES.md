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

## New classes added to the jar (not upstream's - see `src/injected/java/`)

- `net/sideways_sky/geyserrecipefix/FoliaRuntimeSupport.class` - Folia
  detection + startup log line.
- `net/sideways_sky/geyserrecipefix/events/FoliaAnvilBridge.class` -
  Folia-safe replacement for the old "close inventory, wait a tick, open
  the real anvil" flow: prefers the per-entity scheduler (via reflection,
  so it still runs fine on plain Paper/Spigot where that API doesn't
  exist) and falls back to the plain Bukkit scheduler otherwise.

## Compile-only stubs (`src/stubs/java/`, never packaged)

Two tiny classes reproducing only two field *declarations* (name + type,
not any logic) so the helper classes above compile against the exact
name/descriptor the real classes use, and therefore link correctly against
the genuine downloaded classes at runtime. `bundleInjectedClasses` in
`build.gradle` only copies the `injected` source set's own output, so these
stubs never end up in the plugin jar or the patched output.
