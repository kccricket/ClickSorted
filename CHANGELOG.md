# ClickSorted 2.0.0

Release date: 2026-06-29

ClickSorted 2.0.0 introduces a per-player bundle blacklist, server-wide admin item and slot locks, a dedicated enabled toggle, and a reorganised command tree.

## Highlights

- **Per-player bundle blacklist** — exclude specific item types or display names from bundle packing via a GUI or commands; blacklisted bundle colors are also skipped as packing bins.
- **Admin item blacklist** — server admins can declare materials and display names that ClickSorted will never sort, move, or pack, via `config.yml` and per-player permission nodes.
- **Admin slot locks** — server admins can lock specific player inventory slots server-wide via `config.yml` (`locked_slots.player`) or `clicksorted.lock.player.slot.<n>` permission nodes. Admin-locked slots appear as non-toggleable iron-bars panes in the lock GUI.
- **Dedicated `enabled` preference** — click-sorting can now be toggled on/off independently of the click method (`/clicksorted sort enabled`). `ClickMethod.NONE` is removed.
- **Command tree reorganised by domain** — per-player preferences grouped under `sort`, `click`, `bundle`, and `lock-slots`; admin diagnostics under `admin`.

## Breaking Changes

- **Command tree restructured.** The `set` subcommand group is removed. Old paths → new paths:
  - `/clicksorted set sort-method` → `/clicksorted sort method`
  - `/clicksorted set click-method` → `/clicksorted click method`
  - `/clicksorted set hover` → `/clicksorted click allow-on-hover`
  - `/clicksorted set lock` → `/clicksorted lock-slots`
  - `/clicksorted set bundle inventory|others` → `/clicksorted bundle enabled in-inventory|in-containers`
  - `/clicksorted set bundle stacklimit` → `/clicksorted bundle stack-limit`
  - `/clicksorted set start-corner` → `/clicksorted sort start-corner`
  - `/clicksorted set fill-axis` → `/clicksorted sort fill-axis`
  - `/clicksorted set enabled` → `/clicksorted sort enabled`
  - Admin commands moved under `admin`: `reload` → `admin reload`, `getcfg` → `admin config`, `debug` → `admin debug`, `benchmark` → `admin benchmark`.
  Update macros, command blocks, and aliases.
- **`ClickMethod.NONE` removed.** Players who had `NONE` stored are automatically migrated: their click method is set to `SWAP` and `enabled` is set to `false`. Use `/clicksorted sort enabled no` (or the bare `/clicksorted` toggle) to disable click-sorting going forward.
- **Config keys renamed** (auto-migrated on first startup):
  - `defaults.bundle_inventory` → `defaults.bundle_in_inventory`
  - `defaults.bundle_others` → `defaults.bundle_in_containers`
- **Permission nodes restructured.** Key renames:
  - `clicksorted.commands.reload` → `clicksorted.admin.commands.reload`
  - `clicksorted.commands.getcfg` → `clicksorted.admin.commands.config`
  - `clicksorted.commands.debug` → `clicksorted.admin.commands.debug`
  - `clicksorted.commands.benchmark` → `clicksorted.admin.commands.benchmark`
  New sort-time bundle gates added: `clicksorted.bundle`, `clicksorted.bundle.inventory`, `clicksorted.bundle.container`.
  Update any permission-plugin configurations that reference the old nodes.

## New Features

### Per-player bundle blacklist

`/clicksorted bundle blacklist` opens a 54-slot GUI: click an item in your real inventory to add it to the blacklist by material (for vanilla items) or by display name (for custom-named items); click a listed entry to remove it; pagination arrows navigate large lists. Text-command alternatives:

- `bundle blacklist add material|remove material <material>`, `bundle blacklist list`, `bundle blacklist clear` — material entries.
- `bundle blacklist add item-name|remove item-name <text>` — display-name entries. `list` and `clear` above cover both materials and names together.

