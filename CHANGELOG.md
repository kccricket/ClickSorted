# ClickSorted 1.1.0

ClickSorted 1.1.0 adds slot locking and bundle packing, restructures all player preferences under a single `set` command, and introduces a settings-migration framework so stored values survive future renames.

## Highlights

- **Slot locking** — mark individual inventory slots to keep them untouched by sorting, managed through a visual `/clicksorted set lock` GUI.
- **Bundle packing** — sorting can now fold each item type's leftover remainder into the bundles you keep in an inventory, reclaiming slots in the same click. Opt in per region for your own inventory and for containers.
- **Commands restructured under `set`** — per-player preferences now live under `/clicksorted set …` (`sort-method`, `click-method`, `hover`, `lock`, `bundle`), with a new `/clicksorted status` to print your current settings.
- **Settings migration framework** — a single catalog migrates renamed values and drops removed settings across both config and per-player data, so legacy values (e.g. `DOUBLE` → `DOUBLE_CLICK`) convert automatically on load/join.
- **Per-player action throttle** — caps how fast a scripted client can drive plugin work, with a `/clicksorted benchmark` diagnostic for measuring the sort and repack paths.

## Breaking Changes

- **Preference commands moved under `set`.** `/clicksorted sort` is now `/clicksorted set sort-method` and `/clicksorted click` is now `/clicksorted set click-method`; the old top-level forms no longer exist. Update any macros, command blocks, or aliases. (The `clicksorted.commands.sort` / `.click` permission nodes are unchanged.)
- **Shift-click preference cycling removed.** Shift-left/right-clicking in your inventory no longer cycles your sort and click preferences. The `/clicksorted shiftclick` command, the `clicksorted.commands.shiftclick` permission, and the `defaults.shift_click` config key are gone. Shift-left-click and shift-right-click are now available as explicit sort *triggers* instead (`/clicksorted set click-method shift_left_click|shift_right_click`).
- **`ClickMethod` values renamed.** `DOUBLE` → `DOUBLE_CLICK` and `SINGLE` → `SINGLE_CLICK`. Stored player preferences and `config.yml` values are rewritten automatically on load/join, so no manual action is needed unless you script against the raw values.

## New Features

### Slot locking

`/clicksorted set lock` opens a chest GUI that mirrors your inventory — three rows for main storage and one for the hotbar, separated by a divider. Lime panes are unlocked, barrier icons are locked, and black panes mark slots outside the sortable range. Click a pane to toggle it; changes save immediately, and locked slots are neither read nor overwritten when you sort. Locks apply only to your own inventory (main region and hotbar) — containers always sort in full.

### Bundle packing

When enabled, a sort first consolidates bundle-eligible items by type: full stacks stay loose in the inventory and only the leftover remainder is packed into a bundle — and only when it's an efficient trade (the remainder is at or below half a bundle's weight). The result is a pure function of the item multiset, so re-running on an unchanged inventory is a no-op. Toggle it per region with `/clicksorted set bundle inventory on|off` and `/clicksorted set bundle others on|off`, and cap distinct entries per bundle with `/clicksorted set bundle stacklimit <n|off>` (server defaults: `defaults.bundle_inventory`, `defaults.bundle_others`, `defaults.bundle_stack_limit`).

### Command restructure & status

All preference commands moved under `/clicksorted set` (`sort` → `set sort-method`, `click` → `set click-method`, plus `set hover`, `set lock`, `set bundle`). A new `/clicksorted status` reports your click method, sort method, and sort-over-items state.

### Settings migration framework

A new `Migrations` catalog owns "what is stored where," migrating both `config.yml` and per-player persistent data through one entry point each. Renamed values are declared as lineages that converge in a single pass; removed settings are dropped from the store. Legacy `ClickMethod` spellings migrate automatically and the deprecated `shift_click` setting is removed on upgrade.

### Action throttle & benchmark

A global per-player rate limiter (`action_cooldown_ms`, default 150 ms) gates every plugin-driven action; players with `clicksorted.throttle.bypass` (default op) are exempt. `/clicksorted benchmark [iterations]` runs an in-situ micro-benchmark of the sort and bundle-repack paths.

## Bug Fixes

- Empty-slot sorting, over-cancellation, and non-fungible item merge — sorting on empty slots, event over-cancellation, and merging of non-fungible items (bundles and non-stackables) are now handled correctly.
- `player_sort_max` is now treated as an exclusive bound (matching its default of 36) and is clamped to the armor boundary, so a misconfigured value can no longer scramble armor or off-hand slots.

## Other Improvements

- Consolidated the throttle gate into `ActionThrottle.throttled()` and routed all command handlers through shared `requirePlayer`/`parseState` helpers, removing duplicated guard logic.
- Regenerated `groups.yml` for Minecraft 26.1.2, correcting some mis-grouped items (e.g. chains).
- README and CLAUDE.md updated for the `set` command tree, bundle packing, the action throttle, and the new config/lang keys.

## Compatibility

✔️ Paper/Folia 1.20.6 – 26.1.x
✔️ Java 21

## Upgrading

1. Stop your server.
2. Replace the old jar in `plugins/` with this release.
3. Start your server.

> Note: Existing preferences migrate automatically — legacy click-mode values (`DOUBLE`, `SINGLE`) are rewritten to their current spellings and the deprecated `shift_click` setting is dropped on first load/join. No manual config edits are required.

## What's Changed

