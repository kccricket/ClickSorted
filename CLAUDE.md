# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
./gradlew clean build                                    # Build JAR → build/libs/clicksorted-<version>.jar
./gradlew clean test                                     # Run all tests
./gradlew test --tests "ClassName"                       # Run a single test class
./gradlew test --tests "ClassName.methodName"            # Run a single test method
```

The Gradle `test` task is pre-configured with the `--add-opens` JVM args needed for MockBukkit + ByteBuddy on Java 16+; no extra flags needed. JUnit Platform launcher is added via `testRuntimeOnly`.

## Architecture Overview

ClickSorted is a Paper/Bukkit plugin that lets players sort inventories via configurable mouse clicks. The main plugin class (`net.kccricket.clicksorted.ClickSortedPlugin`) is the `JavaPlugin` entry point.

### Package Layout

```
net.kccricket.clicksorted
├── ClickSortedPlugin          entry point
├── model/                   ClickMethod, SortingMethod, SortKey, PlayerSortingPrefs,
│                            StartCorner, FillAxis, EnumParse
├── commands/                ClickSortedCommands (Brigadier command tree)
├── config/                  ConfigManager, ManagedConfig, MainConfig, LangConfig,
│                            GroupsConfig, ItemsConfig, ResourceUpdater
├── events/                  InventorySortEvent
├── gui/                     ClickSortedHolder, LockGuiHolder, LockGuiListener
├── sort/                    InventoryClickListener, InventorySortService, SortEngine,
│                            BundlePacker, BundleBenchmark, GridGeometry, SlotOrder,
│                            TreemapPacker, PrefsCycleHandler, ProtectedItems, ProtectedSlots
├── migration/               ValueMigration, Migration, Store, Migrations,
│                            PlayerMigrationListener, PreferenceRepair
├── text/                    MessageUtil, CooldownMessenger, ItemNames
├── logging/                 Log, DebugLevel
├── security/                Permissions, ActionThrottle
└── update/                  UpdateChecker
```

### Settings migration

`Migrations` is an instance component (constructed in `ClickSortedPlugin.onEnable` before
`configManager.loadAll()`, retrieved via `plugin.getMigrations()`) that **owns the catalog of what is
stored where**. Loaders/stores don't name specific settings — they just hand the migrator the store:
config is migrated in `MainConfig.load()` (`plugin.getMigrations().migrate(plugin.getConfig())`, after
`normalizeValues()` and before `saveConfig()`), and per-player PDC is migrated by
`PlayerMigrationListener` on `PlayerJoinEvent` (`plugin.getMigrations().migrate(player)`). `Migrations`
exposes exactly one entry point per store: `migrate(ConfigurationSection)` and `migrate(Player)`.

**Rule hierarchy** (three layers, applied in order on every `migrate(config)` call):

1. **Structural transforms** (`ConfigTransform` — config only). Derive new config state from old values in place (e.g. translate a deprecated numeric range into an equivalent slot list). Run *first*, before removal, so old keys are still readable. Adding a future transform is a one-line append to `CONFIG_TRANSFORMS`. Source-key removal is not the transform's job.
2. **Root-path removal** (`DEPRECATED_ROOT_PATHS` — config only). Drop root-level config keys that have been removed (e.g. `player_sort_min`, `player_sort_max`). These are not in `defaults.*` so they fall outside the shared `Store` namespace.
3. **Shared rules** (`SHARED` — applied to both config and PDC via `Store` adapters). A single ordered `List<Migration>` covers all `defaults.*` / PDC settings with no per-key mapping table.

**`Store` and `Migration`** are the shared rule mechanism. `Store` is a store-neutral interface (`getString`, `setString`, `setBoolean`, `clear`, `contains`); each adapter applies its own namespace:
- `Store.ConfigStore` wraps a `ConfigurationSection`; leaf `k` → path `defaults.k`. No per-key map.
- `Store.PdcStore` wraps a `PersistentDataContainer` + plugin; leaf `k` → `NamespacedKey(plugin, k)`. Strings via `STRING`, booleans via `BYTE`. No per-key map.

PDC leaf names now match config-default leaf names (`click_mode`, `sort_mode`, `start_corner`, `fill_axis`, `sort_over_items`, `enabled`, `bundle_*`), which is what allows the namespace-only adapter with no mapping table.

**`Migration`** is a composable rule (`boolean apply(Store)`). Factory methods cover four cases:
- `renameKey(oldLeaf, newLeaf)` — move a value from one leaf to another (clears old).
- `remap(leaf, ValueMigration lineage)` — rewrite a value via a lineage.
- `remove(leaf)` — drop a deprecated leaf.
- `when(leaf).is(value).then(effects…)` — conditional branch; effect factories: `set(leaf, String)`, `set(leaf, boolean)`, `clear(leaf)`.

`ValueMigration` (unchanged) models old→new value renames; `ValueMigration.builder().rename(old).to(next).to(newer)…build()` collapses every historical alias directly to the canonical last token.

The **`SHARED` list** (order encodes data dependency — renames first, then remaps, then conditionals, then removals):
```
renameKey("click", "click_mode")    // PDC rename; no-op on config (already click_mode)
renameKey("sort",  "sort_mode")     // PDC rename; no-op on config
remap("click_mode",  CLICK_METHOD)  // e.g. DOUBLE → DOUBLE_CLICK
remap("start_corner", START_CORNER)
remap("fill_axis",    FILL_AXIS)
when("click_mode").is("NONE").then(set("enabled", false), set("click_mode", "SWAP"))
remove("shift_click")               // drops defaults.shift_click (config) and shift_click PDC
```

Config-only structural work (slot-bounds migration, root-path removal) stays outside `SHARED` as the dedicated escape hatch for store-specific migrations.

### Core Flow

1. `InventoryClickEvent` fires when a player clicks inside an inventory.
2. `InventoryClickListener` checks the player's `PlayerSortingPrefs` (stored in Bukkit's Persistent Data Container): first the `enabled` flag (sorting disabled → early exit), then whether the click matches their configured `ClickMethod`, then the "sort over items" gate, then the shared `ActionThrottle` rate-limiter.
3. If it matches, `InventorySortService` delegates to `SortEngine`: fungible items are collapsed into a `HashMap<SortKey, Integer>` (material → quantity) and non-fungible items (bundles, non-stackables) are kept discrete, then everything is reconstructed into stacks and written back to the inventory.
4. When bundle packing is enabled for the target (`defaults.bundle_inventory` / `defaults.bundle_others`, toggled per-player), `InventorySortService` first runs `BundlePacker` to repack partial stacks into bundles before the sort.
5. A custom `InventorySortEvent` fires after sorting so third-party plugins can intervene.
6. For player inventories, any slots the player has locked (via `/clicksorted set lock`) are excluded from the sortable set before `SortEngine` runs — locked slots are neither read nor overwritten.
7. For player inventories, admin-enforced slot locks are also excluded from the sortable set, immediately after per-player locks. A slot is excluded if it appears in `config.yml`'s `locked_slots.player` list or if the player has the `clicksorted.lock.player.slot.<n>` permission node explicitly set (see `ProtectedSlots`). Admin-locked slots cannot be toggled by the player in the lock GUI — they render as a distinct IRON_BARS pane.
8. Item-blacklisted slots are also excluded from the sortable set (for all inventory types, not just player), immediately after slot locks. A slot is excluded if its item matches the admin `ProtectedItems` list — checked against `config.yml`'s `blacklist.materials`/`blacklist.names` and the sorting player's explicit `clicksorted.blacklist.*` permission nodes.
9. On startup (and after `/clicksorted reload`), `UpdateChecker` runs an async best-effort Modrinth API call and logs a console notice if a newer release exists (`check_for_updates: true`).
10. On `PlayerJoinEvent`, `PreferenceRepair` validates the player's PDC preferences and resets any that hold unrecognised values, notifying the player in chat.

### Key Classes

| Class | Package | Role |
|---|---|---|
| `ClickSortedPlugin` | root | `JavaPlugin` entry point, wires all components |
| `PlayerSortingPrefs` | model | Per-player state (enabled flag, ClickMethod, SortingMethod, sort-over-items flag, bundle-packing flags, bundle stack limit, bundle material blacklist, bundle display-name blacklist, locked slots) stored via PDC. PDC leaf names match config-default names (`click_mode`, `sort_mode`, `enabled`, etc.) |
| `LockGuiHolder` | gui | 45-slot chest inventory for the lock GUI; builds lime/barrier/iron-bars panes and maps chest↔inventory slots; admin-locked slots (config or permission) render as IRON_BARS and are non-toggleable |
| `LockGuiListener` | gui | Handles clicks/drags in the lock GUI; guards admin-locked slots via `ProtectedSlots.forSort`, toggles per-player lock state, and cancels all real-inventory interaction |
| `SortKey` | model | `Comparable` wrapper around an ItemStack that drives all sort ordering |
| `SortingMethod` | model | Enum (NAME, GROUP, TREEMAP) controlling `SortKey.makeSortPrefix()`; `isTreemap()` routes placement through `TreemapPacker` instead of `SlotOrder` |
| `ClickMethod` | model | Enum (SINGLE_CLICK, DOUBLE_CLICK, SWAP, CONTROL_DROP, SHIFT_LEFT_CLICK, SHIFT_RIGHT_CLICK). `NONE` was removed — use the `enabled` preference instead |
| `Migration` | migration | Composable rule interface (`boolean apply(Store)`). Factory methods: `renameKey`, `remap`, `remove`, `when(…).is(…).then(effects…)`. Effect factories: `set(leaf, String/boolean)`, `clear(leaf)` |
| `Store` | migration | Store-neutral key/value handle; `ConfigStore` (namespace `defaults.*`) and `PdcStore` (namespace `NamespacedKey(plugin, leaf)`) adapters |
| `StartCorner` | model | Enum (TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT) — which corner the sort grid begins from |
| `FillAxis` | model | Enum (HORIZONTAL, VERTICAL) — whether rows or columns fill first from the start corner |
| `EnumParse` | model | Case-insensitive enum parse helper used by StartCorner, FillAxis, and others |
| `InventoryClickListener` | sort | Dispatches click events: trigger match, sort-over-items gate, throttle, then hand off to the sort service |
| `InventorySortService` | sort | Target resolution, permissions, event lifecycle, optional bundle packing, write-back |
| `SortEngine` | sort | Pure sort/merge algorithm (no plugin state) — fungible merge + discrete passthrough |
| `GridGeometry` | sort | Maps an inventory's slot indices to a 2-D grid; computes row/column counts and the mount-slot offset |
| `SlotOrder` | sort | Produces a write-back slot sequence from a `GridGeometry` given a `StartCorner` and `FillAxis` |
| `TreemapPacker` | sort | Implements the `TREEMAP` sort method: assigns each item type a contiguous near-square block sized to its stack count; respects `StartCorner` and `FillAxis`; falls back to gap-free fill when rectangles no longer fit |
| `PrefsCycleHandler` | sort | Handles a click-method-driven preference cycle (used internally by InventoryClickListener) |
| `BundlePacker` | sort | Pure pool-and-repack of bundle-eligible items into bundles (no plugin state) |
| `ProtectedItems` | sort | Immutable admin "do not touch" snapshot: items matching the server-wide config blacklist or the sorting player's explicit `clicksorted.blacklist.*` permission nodes are excluded from the sortable slot set entirely (never sorted, moved, or packed). Config names are matched case-insensitively; permission name nodes use a slug (`ProtectedItems.nameToken`) — lowercase, collapse non-`[a-z0-9]` runs to `_`, strip edges. Uses `isPermissionSet` before `hasPermission` to avoid OP-default false positives for undeclared dynamic nodes. |
| `ProtectedSlots` | sort | Immutable admin slot-lock snapshot: player inventory slots in the `locked_slots.player` config list or covered by an explicit `clicksorted.lock.player.slot.<n>` permission node are excluded from the sortable slot set (player inventories only). Same `isPermissionSet`-before-`hasPermission` OP guard as `ProtectedItems`. Namespace is hierarchical (`lock.player.*`) to reserve room for future `lock.container.*` categories. |
| `BundleBenchmark` | sort | In-situ micro-benchmark of the sort and bundle-repack paths (`/clicksorted benchmark`) |
| `ClickSortedHolder` | gui | Base `InventoryHolder` marker for all ClickSorted-owned GUIs (used to block self-sort) |
| `PreferenceRepair` | migration | Validates and resets invalid per-player PDC preferences on login, notifying the player |
| `UpdateChecker` | update | Best-effort async Modrinth API check; logs a console notice when a newer release exists |
| `ConfigManager` | config | Unified lifecycle for all four config files |
| `ResourceUpdater` | config | Add-only merge of bundled resource into plugin data folder |
| `Log`, `DebugLevel` | logging | Plugin logger wrapper with gated debug levels |
| `MessageUtil` | text | Coloured Adventure `Component` message helpers |
| `CooldownMessenger` | text | Rate-limits repeated messages to players |
| `Permissions` | security | Permission-check helper with debug logging |
| `ActionThrottle` | security | Global per-player rate limiter (`action_cooldown_ms`) gating every plugin-driven action; `throttled()` also sends the rate-limited notice |

### Configuration Files (src/main/resources)

- `config.yml` — debug level, sortable inventory types, `action_cooldown_ms` throttle, `check_for_updates` flag, per-player `defaults` (including `enabled`, click/sort mode, `start_corner`, `fill_axis`, sort-over-items, bundle packing), the admin `blacklist` section (`blacklist.materials` / `blacklist.names` — items matching these are never sorted, moved, or packed by anyone), and the admin `locked_slots` section (`locked_slots.player` — list of player inventory slot indices (0–35) that are always excluded from sorting; also enforced via `clicksorted.lock.player.slot.<n>` permission nodes)
- `groups.yml` — item groupings for GROUP sort method
- `items.yml` — persistent store of material → display-name mappings
- `lang.yml` — all user-facing messages (MiniMessage format)

### Testing

Tests live in `src/test/java/net/kccricket/clicksorted/` and are **integration-level**: they bootstrap the full plugin via `MockBukkit.loadWithConfig()` and dispatch real Bukkit events. `AbstractClickSortedTest` is the shared base class — it wires up the server, loads the plugin with `src/test/resources/test-config.yml` (bStats disabled), and provides helper methods for simulating inventory interactions.

There is a known non-obvious setup required for MockBukkit v4 on Java 16+; see `memory/mockbukkit-v4-test-setup.md` for the full list of fixes (ByteBuddy opens, JARUtil null-guard, SQLite JDBC classloader, DurationUtil format, JaCoCo scoping).

### Command Framework

Commands are implemented as a Brigadier tree in `ClickSortedCommands` and registered via `LifecycleEvents.COMMANDS`. Each subcommand is a static builder method. The tree is:

- **`/clicksorted` (bare)** — toggles the player's `enabled` flag (on→off or off→on) and reports the new state. Requires `clicksorted.commands.enabled` (default: true).
- **`set`** — per-player preferences: `set enabled [on|off]` (toggles when no arg — same as the bare command), `set sort-method <NAME|GROUP|TREEMAP>`, `set click-method <…>`, `set start-corner <TOP_LEFT|TOP_RIGHT|BOTTOM_LEFT|BOTTOM_RIGHT>`, `set fill-axis <HORIZONTAL|VERTICAL>`, `set hover [on|off]` (toggles when no arg; some click methods govern this automatically), `set lock` (opens the lock GUI), and `set bundle` (no-arg prints status; `set bundle inventory|others <on|off>`; `set bundle stacklimit <n|off>`; `set bundle blacklist` / `set bundle blacklist gui` opens a 54-slot GUI — click an item in the player's real inventory to add it to the blacklist by material (vanilla items) or by display name (custom-named items); click a listed entry to remove it; arrows navigate pagination; `set bundle blacklist add|remove <material>`, `set bundle blacklist list`, `set bundle blacklist clear`, `set bundle blacklist name add|remove <text>` are text-command alternatives — materials in the blacklist are never packed into or unpacked from bundles; normal stack-merging still applies; items whose plain-text display name matches a name entry are also kept out of bundles).
- **`status`** — print the player's current enabled state, click method, sort method, start corner, fill axis, and sort-over-items state.
- **`reload`**, **`getcfg`**, **`debug [level]`**, **`benchmark [iterations]`** — admin/diagnostic commands.

`set hover` toggles the per-player "sort over items" flag (server default `defaults.sort_over_items`), which controls whether a sort fires only on an empty slot or also while hovering an occupied one.

Player-facing command handlers resolve the executor via the shared `requirePlayer(plugin, ctx)` helper and gate on `ActionThrottle.throttled(player)`; both return early on failure. Boolean on/off arguments are parsed via `parseState` (the sole consumer of the `ON_WORDS`/`OFF_WORDS` vocabularies).

Note: the `AbstractCommand` / `CommandManager` pattern referenced in older docs no longer applies — the codebase uses Paper's native Brigadier API.

### Admin "do not touch" blacklist (item-based)

Admins can prevent ClickSorted from ever touching specific items — they are never sorted, moved, or packed/unpacked regardless of player preferences. Two enforcement channels (unioned):

1. **Config** (`config.yml → blacklist.materials` / `blacklist.names`) — server-wide. Materials matched exactly; names matched case-insensitively against the item's plain-text display name. Unknown material names are warned and skipped on load/reload.
2. **Permission** — `clicksorted.blacklist.material.<material>` (e.g. `clicksorted.blacklist.material.nether_star`) and `clicksorted.blacklist.name.<slug>` (e.g. `clicksorted.blacklist.name.creative_menu`). Nodes are dynamic, constructed from the item at sort time and checked with `hasPermission` — no enumeration needed, so group inheritance works natively via permissions plugins (LuckPerms, etc.).

**Name slug rule** (`ProtectedItems.nameToken`): strip legacy `§X` color codes → lowercase (`Locale.ROOT`) → collapse non-`[a-z0-9]` runs to `_` → strip edge underscores. E.g. `"Creative Menu"` → `creative_menu`.

**OP note**: The permission channel uses `isPermissionSet` before `hasPermission` to avoid Bukkit's OP default (undeclared nodes return `true` for OPs via `PermissionDefault.OP`). Only explicitly-attached permission nodes trigger the blacklist — OPs without explicit node grants are unaffected.

This blacklist is **admin-only** and not exposed to players via any command or GUI. For player-controlled bundle exclusions, see the per-player bundle blacklist (`PlayerSortingPrefs`, `BundleBlacklist`).

### Admin slot locks (slot-based)

Admins can lock specific **player inventory slots** server-wide so they are never sorted, moved, or packed/unpacked, and cannot be toggled by the player in the lock GUI. Two enforcement channels (unioned), scoped to player inventories only (slots 0–35):

1. **Config** (`config.yml → locked_slots.player`) — server-wide list of slot indices (0–8 hotbar, 9–35 main storage). Out-of-range values are warned and skipped on load/reload.
2. **Permission** — `clicksorted.lock.player.slot.<n>` (e.g. `clicksorted.lock.player.slot.9`). Nodes are dynamic and undeclared in `paper-plugin.yml` (intentional — keeps the `isPermissionSet` OP guard effective). Grant via a permissions plugin to lock specific slots per player or group.

**OP note**: Same `isPermissionSet`-before-`hasPermission` guard as the item blacklist — OPs without an explicit node grant are unaffected.

**Namespace**: `clicksorted.lock.player.*` and `locked_slots.player` are deliberately hierarchical, reserving room for future lock categories (e.g. `clicksorted.lock.container.*` for container-title-based locks).

**Lock GUI**: admin-locked slots render as a non-toggleable IRON_BARS pane with `lockPaneAdmin`/`lockPaneAdminLore` lang keys. The permission channel is checked per-player so the GUI reflects both config and permission locks for the viewing player.

**Migration**: servers that had custom `player_sort_min`/`player_sort_max` values are automatically migrated on first load — the formerly-excluded slot indices are added to `locked_slots.player` and both old keys are removed. A `ConfigTransform` (`Migrations.migrateSortBounds`) handles this as part of the three-pass config migration framework.

### Version Compatibility

`ClickMethod` and `SortingMethod` are plain enums. `SortingMethod.isAvailable()` checks whether `groups.yml` has any mappings loaded (GROUP requires a populated groups file).

**Never invoke `InventoryView` methods from plugin bytecode** (e.g. `event.getView().getTopInventory()`). `InventoryView` is a concrete class on ≤1.20.6 but an interface on 1.21+; compiling against the newer API and running on an older server makes the JVM throw `IncompatibleClassChangeError` ("Found class … but interface was expected") at the call site. Use `InventoryEvent.getInventory()` (resolved inside the API jar, returns the stable `Inventory` interface) instead. Passing a `getView()` result as a plain argument is fine — only *calling methods on* the view from our bytecode breaks.

## Release Notes Template

Use this format when drafting GitHub release notes. Omit sections that have no entries.

```markdown
ClickSorted <version> <one-sentence summary of the release theme>.

## Highlights

- <bullet> — <one-line description>

## Breaking Changes

<!-- Optional — omit this section entirely if the release has no breaking changes.
     Lead with what broke and the exact migration step. Cover: renamed/removed commands,
     permission nodes, config keys, or stored-value formats; changed defaults; removed features. -->

- <what changed> — <what users must do; note if migration is automatic>

## New Features

### <Feature Area>

<Prose description. Include PR reference [PR #N] if applicable.>

## Bug Fixes

- <description> — <brief cause/fix>. [PR #N]

## Other Improvements

- <description>. [PR #N]

## Compatibility

✔️ Paper <version>
✔️ Java <version>

## Upgrading

1. Stop your server.
2. Replace the old jar in `plugins/` with this release.
3. <Any migration steps — delete if none.>
4. Start your server.

## What's Changed

- <PR title> by @<author> in #<N>
```
