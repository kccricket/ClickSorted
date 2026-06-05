# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
./gradlew clean build                                    # Build JAR → build/libs/clicksort-<version>.jar
./gradlew clean test                                     # Run all tests
./gradlew test --tests "ClassName"                       # Run a single test class
./gradlew test --tests "ClassName.methodName"            # Run a single test method
```

The Gradle `test` task is pre-configured with the `--add-opens` JVM args needed for MockBukkit + ByteBuddy on Java 16+; no extra flags needed. JUnit Platform launcher is added via `testRuntimeOnly`.

## Architecture Overview

ClickSort is a Paper/Bukkit plugin that lets players sort inventories via configurable mouse clicks. The main plugin class (`net.kccricket.clicksort.ClickSortPlugin`) is the `JavaPlugin` entry point.

### Package Layout

```
net.kccricket.clicksort
├── ClickSortPlugin          entry point
├── model/                   ClickMethod, SortingMethod, SortKey, PlayerSortingPrefs
├── commands/                ClickSortCommands (Brigadier command tree)
├── config/                  ConfigManager, ManagedConfig, MainConfig, LangConfig,
│                            GroupsConfig, ItemsConfig, ResourceUpdater
├── events/                  InventorySortEvent
├── sort/                    InventoryClickListener, InventorySortService,
│                            PrefsCycleHandler, SortEngine
├── text/                    MessageUtil, CooldownMessenger, ItemNames
├── logging/                 Log, DebugLevel
└── security/                Permissions
```

### Core Flow

1. `InventoryClickEvent` fires when a player clicks inside an inventory.
2. `InventoryClickListener` checks the player's `PlayerSortingPrefs` (stored in Bukkit's Persistent Data Container) to see if the click matches their configured `ClickMethod`.
3. If it matches, `InventorySortService` delegates to `SortEngine`: items are collapsed into a `HashMap<SortKey, Integer>` (material → quantity), then reconstructed into stacks and written back to the inventory.
4. A custom `InventorySortEvent` fires after sorting so third-party plugins can intervene.

### Key Classes

| Class | Package | Role |
|---|---|---|
| `ClickSortPlugin` | root | `JavaPlugin` entry point, wires all components |
| `PlayerSortingPrefs` | model | Per-player state (ClickMethod, SortingMethod, shift-click flag) stored via PDC |
| `SortKey` | model | `Comparable` wrapper around an ItemStack that drives all sort ordering |
| `SortingMethod` | model | Enum (NAME, GROUP) controlling `SortKey.makeSortPrefix()` |
| `ClickMethod` | model | Enum (SINGLE, DOUBLE, SWAP, NONE) |
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

Tests live in `src/test/java/net/kccricket/clicksort/` and are **integration-level**: they bootstrap the full plugin via `MockBukkit.loadWithConfig()` and dispatch real Bukkit events. `AbstractClickSortTest` is the shared base class — it wires up the server, loads the plugin with `src/test/resources/test-config.yml` (bStats disabled), and provides helper methods for simulating inventory interactions.

There is a known non-obvious setup required for MockBukkit v4 on Java 16+; see `memory/mockbukkit-v4-test-setup.md` for the full list of fixes (ByteBuddy opens, JARUtil null-guard, SQLite JDBC classloader, DurationUtil format, JaCoCo scoping).

### Command Framework

Commands are implemented as a Brigadier tree in `ClickSortCommands` and registered via `LifecycleEvents.COMMANDS`. Each subcommand (`sort`, `click`, `shiftclick`, `reload`, `getcfg`, `debug`) is a static builder method. Note: the `AbstractCommand` / `CommandManager` pattern referenced in older docs no longer applies — the codebase uses Paper's native Brigadier API.

### Version Compatibility

`ClickMethod` and `SortingMethod` are plain enums. `SortingMethod.isAvailable()` checks whether `groups.yml` has any mappings loaded (GROUP requires a populated groups file).
