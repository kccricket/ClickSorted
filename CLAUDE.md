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
├── model/                   ClickMethod, SortingMethod, SortKey, PlayerSortingPrefs
├── commands/                ClickSortedCommands (Brigadier command tree)
├── config/                  ConfigManager, ManagedConfig, MainConfig, LangConfig,
│                            GroupsConfig, ItemsConfig, ResourceUpdater
├── events/                  InventorySortEvent
├── gui/                     LockGuiHolder, LockGuiListener
├── sort/                    InventoryClickListener, InventorySortService, SortEngine
├── migration/               ValueMigration, Migrations, PlayerMigrationListener
├── text/                    MessageUtil, CooldownMessenger, ItemNames
├── logging/                 Log, DebugLevel
└── security/                Permissions
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
2. `InventoryClickListener` checks the player's `PlayerSortingPrefs` (stored in Bukkit's Persistent Data Container) to see if the click matches their configured `ClickMethod`.
3. If it matches, `InventorySortService` delegates to `SortEngine`: items are collapsed into a `HashMap<SortKey, Integer>` (material → quantity), then reconstructed into stacks and written back to the inventory.
4. A custom `InventorySortEvent` fires after sorting so third-party plugins can intervene.
5. For player inventories, any slots the player has locked (via `/clicksorted lock`) are excluded from the sortable set before `SortEngine` runs — locked slots are neither read nor overwritten.

### Key Classes

| Class | Package | Role |
|---|---|---|
| `ClickSortedPlugin` | root | `JavaPlugin` entry point, wires all components |
| `PlayerSortingPrefs` | model | Per-player state (ClickMethod, SortingMethod, sort-over-items flag, locked slots) stored via PDC |
| `LockGuiHolder` | gui | 45-slot chest inventory for the lock GUI; builds lime/barrier panes and maps chest↔inventory slots |
| `LockGuiListener` | gui | Handles clicks/drags in the lock GUI; toggles lock state and cancels all real-inventory interaction |
| `SortKey` | model | `Comparable` wrapper around an ItemStack that drives all sort ordering |
| `SortingMethod` | model | Enum (NAME, GROUP) controlling `SortKey.makeSortPrefix()` |
| `ClickMethod` | model | Enum (SINGLE_CLICK, DOUBLE_CLICK, SWAP, CONTROL_DROP, SHIFT_LEFT_CLICK, SHIFT_RIGHT_CLICK, NONE) |
| `InventoryClickListener` | sort | Dispatches click events to sort or cycle prefs |
| `InventorySortService` | sort | Target resolution, permissions, event lifecycle, write-back |
| `SortEngine` | sort | Pure sort/merge algorithm (no plugin state) |
| `ConfigManager` | config | Unified lifecycle for all four config files |
| `ResourceUpdater` | config | Add-only merge of bundled resource into plugin data folder |
| `Log`, `DebugLevel` | logging | Plugin logger wrapper with gated debug levels |
| `MessageUtil` | text | Coloured Adventure `Component` message helpers |
| `CooldownMessenger` | text | Rate-limits repeated messages to players |
| `Permissions` | security | Permission-check helper with debug logging |

### Configuration Files (src/main/resources)

- `config.yml` — debug level, sortable inventory types, `player_sort_min`/`player_sort_max` slot range
- `groups.yml` — item groupings for GROUP sort method
- `items.yml` — persistent store of material → display-name mappings
- `lang.yml` — all user-facing messages (MiniMessage format)

### Testing

Tests live in `src/test/java/net/kccricket/clicksorted/` and are **integration-level**: they bootstrap the full plugin via `MockBukkit.loadWithConfig()` and dispatch real Bukkit events. `AbstractClickSortedTest` is the shared base class — it wires up the server, loads the plugin with `src/test/resources/test-config.yml` (bStats disabled), and provides helper methods for simulating inventory interactions.

There is a known non-obvious setup required for MockBukkit v4 on Java 16+; see `memory/mockbukkit-v4-test-setup.md` for the full list of fixes (ByteBuddy opens, JARUtil null-guard, SQLite JDBC classloader, DurationUtil format, JaCoCo scoping).

### Command Framework

Commands are implemented as a Brigadier tree in `ClickSortedCommands` and registered via `LifecycleEvents.COMMANDS`. Each subcommand (`sort`, `click`, `hover`, `lock`, `bundle`, `reload`, `getcfg`, `debug`) is a static builder method. `/clicksorted hover` toggles the per-player "sort over items" flag (server default `defaults.sort_over_items`), which controls whether a sort fires only on an empty slot or also while hovering an occupied one. Note: the `AbstractCommand` / `CommandManager` pattern referenced in older docs no longer applies — the codebase uses Paper's native Brigadier API.

### Version Compatibility

`ClickMethod` and `SortingMethod` are plain enums. `SortingMethod.isAvailable()` checks whether `groups.yml` has any mappings loaded (GROUP requires a populated groups file).

## Release Notes Template

Use this format when drafting GitHub release notes. Omit sections that have no entries.

```markdown
ClickSorted <version> <one-sentence summary of the release theme>.

## Highlights

- <bullet> — <one-line description>

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