Material entries block any item of that type; name entries match case-insensitively against the item's resolved display name (custom name → `item_name` data component → vanilla / `items.yml` name). Blacklisted items are never packed into or unpacked from bundles; blacklisted bundle colors are also skipped as packing bins.

### Admin item blacklist

Admins can prevent ClickSorted from ever touching specific items — never sorted, moved, or packed/unpacked regardless of player preferences. Two enforcement channels (unioned):

- `blacklist.materials: []` / `blacklist.names: []` in `config.yml` — server-wide; reloads with `/clicksorted admin reload`.
- `clicksorted.blacklist.material.<material>` / `clicksorted.blacklist.name.<slug>` permission nodes — per-player or group-based via a permissions plugin.

Name slug rule: strip legacy color codes → lowercase → collapse non-`[a-z0-9]` runs to `_` → strip edge underscores. E.g. `"Creative Menu"` → `creative_menu`.

### Admin slot locks

Admins can lock specific player inventory slots (0–35) server-wide so they are always excluded from sorting and cannot be toggled by the player in the lock GUI. Two enforcement channels (unioned):

- `locked_slots.player: []` in `config.yml` — server-wide; reloads with `/clicksorted admin reload`.
- `clicksorted.lock.player.slot.<n>` permission nodes — per-player or group-based.

Admin-locked slots render as non-toggleable iron-bars panes in the lock GUI.

### Dedicated `enabled` preference

Click-sorting can now be enabled or disabled independently of which click method is configured. Toggle with the bare `/clicksorted`, with `/clicksorted sort enabled`, or with `/clicksorted sort enabled yes|no`. The enabled state appears in `/clicksorted status`. Server default: `defaults.enabled: true` in `config.yml`.

### `item_name` data component resolution

Name-based matching (admin name blacklist, bundle name blacklist) now resolves the `item_name` data component — the base name set by data packs or plugins before any custom rename — in addition to custom display names. Lookup priority: custom name → `item_name` → vanilla / `items.yml` name.

### Graceful reload failure

Config reload failures are now caught and reported to the admin via the `configReloadFailed` lang key (`lang.yml`), with the previous configuration remaining active. The server console receives the full stack trace.

## Bug Fixes

- **Bundle-displaced items dropped instead of placed** — items displaced from bundles during a sort now fill free slots in the region rather than being dropped on the ground.
- **NPE when bundle contains items with no loose counterpart** — `BundlePacker` now handles the case where a bundle's contents have no corresponding loose stack instead of throwing a `NullPointerException`.

## Other Improvements

- `MaterialNameSet` extracted as a shared record for material + display-name matching, used by both `BundleBlacklist` and `ProtectedItems`.
- Shared GUI helpers consolidated into `ClickSortedHolder` base class (`BlacklistGuiHolder`, `LockGuiHolder`).
- Integration test coverage expanded: crafting exclusion, item conservation, fuzz testing, held-cursor state, and more.

## Compatibility

✔️ Paper/Folia 1.21.5 – 26.1.x
✔️ Java 21+

## Upgrading

1. Stop your server.
2. Replace the old jar in `plugins/` with this release.
3. Start your server — config keys and stored preferences migrate automatically on first load.

> **Migration notes:**
> - `defaults.bundle_inventory` and `defaults.bundle_others` in `config.yml` are rewritten to `defaults.bundle_in_inventory` / `defaults.bundle_in_containers` automatically.
> - Players who had `ClickMethod.NONE` stored will have their preference migrated to `SWAP` + `enabled: false` automatically.
> - Permission-plugin configurations referencing `clicksorted.commands.reload|getcfg|debug|benchmark` should be updated to `clicksorted.admin.commands.reload|config|debug|benchmark`.

## What's Changed

