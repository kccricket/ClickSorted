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
│                            TreemapPacker, PrefsCycleHandler
├── migration/               ValueMigration, Migrations, PlayerMigrationListener,
│                            PreferenceRepair
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

Two kinds of catalog rule, applied symmetrically across both stores:

- **Renamed values.** Declare a `ValueMigration` lineage with
  `ValueMigration.builder().rename(old).to(next).to(newer)…build()`. The last token is the current
  canonical value; every earlier token maps **directly** to it, so any value ever stored converges in a
  single pass. Adding a future rename is a pure append (`.to("X")`). The catalog maps each storage
  location to its lineage (config path `defaults.click_mode` and PDC key `click` both → `CLICK_METHOD`).
- **Removed settings.** List the deprecated storage location in `DEPRECATED_CONFIG_PATHS` /
  `DEPRECATED_PDC_KEYS` and the migrator drops it from the store (e.g. `defaults.shift_click` /
  `shift_click`).

The per-location read→migrate→write and removal logic lives in **private** helpers inside `Migrations`;
the lineage definitions stay pure data.

### Core Flow

1. `InventoryClickEvent` fires when a player clicks inside an inventory.
2. `InventoryClickListener` checks the player's `PlayerSortingPrefs` (stored in Bukkit's Persistent Data Container) to see if the click matches their configured `ClickMethod`, applies the "sort over items" gate, and runs the shared `ActionThrottle` rate-limiter.
3. If it matches, `InventorySortService` delegates to `SortEngine`: fungible items are collapsed into a `HashMap<SortKey, Integer>` (material → quantity) and non-fungible items (bundles, non-stackables) are kept discrete, then everything is reconstructed into stacks and written back to the inventory.
4. When bundle packing is enabled for the target (`defaults.bundle_inventory` / `defaults.bundle_others`, toggled per-player), `InventorySortService` first runs `BundlePacker` to repack partial stacks into bundles before the sort.
5. A custom `InventorySortEvent` fires after sorting so third-party plugins can intervene.
6. For player inventories, any slots the player has locked (via `/clicksorted set lock`) are excluded from the sortable set before `SortEngine` runs — locked slots are neither read nor overwritten.
7. On startup (and after `/clicksorted reload`), `UpdateChecker` runs an async best-effort Modrinth API call and logs a console notice if a newer release exists (`check_for_updates: true`).
8. On `PlayerJoinEvent`, `PreferenceRepair` validates the player's PDC preferences and resets any that hold unrecognised values, notifying the player in chat.

### Key Classes

| Class | Package | Role |
|---|---|---|
| `ClickSortedPlugin` | root | `JavaPlugin` entry point, wires all components |
| `PlayerSortingPrefs` | model | Per-player state (ClickMethod, SortingMethod, sort-over-items flag, bundle-packing flags, bundle stack limit, locked slots) stored via PDC |
| `LockGuiHolder` | gui | 45-slot chest inventory for the lock GUI; builds lime/barrier panes and maps chest↔inventory slots |
| `LockGuiListener` | gui | Handles clicks/drags in the lock GUI; toggles lock state and cancels all real-inventory interaction |
| `SortKey` | model | `Comparable` wrapper around an ItemStack that drives all sort ordering |
| `SortingMethod` | model | Enum (NAME, GROUP, TREEMAP) controlling `SortKey.makeSortPrefix()`; `isTreemap()` routes placement through `TreemapPacker` instead of `SlotOrder` |
| `ClickMethod` | model | Enum (SINGLE_CLICK, DOUBLE_CLICK, SWAP, CONTROL_DROP, SHIFT_LEFT_CLICK, SHIFT_RIGHT_CLICK, NONE) |
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

- `config.yml` — debug level, sortable inventory types, `player_sort_min`/`player_sort_max` slot range, `action_cooldown_ms` throttle, `check_for_updates` flag, and per-player `defaults` (click/sort mode, `start_corner`, `fill_axis`, sort-over-items, bundle packing)
- `groups.yml` — item groupings for GROUP sort method
- `items.yml` — persistent store of material → display-name mappings
- `lang.yml` — all user-facing messages (MiniMessage format)

### Testing

Tests live in `src/test/java/net/kccricket/clicksorted/` and are **integration-level**: they bootstrap the full plugin via `MockBukkit.loadWithConfig()` and dispatch real Bukkit events. `AbstractClickSortedTest` is the shared base class — it wires up the server, loads the plugin with `src/test/resources/test-config.yml` (bStats disabled), and provides helper methods for simulating inventory interactions.

There is a known non-obvious setup required for MockBukkit v4 on Java 16+; see `memory/mockbukkit-v4-test-setup.md` for the full list of fixes (ByteBuddy opens, JARUtil null-guard, SQLite JDBC classloader, DurationUtil format, JaCoCo scoping).

### Command Framework

Commands are implemented as a Brigadier tree in `ClickSortedCommands` and registered via `LifecycleEvents.COMMANDS`. Each subcommand is a static builder method. The tree is:

- **`set`** — per-player preferences: `set sort-method <NAME|GROUP|TREEMAP>`, `set click-method <…>`, `set start-corner <TOP_LEFT|TOP_RIGHT|BOTTOM_LEFT|BOTTOM_RIGHT>`, `set fill-axis <HORIZONTAL|VERTICAL>`, `set hover [on|off]` (toggles when no arg; some click methods govern this automatically), `set lock` (opens the lock GUI), and `set bundle` (no-arg prints status; `set bundle inventory|others <on|off>`; `set bundle stacklimit <n|off>`).
- **`status`** — print the player's current click method, sort method, start corner, fill axis, and sort-over-items state.
- **`reload`**, **`getcfg`**, **`debug [level]`**, **`benchmark [iterations]`** — admin/diagnostic commands.

`set hover` toggles the per-player "sort over items" flag (server default `defaults.sort_over_items`), which controls whether a sort fires only on an empty slot or also while hovering an occupied one.

Player-facing command handlers resolve the executor via the shared `requirePlayer(plugin, ctx)` helper and gate on `ActionThrottle.throttled(player)`; both return early on failure. Boolean on/off arguments are parsed via `parseState` (the sole consumer of the `ON_WORDS`/`OFF_WORDS` vocabularies).

Note: the `AbstractCommand` / `CommandManager` pattern referenced in older docs no longer applies — the codebase uses Paper's native Brigadier API.

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
