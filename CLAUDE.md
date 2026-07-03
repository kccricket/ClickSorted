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
│                            StartCorner, FillAxis, EnumParse, PreferenceResult
├── commands/                ClickSortedCommands (Brigadier command tree)
├── config/                  ConfigManager, ManagedConfig, MainConfig, LangConfig,
│                            GroupsConfig, ItemsConfig, ResourceUpdater
├── events/                  InventorySortEvent, PlayerPreferenceChangeEvent, Preference
├── gui/                     ClickSortedHolder, BlacklistGuiHolder, BlacklistGuiListener,
│                            LockGuiHolder, LockGuiListener
├── sort/                    InventoryClickListener, InventorySortService, SortEngine,
│                            BundlePacker, BundleBlacklist, InPlacePacker, BundleBenchmark,
│                            GridGeometry, SlotOrder, MaterialNameSet,
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
2. `InventoryClickListener` first checks the master kill-switch `clicksorted` permission (default on). Then it checks whether the click matches their configured `ClickMethod` and whether the target is sortable. If it matches, it calls `InventorySortService.hasWork()` to pre-screen: work exists when **sorting or bundle packing** is enabled for the player and target region (the per-player `enabled` flag gates sorting only, not packing). If `hasWork` is false, the event is a complete no-op. Otherwise the "sort over items" gate and `ActionThrottle` rate-limiter apply.
3. **Sorting on:** `InventorySortService` delegates to `SortEngine`: fungible items are collapsed into a `HashMap<SortKey, Integer>` (material → quantity) and non-fungible items (bundles, non-stackables) are kept discrete, then everything is reconstructed into stacks and written back across the inventory (items may move to any sortable slot).
4. **Sorting off, packing on:** `InventorySortService` delegates to `InPlacePacker`: same-material stacks consolidate within their *own* slots (full stacks first, remainder last, trailing empties cleared). Loose stacks stay anchored to their lane; items displaced from bundles (or exceeding their lane's slot capacity) fill free/freed slots in ascending order; drops occur only when the region is genuinely full.
5. When bundle packing is enabled for the target (`defaults.bundle_in_inventory` / `defaults.bundle_in_containers`, toggled per-player), `BundlePacker` runs either as part of `packAndSort` (sorting on) or inside `InPlacePacker` (sorting off). In both cases eligible remainders are repacked into the bundles already present in the sortable region.
6. Before firing, `InventorySortService` resolves the click's region slots and, for player inventories, its per-player locked slots (`/clicksorted lock-slots`) and admin-enforced slot locks (`ProtectedSlots` — `config.yml`'s `locked_slots.player` list or an explicit `clicksorted.lock.player.slot.<n>` permission node; non-player inventories always resolve to no locks). It then fires a custom `InventorySortEvent` — after the trigger matches and before any writes, so third-party plugins can intervene — carrying that region and both lock sets, plus the admin item-blacklist snapshot (`ProtectedItems`) as metadata. `getSortableSlots()` is pre-narrowed to region minus both lock sets, so listeners see the lock-adjusted set from the start; `getSlots()`/`statusOf(int)` classify every slot in the clicked inventory as `OUT_OF_RANGE`/`ADMIN_LOCKED`/`USER_LOCKED`/`SORTABLE` (that precedence order) — admin-locked slots cannot be toggled by the player in the lock GUI and render as a distinct IRON_BARS pane. Listeners can further narrow the sort via `excludeSlot(int)`, or contribute their own item exclusions via `excludeItem(Material)`/`excludeItem(String)`. The event applies to both the sort-with-layout and in-place consolidation paths. The old `(view, inv, int min, int max)` constructor is deprecated (materializes a contiguous region with empty locks and no blacklist metadata) in favor of the set-based constructor. A cancelling listener may call `setCancelReason(Component)`; since this event fires on every matching click, `InventorySortService` shows a set reason to the player rate-limited via `CooldownMessenger` and stays silent when no reason was given (no generic fallback, unlike preference-change cancellation below).
7. After the event fires (and if not cancelled), `InventorySortService` excludes any slot in `getSortableSlots()` whose item matches `sortEvent.matchesExcludedItem(is)` — the union of the admin `ProtectedItems` blacklist and any listener `excludeItem` additions — for all inventory types, not just player. This runs after listeners so it can see any content edits they made.
8. On startup (and after `/clicksorted admin reload`), `UpdateChecker.restart()` fires an async best-effort Modrinth API call (when `check_for_updates: true`) that logs a console notice if a newer release exists, then rearms the recurring schedule (`check_for_updates_interval_hours`, default 24, clamped to a minimum of `MainConfig.MIN_UPDATE_CHECK_INTERVAL_HOURS`); the recurring task is cancelled via `stop()` on disable.
9. Every per-player preference change (click/sort method, start corner, fill axis, `enabled`, sort-over-items, bundle-packing toggles/stack limit, a locked-slot toggle, or a bundle blacklist add/remove/clear) fires a cancellable `PlayerPreferenceChangeEvent` from `PlayerSortingPrefs` before the change is applied; listeners can inspect `getChange()` (or the typed `getChange(Preference)` accessor) for the before/after values and cancel to block the change from persisting. Every event-firing `PlayerSortingPrefs` mutator returns a `PreferenceResult` (`APPLIED`/`UNCHANGED`/`CANCELLED`) instead of a bare `boolean`/`void`, so command and GUI callers can tell a real no-op apart from a listener veto and report each correctly (the bulk `setLockedSlots` used by `toggleSlotLocked` and tests is the deliberate exception — it writes directly, no event): on `CANCELLED`, `MessageUtil.preferenceBlocked(...)` shows the listener's `getCancelReason()` if one was set (via `setCancelReason(Component)`), else the generic `preferenceChangeBlocked` lang key — this generic fallback is safe here because preference changes are explicit, low-frequency commands, unlike per-click `InventorySortEvent` cancellation above. A no-op setter call that matches the config default still pins the value in PDC (without firing the event), so an explicit choice survives a later default change.
10. On `PlayerJoinEvent`, `PreferenceRepair` validates the player's PDC preferences and resets any that hold unrecognised values, notifying the player in chat.