- Add per-player bundle blacklist (GUI + commands) by @kccricket
- Add admin item blacklist via config and permission nodes by @kccricket
- Add admin slot locks via config and permission nodes by @kccricket
- Replace ClickMethod.NONE with dedicated enabled preference by @kccricket
- Resolve item_name data component in name-based operations by @kccricket
- Enforce master kill-switch on all player-facing commands by @kccricket
- Add MigrationException for graceful config reload failure by @kccricket
- Fix: place bundle-displaced items into free slots instead of dropping by @kccricket
- Fix: guard against NPE when bundle contains items with no loose counterpart by @kccricket
- Reorganize command tree by domain by @kccricket
- Integration test sweep by @kccricket in #55
- Simplify pass: extract MaterialNameSet and PdcStringSet by @kccricket in #56

---

# ClickSorted 1.2.1

Release date: 2026-06-23

ClickSorted 1.2.1 fixes bundle packing silently ignoring dyed bundles as pack targets.

## Bug Fixes

- **Colored bundles not recognized as pack bins** — `BundlePacker` now matches all bundle color variants (e.g. `RED_BUNDLE`, `BLUE_BUNDLE`) when scanning for pack targets, not just `Material.BUNDLE`. Dyed bundles in an inventory were passed over as bins and left empty during a bundle-packing sort.

## Compatibility

✔️ Paper/Folia 1.21.5 – 26.1.x
✔️ Java 21+

## Upgrading

1. Stop your server.
2. Replace the old jar in `plugins/` with this release.
3. Start your server.

## What's Changed

- Fix bundle packing to match all bundle color variants by @kccricket

---

# ClickSorted 1.2.0

Release date: 2026-06-22

ClickSorted 1.2.0 adds a treemap sort method, configurable sort direction, an automatic update check, and fixes several sorting correctness bugs including an item-loss edge case.

## Highlights

- **Treemap sort** — a new `TREEMAP` sort method groups each item type into its own contiguous near-square block, sized to that type's stack count. The most numerous types claim the largest rectangles; smaller types fill in beside or below them; and when the inventory is nearly full, remaining types degrade to a gap-free linear fill so no slots are wasted.
- **Sort direction** — each player can now choose which corner items are placed from (`TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`) and whether rows or columns fill first (`HORIZONTAL` / `VERTICAL`), via `/clicksorted set start-corner` and `/clicksorted set fill-axis`. Both settings affect treemap placement as well as the linear sorts.
- **Automatic update check** — on startup and after `/clicksorted reload`, the plugin queries the Modrinth API and logs a console notice if a newer release exists. Can be disabled with `check_for_updates: false`.
- **Item-loss fix** — enchantments and other `ItemMeta` fields are now included in the sort-key merge-equality check, so identically typed but differently enchanted items are never merged.
- **Hover coupled to click method** — `single_click` now forces sort-over-items off (it only ever triggers on an empty slot); `control_drop` forces it on. Switching click method adjusts the hover preference automatically, and trying to override it manually shows a clear message.

## Breaking Changes

- **Minimum server version is now Paper/Folia 1.21.5**

## New Features

### Treemap sort method

`/clicksorted set sort-method treemap` selects the new `TREEMAP` mode. Unlike `NAME` (linear alphabetical) and `GROUP` (linear by creative-tab bucket), `TREEMAP` assigns each item type a shelf-packed, contiguous near-square rectangle in the inventory grid. Types are ranked largest-first so the biggest stacks dominate the most visible area. The rectangle's shape is chosen to minimise wasted cells while staying close to square; types that can no longer fit cleanly fall back to filling free cells in reading order, so a nearly-full inventory degrades gracefully to a gap-free fill. `TREEMAP` is always available — no `groups.yml` required. The `start_corner` and `fill_axis` preferences (see below) control which corner the largest block anchors to and whether shelves of blocks grow horizontally or vertically.

### Sort direction

Two new per-player preferences control how sorted items are written back into the inventory grid (for linear sorts) and how treemap shelves are oriented:

- `/clicksorted set start-corner <TOP_LEFT|TOP_RIGHT|BOTTOM_LEFT|BOTTOM_RIGHT>` — which corner items are placed from (default `TOP_LEFT`).
- `/clicksorted set fill-axis <HORIZONTAL|VERTICAL>` — whether rows (`HORIZONTAL`) or columns (`VERTICAL`) fill first (default `HORIZONTAL`).

Server admins set defaults with `defaults.start_corner` and `defaults.fill_axis` in `config.yml`. The new `GridGeometry` / `SlotOrder` / `TreemapPacker` classes implement the layout internally.

### Automatic update check

On startup and after `/clicksorted reload`, `UpdateChecker` fires an async Modrinth API query. If a newer release version is available, two INFO-level lines are logged to the console with the version number and download links. Failures degrade silently to a debug log. Disable entirely with `check_for_updates: false` in `config.yml`.

### Preference repair

A "preference repair" procedure runs on player join and resets any per-player PDC value that can no longer be parsed as its expected type (e.g. a stale enum name from an old version). Each reset preference sends the player a chat notice (`prefResetInvalid` in `lang.yml`) naming the preference, the bad value, and the new default.

## Bug Fixes

- **Item loss from same-material stacks with differing meta** — Merge equality now includes `ItemMeta`, so enchanted and plain tools are never collapsed together. Previously, merging a stack of stackable items with different enchantments could silently discard the extras. [PR #46]
- **Lifted stack not re-deposited on `DOUBLE_CLICK`** — a double-click sort left the item on the cursor unreturned to the inventory if the click initiated the sort. The stack is now placed back before writing sorted results.
- **`SINGLE_CLICK` sort could fire over an occupied slot** — the sort-over-items gate now enforces that `single_click` only triggers on an empty slot unconditionally (hover is forced off for this method).
- **Sort grid origin not row-aligned** — now aligns the sort origin to the first full row of the inventory, preventing partial-row layouts at the top of large containers.
- **Real chest grids and fill-axis not honoured** — the slot-order implementation now correctly reflects actual chest grid dimensions and respects the fill-axis setting end-to-end. [PR #46]
- **`Log` not null-safe before `onEnable`** — the logger handle is now checked for null before early log calls during plugin load.

## Other Improvements

- Deduplicated enum/boolean PDC accessors in `PlayerSortingPrefs` using a shared accessor pattern; `sortable_inventories` is now backed by an `EnumSet`.
- `GridGeometry` is now the single authoritative source for the mount-slot offset used in player inventory layout.
- New `lang.yml` keys: `setStartCornerTo`, `setFillAxisTo`, `hoverForcedByClickMethod`, `hoverGovernedByClickMethod`, `prefResetInvalid`, `invalidValue`, `statusStartCorner`, `statusFillAxis`.
- New `lang.yml` key: `prefix` — a configurable MiniMessage string prepended to every status, error, and alert message sent to players. Defaults to `<gray>[<aqua>ClickSorted<gray>] `. Set to `""` to disable. Lock-GUI item names are not affected.
- Unified command-feedback messaging: all player-facing `set …`/status/reload messages were reworded for a consistent voice and now default to white text. `/clicksorted set …` subcommands also send a clear `invalidValue` error when an argument can't be parsed, naming the bad value and listing valid options (previously invalid input was silently ignored). [PR #47]
- Treemap grouping refined: durable items get their own block per meta variant while non-durable items (including bundles) group by material, with a stable within-block stack order (enchanted books ordered by primary enchantment), and rectangle selection now prefers squareness over minimal waste. [PR #48]
- README and CLAUDE.md updated for sort direction, the update check, hover coupling, preference repair, and the new config/lang keys.

## Compatibility

✔️ Paper/Folia 1.21.5 – 26.1.x
✔️ Java 21+

## Upgrading

1. Stop your server.
2. Replace the old jar in `plugins/` with this release.
3. Start your server.

> Note: The two new per-player preferences (`start_corner`, `fill_axis`) are read from `config.yml` defaults if a player has never set them. No manual migration is needed.

## What's Changed

- Add Modrinth-based update check by @kccricket in #45
- Add TREEMAP sort method, sort direction (start corner + fill axis), and treemap layout engine by @kccricket in #46
- Unify command feedback messages and add configurable `prefix` by @kccricket in #47
- Refine treemap grouping/ordering so bundles and meta variants pack correctly by @kccricket in #48
- Couple hover preference to click method, repair invalid prefs by @kccricket
- Fix item loss from same-material stacks with differing ItemMeta by @kccricket
- Fix re-deposit of lifted stack on DOUBLE_CLICK sort by @kccricket
- Fix SINGLE_CLICK sort over occupied slots by @kccricket
- Fix sort grid alignment and chest grid layout by @kccricket
- Dedupe enum/boolean accessors and EnumSet sortables by @kccricket

---

# ClickSorted 1.1.1

Release date: 2026-06-14

ClickSorted 1.1.1 adds explicit Folia support, hardens shared state for Folia's per-region worker threads, and fixes sorting from inside ClickSorted's own GUIs.

## Highlights

- **Explicit Folia support** — the plugin now declares `folia-supported` and makes all shared mutable state safe for Folia's per-region worker threads.
- **Lock GUI no longer sortable** — sort triggers inside ClickSorted's own GUIs are now ignored unconditionally, so a click in the lock GUI can no longer rearrange its panes or the inventory beneath it.
- **Atomic action throttle** — the per-player throttle window is now updated atomically, closing a race under concurrent clicks.

## Bug Fixes

- ClickSorted's own GUIs are never sorted — the lock GUI is a CHEST-type inventory that matched the sortable set, so a sort-trigger click inside it could rearrange its panes before the GUI listener cancelled the interaction. A `ClickSortedHolder` marker interface now makes the sort listener bail on any ClickSorted GUI, independent of `ignore_plugin_inventory`.
- The action throttle window is now atomic, preventing a race when a player's clicks arrive concurrently.

## Other Improvements

- Hardened shared mutable state for Folia: `CooldownMessenger`'s cooldown map is now a `ConcurrentHashMap` (keyed by player UUID), `GroupsConfig` mappings are built locally and swapped atomically on reload, and `ItemsConfig`/`MainConfig` reload swaps are published via `volatile`.
- Dropped the obsolete `SWAP_OFFHAND` offhand-reset workaround — modern Paper's inventory ack/sequence system reconciles the client prediction, so cancelling the event suffices. This removes the plugin's only `getScheduler()` call; an integration test pins the contract that a SWAP-triggered sort leaves the offhand untouched across a tick.
- Replaced `plugin.yml` with `paper-plugin.yml`.
- Avoided `InventoryView` method calls from plugin bytecode in `LockGuiListener` (using `InventoryEvent.getInventory()`), preventing `IncompatibleClassChangeError` across 1.20.6/1.21.
- `MessageUtil` now takes an Adventure `Audience` instead of `CommandSender`, making the Adventure routing explicit and allowing any audience to be passed.
- Made item-name lookup a pure read and removed test-only public methods with no production callers.
- Added a GitHub Actions CI workflow that runs the test suite on PRs (Java 25, matching the Paper API requirement).

## Compatibility

✔️ Paper/Folia 1.20.6 – 26.1.x
✔️ Java 21+

## Upgrading

1. Stop your server.
2. Replace the old jar in `plugins/` with this release.
3. Start your server.

## What's Changed

- Support Folia explicitly and harden cross-thread/version safety by @kccricket in #43
- Never sort ClickSorted's own GUIs by @kccricket
- Make the action throttle window atomic by @kccricket
- Convert `plugin.yml` to `paper-plugin.yml` by @kccricket
- Optimize Adventure usage in `MessageUtil` by @kccricket in #41
- Add GitHub Actions CI workflow to run tests on PRs by @kccricket in #42

---

# ClickSorted 1.1.0

Release date: 2026-06-13

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
✔️ Java 21+

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
✔️ Java 21+

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