- Add slot-lock GUI (`/clicksorted set lock`) by @kccricket
- Restructure commands under `set`, rename click/sort, add status by @kccricket
- Settings migration framework, hover command, and expanded ClickMethod by @kccricket
- Pool-and-repack bundle packing folded into a unified sort pipeline by @kccricket
- Per-player action throttle and in-situ benchmark by @kccricket
- Fix empty-slot sorting, over-cancellation, and non-fungible item merge by @kccricket
- Fix `player_sort_max` exclusivity and clamping by @kccricket
- Fix Minecraft 26.1.2 item grouping (chains) by @kccricket

---

# ClickSorted 1.0.0

Release date: 2026-06-07

ClickSorted 1.0.0 is a ground-up modernization of the original ClickSort plugin,
rewritten for Paper 1.21.x with a clean architecture and no legacy dependencies.

## Highlights

- New package & name — rebranded from `ClickSort` (`me.desht.clicksort`) to `ClickSorted`
  (`net.kccricket.clicksorted`) with a fully reorganized, concern-based package layout.
- No more SQLite — player sorting preferences are now stored in Bukkit's Persistent Data
  Container; no database file, no async JDBC, no migration needed.
- Paper-native commands — the old dhutils `AbstractCommand` / `CommandManager` framework
  is gone; commands are a Brigadier tree registered via `LifecycleEvents.COMMANDS`.
- Adventure / MiniMessage messages — all user-facing text is now an Adventure `Component`;
  legacy § color codes are fully replaced and `lang.yml` uses MiniMessage format throughout.
- Full integration test suite — 10 test classes covering sort behavior, commands, config
  reload, cooldown messaging, player prefs, and resource updating via MockBukkit.

## New Features

### Add-only config merging

`ResourceUpdater` performs an add-only merge of bundled defaults into the plugin data
folder on startup. New keys added in a future release appear in your live config
automatically; existing customizations are never overwritten.

### Expanded sortable inventory types

`config.yml` now includes additional inventory types (barrel, blast furnace,
dispenser, dropper, furnace, hopper, smoker) in the default `sortable_inventories`
list so more containers sort out of the box.

### Updated item groups

`groups.yml` has been updated to reflect the full 1.21.5 creative-mode tab groupings,
giving GROUP sort a more intuitive ordering for current-version items.

## Bug Fixes

- `SortEngine` null/empty-inventory edge cases hardened — operations on inventories
  with no sortable slots no longer throw.
- `InventorySortEvent` defensive guard — the post-sort event is only fired when a sort
  actually occurs, preventing spurious third-party handler calls.
- `LangConfig` missing-key fallback — referencing an undefined message key now returns
  a safe placeholder instead of throwing a `NullPointerException`.

## Other Improvements

- **Build system** — migrated from Maven (`pom.xml`) to Gradle (`build.gradle.kts`) with
  the Shadow plugin for fat-JAR assembly.
- **Java 21 bytecode target** — the build now emits Java 21 class files, matching the
  minimum JVM required by modern Paper builds.
- **Removed dhutils** — the entire embedded `me.desht.dhutils` library (≈3 000 lines) is
  gone; only the handful of utilities actually used by the plugin were rewritten as
  focused classes (`Log`, `ItemNames`, `CooldownMessenger`, `Permissions`).
- **Removed Essentials integration** — the VALUE sort method (which relied on Essentials
  item worth data) has been removed; NAME and GROUP remain.
- **Removed `coloured_console` setting** — console output is always plain text; the
  config key had no effect on modern Paper and is no longer present.
- **Removed legacy version compatibility shims** — version-parsing workarounds for
  Minecraft 1.12 and earlier are gone.
- **Unified config lifecycle** — all four config files (`config.yml`, `lang.yml`,
  `groups.yml`, `items.yml`) are managed through a single `ConfigManager`; reload via
  `/clicksorted reload` refreshes all of them atomically.
- **Typed debug levels** — `DebugLevel` is now an enum (`OFF`, `LOW`, `MEDIUM`, `HIGH`)
  replacing the old integer-based debug flag, with log calls gated by level comparison.
- **Documented YAML configs** — all four bundled resource files now have inline comments
  explaining every key.
- **Expanded README** — covers installation, permissions, commands, configuration
  reference, and the GROUP sort method.

## Compatibility

✔️ Paper/Folia 1.20.6+  
✔️ Java 21  

## Upgrading from ClickSort (original fork)

1. Stop your server.
2. Remove the old ClickSort jar from `plugins/`.
3. Place `clicksorted-1.0.0.jar` in `plugins/`.
4. Start your server. Fresh `config.yml`, `lang.yml`, `groups.yml`, and `items.yml`
   files will be generated in `plugins/ClickSorted/`.
5. (Optional) Review your old `plugins/ClickSort/` settings and migrate any
   customizations to the new config files. Note that player sorting preferences
   were stored in SQLite and cannot be migrated; players will start with defaults.

## Upgrading from ClickSort (original fork)

1. Stop your server.
2. **Remove** the old ClickSort jar from `plugins/`.
3. **Delete** `plugins/ClickSort/` — player preferences were stored in SQLite and cannot
   be migrated; players will start with default preferences.
4. Place `clicksorted-1.0.0.jar` in `plugins/`.
5. Start your server. Fresh `config.yml`, `lang.yml`, `groups.yml`, and `items.yml`
   files will be generated in `plugins/ClickSorted/`.