### Key Classes

| Class | Package | Role |
|---|---|---|
| `ClickSortedPlugin` | root | `JavaPlugin` entry point, wires all components |
| `PlayerSortingPrefs` | model | Per-player state (enabled flag, ClickMethod, SortingMethod, sort-over-items flag, bundle-packing flags, bundle stack limit, bundle material blacklist, bundle display-name blacklist, locked slots) stored via PDC. PDC leaf names match config-default names (`click_mode`, `sort_mode`, `enabled`, etc.). Every mutator except the bulk `setLockedSlots` fires a cancellable `PlayerPreferenceChangeEvent` before applying the change (skipped when old == new, though a no-op that matches the config default still pins the value in PDC) and returns a `PreferenceResult`. The enum/boolean setters delegate to two private helpers, `setEnumPref`/`setBoolPref`, and the set-valued blacklist mutators to `mutateBlacklist`, which own the fire→write→return sequence once rather than per setter; set-valued stores re-read PDC after the event fires so re-entrant listener mutations aren't clobbered. |
| `PreferenceResult` | model | Outcome of a `PlayerSortingPrefs` mutator call: `APPLIED`, `UNCHANGED` (no-op), or `CANCELLED` (with an optional listener-supplied `cancelReason()`) — lets callers distinguish a real no-op from a listener veto, which a bare `boolean`/`void` return could not |
| `PlayerPreferenceChangeEvent` | events | Cancellable event fired by `PlayerSortingPrefs` before any per-player preference is changed; carries a sealed `Change<T>` payload (`ValueChange<T>` or `LockedSlotChange`, which adds a `slot()`) with typed `oldValue()`/`newValue()`; `getChange(Preference<T>)` gives a type-narrowed accessor for one preference. A cancelling listener may call `setCancelReason(Component)` to explain the veto; callers report it via `MessageUtil.preferenceBlocked(...)`, falling back to the generic `preferenceChangeBlocked` lang key when no reason was set. |
| `Preference<T>` | events | Typed key identifying a per-player preference (e.g. `Preference.CLICK_MODE : Preference<ClickMethod>`); constants are the sole instances, so identity comparison proves the payload's type parameter |
| `BlacklistGuiHolder` | gui | 54-slot chest GUI for the per-player bundle blacklist; lists blacklisted materials and display-name entries as item stacks with click-to-remove lore; pagination via arrow items |
| `BlacklistGuiListener` | gui | Handles clicks in the blacklist GUI; adds items from the real inventory to the blacklist by material or display name, removes listed entries, handles pagination, and cancels all real-inventory interaction |
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
| `InventoryClickListener` | sort | Dispatches click events: master-perm check, trigger match, `hasWork` pre-screen, sort-over-items gate, throttle, then hand off to the sort service |
| `InventorySortService` | sort | Target resolution (via private `Region`/`Target`), permissions, event lifecycle, mode dispatch (sort-with-layout vs. in-place consolidation), write-back. `hasWork()` is the public pre-screen gate for the listener. On a cancelled `InventorySortEvent`, shows a listener-supplied `getCancelReason()` rate-limited via `CooldownMessenger` (no message when no reason was set). On Folia, `resolve()` refuses a non-vanilla-held, multi-viewer (shared virtual) inventory as a target — see [Folia safety](#folia-safety). |
| `SortEngine` | sort | Pure sort/merge algorithm (no plugin state) — fungible merge + discrete passthrough |
| `InPlacePacker` | sort | Pure in-place consolidator (no plugin state): collapses same-material stacks within their own slots and optionally packs eligible remainders into existing bundles — used when sorting is off but packing is on. Loose stacks stay anchored; items displaced from bundles or exceeding lane capacity fill free/freed slots; drops only when region is full. |
| `GridGeometry` | sort | Maps an inventory's slot indices to a 2-D grid; computes row/column counts and the mount-slot offset |
| `SlotOrder` | sort | Produces a write-back slot sequence from a `GridGeometry` given a `StartCorner` and `FillAxis` |
| `TreemapPacker` | sort | Implements the `TREEMAP` sort method: assigns each item type a contiguous near-square block sized to its stack count; respects `StartCorner` and `FillAxis`; falls back to gap-free fill when rectangles no longer fit |
| `PrefsCycleHandler` | sort | Handles a click-method-driven preference cycle (used internally by InventoryClickListener) |
| `BundlePacker` | sort | Pure pool-and-repack of bundle-eligible items into bundles (no plugin state) |
| `BundleBlacklist` | sort | Immutable per-player snapshot of blacklisted materials and display names; delegates matching to `MaterialNameSet`. Blocks packing and unpacking for matching items and blocks blacklisted bundle colors as packing bins. |
| `MaterialNameSet` | sort | Immutable record holding a `Set<Material>` and a pre-lowercased `Set<String>` of display names; shared by `BundleBlacklist` and `ProtectedItems` for material-or-name membership tests |
| `MigrationException` | migration | Unchecked exception thrown by `Migrations.migrate(ConfigurationSection)` on unrecoverable migration failure; caught by the reload handler to report a graceful failure via `configReloadFailed` lang key |
| `ProtectedItems` | sort | Immutable admin "do not touch" snapshot: items matching the server-wide config blacklist or the sorting player's explicit `clicksorted.blacklist.*` permission nodes are excluded from the sortable slot set entirely (never sorted, moved, or packed). Config names are matched case-insensitively; permission name nodes use a slug (`ProtectedItems.nameToken`) — lowercase, collapse non-`[a-z0-9]` runs to `_`, strip edges. Uses `isPermissionSet` before `hasPermission` to avoid OP-default false positives for undeclared dynamic nodes. |
| `ProtectedSlots` | sort | Immutable admin slot-lock snapshot: player inventory slots in the `locked_slots.player` config list or covered by an explicit `clicksorted.lock.player.slot.<n>` permission node are excluded from the sortable slot set (player inventories only). Same `isPermissionSet`-before-`hasPermission` OP guard as `ProtectedItems`. Namespace is hierarchical (`lock.player.*`) to reserve room for future `lock.container.*` categories. |
| `BundleBenchmark` | sort | In-situ micro-benchmark of the sort and bundle-repack paths (`/clicksorted admin benchmark`) |
| `ClickSortedHolder` | gui | Base `InventoryHolder` marker for all ClickSorted-owned GUIs (used to block self-sort) |
| `PreferenceRepair` | migration | Validates and resets invalid per-player PDC preferences on login, notifying the player |
| `UpdateChecker` | update | Best-effort async Modrinth API check; logs a console notice when a newer release exists. `restart()` (the single enable/reload entry point) fires a gated immediate check then calls `reschedule()`, which (re)arms a recurring check per `check_for_updates_interval_hours`; `stop()` cancels it. |
| `ConfigManager` | config | Unified lifecycle for all four config files |
| `ResourceUpdater` | config | Add-only merge of bundled resource into plugin data folder |
| `Log`, `DebugLevel` | logging | Plugin logger wrapper with gated debug levels |
| `MessageUtil` | text | Coloured Adventure `Component` message helpers |
| `CooldownMessenger` | text | Rate-limits repeated messages to players |
| `Permissions` | security | Permission-check helper with debug logging |
| `ActionThrottle` | security | Global per-player rate limiter (`action_cooldown_ms`) gating every plugin-driven action; `throttled()` also sends the rate-limited notice |

### Configuration Files (src/main/resources)

- `config.yml` — debug level, sortable inventory types, `action_cooldown_ms` throttle, `check_for_updates` flag and `check_for_updates_interval_hours` cadence, per-player `defaults` (including `enabled`, click/sort mode, `start_corner`, `fill_axis`, sort-over-items, bundle packing), the admin `blacklist` section (`blacklist.materials` / `blacklist.names` — items matching these are never sorted, moved, or packed by anyone), and the admin `locked_slots` section (`locked_slots.player` — list of player inventory slot indices (0–35) that are always excluded from sorting; also enforced via `clicksorted.lock.player.slot.<n>` permission nodes)
- `groups.yml` — item groupings for GROUP sort method
- `items.yml` — persistent store of material → display-name mappings
- `lang.yml` — all user-facing messages (MiniMessage format)

### Testing

Tests live in `src/test/java/net/kccricket/clicksorted/` and are **integration-level**: they bootstrap the full plugin via `MockBukkit.loadWithConfig()` and dispatch real Bukkit events. `AbstractClickSortedTest` is the shared base class — it wires up the server, loads the plugin with `src/test/resources/test-config.yml` (bStats disabled), and provides helper methods for simulating inventory interactions.

There is a known non-obvious setup required for MockBukkit v4 on Java 16+; see `memory/mockbukkit-v4-test-setup.md` for the full list of fixes (ByteBuddy opens, JARUtil null-guard, SQLite JDBC classloader, DurationUtil format, JaCoCo scoping).

### Command Framework

Commands are implemented as a Brigadier tree in `ClickSortedCommands` and registered via `LifecycleEvents.COMMANDS`. Each subcommand is a static builder method. The tree is:

- **`/clicksorted` (bare)** — toggles the player's `enabled` flag (on→off or off→on). Requires `clicksorted.commands.sort.enabled` and `clicksorted.commands` (both default: true).
- **`sort`** — sorting preferences (requires `clicksorted.commands.sort`):
  - `sort enabled [yes|no]` — toggle or set click-sorting on/off (same as bare command when toggling). Requires `clicksorted.commands.sort.enabled`.
  - `sort method <NAME|GROUP|TREEMAP>` — choose the sort order. Requires `clicksorted.commands.sort.method`.
  - `sort start-corner <TOP_LEFT|TOP_RIGHT|BOTTOM_LEFT|BOTTOM_RIGHT>`. Requires `clicksorted.commands.sort.start-corner`.
  - `sort fill-axis <HORIZONTAL|VERTICAL>`. Requires `clicksorted.commands.sort.fill-axis`.
- **`click`** — click-method preferences (requires `clicksorted.commands.click`):
  - `click method <SWAP|SINGLE_CLICK|DOUBLE_CLICK|CONTROL_DROP|SHIFT_LEFT_CLICK|SHIFT_RIGHT_CLICK>`. Requires `clicksorted.commands.click.method`.
  - `click allow-on-hover [yes|no]` — toggle or set sort-over-items. Some methods govern this automatically. Requires `clicksorted.commands.click.hover`.
- **`lock-slots`** — opens the slot-lock GUI. Admin-locked slots (config or permission) render as IRON_BARS and are non-toggleable. Requires `clicksorted.commands.lock`.
- **`bundle`** — bundle-packing preferences (requires `clicksorted.commands.bundle`; bare `bundle` has no default action — a subcommand is required):
  - `bundle enabled [yes|no]` — toggle bundle packing for both inventory and containers.
  - `bundle enabled in-inventory <yes|no>` — toggle bundle packing in the player's own inventory.
  - `bundle enabled in-containers <yes|no>` — toggle bundle packing in containers.
  - `bundle stack-limit <n|off>` — max distinct item entries per bundle.
  - `bundle blacklist` / `bundle blacklist gui` — open the 54-slot blacklist GUI (click an item in the real inventory to add by material or display name; click a listed entry to remove; arrows paginate).
  - `bundle blacklist add material|remove material <material>` — material text-command alternatives.
  - `bundle blacklist add item-name|remove item-name <text>` — display-name text-command alternatives.
  - `bundle blacklist list`, `bundle blacklist clear` — cover both material and display-name entries together.
- **`status`** — print the player's current enabled state, click method, sort method, start corner, fill axis, sort-over-items, and bundle settings. Requires `clicksorted.commands.status`.
- **`admin`** — admin/diagnostic commands (requires `clicksorted.admin.commands`):
  - `admin reload` — reload all config files. Requires `clicksorted.admin.commands.reload`.
  - `admin config` — print every `config.yml` key/value. Requires `clicksorted.admin.commands.config`.
  - `admin debug [off|debug|trace]` — set logging verbosity at runtime (not persisted). Requires `clicksorted.admin.commands.debug`.
  - `admin benchmark [iterations]` — run an in-situ micro-benchmark. Default 2000 iterations (100–50000). Requires `clicksorted.admin.commands.benchmark`.

`click allow-on-hover` toggles the per-player "sort over items" flag (server default `defaults.sort_over_items`), which controls whether a sort fires only on an empty slot or also while hovering an occupied one.

Player-facing command handlers resolve the executor via the shared `requirePlayer(plugin, ctx)` helper and gate on `ActionThrottle.throttled(player)`; both return early on failure. Boolean arguments use `yes`/`no` strings parsed via `parseState`.

Note: the `AbstractCommand` / `CommandManager` pattern referenced in older docs no longer applies — the codebase uses Paper's native Brigadier API.

### Master kill-switch permission

`clicksorted` (default `true`) is the top-level player-facing kill-switch. Denying it disables:
- Click-triggered sorting (both modes)
- Click-triggered bundle packing (both modes)
- All player-facing commands (`/clicksorted`, `/clicksorted sort`, `/clicksorted click`, `/clicksorted lock-slots`, `/clicksorted bundle`, `/clicksorted status`)

It has **no effect on admin access**: `clicksorted.admin.commands.*` is gated separately and is not a child of `clicksorted`. Admins can still run `admin reload`, `admin config`, `admin debug`, and `admin benchmark` regardless of the master switch.

The constant `Permissions.PERM_MASTER = "clicksorted"` is used at all check sites.

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

### Folia safety

`paper-plugin.yml` declares `folia-supported: true`. The sort runs synchronously on the
event-delivery (region) thread with no scheduling, and every piece of plugin-global mutable state
it touches is concurrency-safe (`ActionThrottle`/`CooldownMessenger` use `ConcurrentHashMap`;
config objects are swapped atomically; `SortEngine`/`InPlacePacker`/`BundlePacker` are stateless
statics on method-local data). Two players in different regions clicking simultaneously cannot
corrupt plugin state through any of that.

The one remaining gap is a plugin-created *virtual* inventory (`Bukkit.createInventory`, backed by
no block/chunk, so owned by no region) whose single backing instance is open to viewers in
**different** regions at once (e.g. a shared GUI). Two region threads could then concurrently
read-modify-write the same backing array — a data race and potential item dupe — and
`refreshViewers()`'s `updateInventory()` call would touch another region's player illegally.
Vanilla inventories don't have this problem: block containers are chunk-owned so Folia delivers
every viewer's click on the same region thread, and player inventory / ender chest are
single-owner.

`InventorySortService` guards this with `isUnsafeSharedInventory(Inventory)`: on Folia only
(`FOLIA`, a one-time `Class.forName` capability probe), a non-vanilla-held inventory with more
than one current viewer is refused as a sort target — a clean no-op, same as clicking an
unsortable type. This is deliberately a refusal, not a lock: a lock only covers our own sort code,
so it cannot stop the *other* viewer's vanilla click (processed on that viewer's region thread,
outside any lock we hold) from racing the sort — do not "fix" this by adding a per-inventory lock
or in-progress-sort registry. On Paper, `FOLIA` is `false` and this check is inert.

## Release Process

Releases are published by pushing a `release/<version>` tag (e.g. `release/2.0.0`) to `origin` while `gradle.properties` `version` matches the tag. The GitHub Actions workflow at `.github/workflows/release.yml` then:

1. Verifies the tag version matches `gradle.properties`.
2. Runs `./gradlew clean test build writeReleaseNotes` (the `writeReleaseNotes` Gradle task reads the top section of `CHANGELOG.md` to produce `build/release-notes.md`).
3. Creates a GitHub Release with the built JAR and the release notes.
4. Publishes to Modrinth (`./gradlew modrinth`).
5. Publishes to Hangar (`./gradlew publishPluginPublicationToHangar syncPluginPublicationMainResourcePagePageToHangar`).

**`README.md`** (repo root) is the long-form plugin page description synced to Hangar and Modrinth on every release. Keep it up to date with major feature changes.

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
