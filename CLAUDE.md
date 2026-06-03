# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
mvn clean package          # Build JAR → target/clicksort.jar
mvn clean test             # Run all tests
mvn test -Dtest=ClassName  # Run a single test class
mvn test -Dtest=ClassName#methodName  # Run a single test method
```

Maven Surefire is pre-configured with the `--add-opens` JVM args needed for MockBukkit + ByteBuddy on Java 16+; no extra flags needed.

## Architecture Overview

ClickSort is a Paper/Bukkit plugin that lets players sort inventories via configurable mouse clicks. The main plugin class (`me.desht.clicksort.ClickSortPlugin`) acts as both the `JavaPlugin` entry point and a `Listener` for inventory events.

### Core Flow

1. `InventoryClickEvent` fires when a player clicks inside an inventory.
2. `ClickSortPlugin` checks the player's `PlayerSortingPrefs` (stored in Bukkit's Persistent Data Container) to see if the click matches their configured `ClickMethod`.
3. If it matches, a `SortKey`-based sort runs: items are collapsed into a `TreeMap<SortKey, Integer>` (material → quantity), then reconstructed into stacks and written back to the inventory.
4. A custom `InventorySortEvent` fires after sorting so third-party plugins can intervene.

### Key Classes

| Class | Role |
|---|---|
| `ClickSortPlugin` | Event handling, onEnable/onDisable wiring, command dispatch |
| `PlayerSortingPrefs` | Per-player state (ClickMethod, SortingMethod, shift-click flag) stored via PDC |
| `SortKey` | `Comparable` wrapper around an ItemStack that drives all sort ordering |
| `SortingMethod` | Enum (NAME, ID, GROUP) controlling `SortKey.makeSortPrefix()` |
| `ClickMethod` | Enum (SINGLE, DOUBLE, MIDDLE, SWAP, NONE) with version-aware `isAvailable()` |
| `me.desht.dhutils.*` | Internal utility framework: `CommandManager`, `LogUtils`, `Debugger`, `MiscUtil` |
| `xyz.chengzi.clicksort.util.LocalUtil` | Message localization backed by `lang.yml` |

### Configuration Files (src/main/resources)

- `config.yml` — debug level, sortable inventory types, `player_sort_min`/`player_sort_max` slot range
- `groups.yml` — item groupings for GROUP sort method
- `items.yml` — localization store for `LocalUtil` (custom item names)
- `lang.yml` — all user-facing messages

### Testing

Tests live in `src/test/java/me/desht/clicksort/` and are **integration-level**: they bootstrap the full plugin via `MockBukkit.loadWithConfig()` and dispatch real Bukkit events. `AbstractClickSortTest` is the shared base class — it wires up the server, loads the plugin with `src/test/resources/test-config.yml` (bStats disabled), and provides helper methods for simulating inventory interactions.

There is a known non-obvious setup required for MockBukkit v4 on Java 16+; see `memory/mockbukkit-v4-test-setup.md` for the full list of fixes (ByteBuddy opens, JARUtil null-guard, SQLite JDBC classloader, DurationUtil format, JaCoCo scoping).

### Command Framework

Commands extend `AbstractCommand` (`me.desht.dhutils.commands`) and are registered with `CommandManager`. Each command declares its label, permission, min/max arg counts, and implements `execute()`. Tab completion is per-command via `onTabComplete()`.

### Version Compatibility

`ClickMethod` and `SortingMethod` both expose `isAvailable()` and `preferredDefault()` for graceful degradation when running on older server versions. Check these before adding new enum values that depend on newer Bukkit API.
