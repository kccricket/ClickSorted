# ClickSorted 2.2.0

ClickSorted 2.2.0 adds a dialog-based preferences UI, per-player localisation with self-updating default strings, and raises the minimum supported server to Paper 1.21.6.

## Highlights

- **New `/clicksorted menu`** — a single Paper Dialog listing every sorting preference (enabled, click/sort method, start corner, fill axis, allow-on-hover, bundle in-inventory/in-containers, bundle stack-limit) with current values pre-filled, plus buttons to launch the slot-lock and bundle-blacklist GUIs without leaving the flow. The existing `/clicksorted sort`/`click`/`bundle`/`status` commands are unchanged and remain the console/scripting interface.
- **Per-player localisation** — every message now resolves against the viewing/target player's Minecraft client locale, with a sparse-override model so default text updates automatically without shadowing admin customizations. See below.

## Breaking Changes

- **Minimum supported server raised to Paper 1.21.6** (from 1.21.5), the version that introduced the Dialog API this release's preferences menu is built on. A 1.21.5 server can no longer run this build — upgrade Paper first.
- **`lang.yml` is replaced by `lang/<locale>.yml` override files** (e.g. `lang/en_us.yml`). An existing `lang.yml` is migrated automatically on first load: any key you had customized is extracted into `lang/en_us.yml`, the old file is archived to `lang.yml.bak`, and any key left at its default is dropped (so you pick up any improved default text in this release). No action is required. See [`docs/admin/lang.md`](docs/admin/lang.md).

## New Features

### Dialog-based preferences menu

`/clicksorted menu` (permission `clicksorted.commands.menu`, default `true`) opens a single form covering every scalar sorting preference, each shown only if the player holds the matching command's permission node, so the dialog can never change something the player couldn't already change by command. Saving applies every changed value through the same `PlayerSortingPrefs` setters the commands use, so listener vetoes and PDC persistence behave identically. The "Locked Slots…" and "Bundle Blacklist…" buttons preserve any unsaved edits made in the dialog, open the corresponding GUI, and restore the dialog with those edits intact once the GUI is closed.

Bedrock/Geyser clients cannot render server-side dialogs; the existing commands remain their path to the same preferences.

### Per-player localisation & self-updating lang defaults

Default message strings are now bundled inside the jar (`lang/en_us.yml`) rather than copied onto disk, so a changed default in a future release takes effect for everyone automatically — no more permanently-shadowed `lang.yml`. Admins customize strings via sparse override files at `plugins/ClickSorted/lang/<locale>.yml`; only the keys you edit are overridden, everything else keeps resolving to the built-in default. Messages are now resolved per-player against their Minecraft client locale, so future translation files (`de_de.yml`, `pt_br.yml`, …) are a pure drop-in with no code changes. A new `default_locale` config key (default `en_us`) controls console output and the fallback for any locale with no matching file. See [`docs/admin/lang.md`](docs/admin/lang.md) for the full resolution order.

## Other Improvements

- README and documentation updated for the new command and the 1.21.6 floor.

---

# ClickSorted 2.1.0

Release date: 2026-07-03

ClickSorted 2.1.0 makes sorting and preference changes fully listener-actionable for third-party plugins, adds a configurable update-check cadence, and closes a Folia edge case around shared virtual inventories.

## Highlights

- **`InventorySortEvent` is now listener-actionable** — narrow a sort with `excludeSlot`/`excludeItem`, inspect precise slot classification (`OUT_OF_RANGE`/`ADMIN_LOCKED`/`USER_LOCKED`/`SORTABLE`), and explain a cancellation with `setCancelReason`, shown to the player rate-limited.
- **New `PlayerPreferenceChangeEvent`** — every per-player preference change (click/sort method, start corner, fill axis, `enabled`, sort-over-items, bundle-packing settings, locked-slot toggles, bundle blacklist add/remove/clear) now fires a cancellable, typed event before it's applied or persisted.
- **New `PreferenceResult`** — every event-firing `PlayerSortingPrefs` setter now returns `APPLIED`/`UNCHANGED`/`CANCELLED` instead of a bare `boolean`/`void`, so callers can tell a real no-op apart from a listener veto.
- **Configurable update-check cadence** — `check_for_updates_interval_hours` (default 24) controls how often the recurring Modrinth check repeats while the server runs.
- **Folia safety** — on Folia, ClickSorted now refuses to sort a plugin-created inventory that is open to more than one viewer at once (e.g. a shared virtual GUI), since such an inventory has no single owning region thread and could otherwise race between viewers' region threads. Vanilla inventories and single-viewer plugin GUIs are unaffected.

## Breaking Changes

These changes are scoped to third-party plugin developers integrating with ClickSorted's Java API — there is no impact on server admins or players.

- **`PlayerSortingPrefs` setter return types changed.** Methods like `setEnabled`, `setClickMethod`, `setSortingMethod`, `setStartCorner`, `setFillAxis`, `setSortOverItems`, `setBundlePackInInventory`, `setBundlePackInContainers`, `setBundleStackLimit`, `addToBundleBlacklist`/`removeFromBundleBlacklist` (and the name variants), `clearBundleBlacklist`, and `toggleSlotLocked` now return `PreferenceResult` instead of `boolean`/`void`. Third-party plugins calling these directly and using the return value must update to check `PreferenceResult.applied()`/`cancelled()`. `setLockedSlots` is unchanged (still `void`, fires no event).
- **`InventorySortEvent(InventoryView, Inventory, int min, int max)` is deprecated** (not removed) in favor of the new set-based constructor, which carries lock and admin-blacklist metadata the old one can't express. Existing code using the old constructor keeps compiling and working.

## New Features

### `InventorySortEvent`: listener-actionable

The event now carries the click's full region, per-player and admin lock sets, and the admin item-blacklist snapshot, and exposes:

- `getSortableSlots()` / `excludeSlot(int)` — read and narrow the live sortable set.
- `getRegionSlots()`, `getUserLockedSlots()`, `getAdminLockedSlots()` — the raw inputs behind the sortable set.
- `statusOf(int)` / `getSlots()` — classify any or every slot as `OUT_OF_RANGE`, `ADMIN_LOCKED`, `USER_LOCKED`, or `SORTABLE`.
- `excludeItem(Material)` / `excludeItem(String)` / `getExcludedMaterials()` / `getExcludedItemNames()` / `matchesExcludedItem(ItemStack)` — listener-contributed item exclusions, unioned with the admin blacklist.
- `setCancelReason(Component)` / `getCancelReason()` — an optional explanation shown to the player (rate-limited, since this event fires on every matching click) when a listener cancels the sort.

See the new [Development → Events](development/events/inventory-sort-event.md) docs page for full details and an example listener.

### `PlayerPreferenceChangeEvent`

A new cancellable event fired by `PlayerSortingPrefs` before any per-player preference changes. `getChange()` returns a sealed `Change<T>` (`ValueChange<T>` or `LockedSlotChange`, which adds a `slot()`) with typed `oldValue()`/`newValue()`; `getChange(Preference<T>)` gives a type-narrowed accessor for one specific preference (`Preference.CLICK_MODE`, `.SORT_MODE`, `.ENABLED`, etc. — see the new `Preference<T>` class). A cancelling listener may call `setCancelReason(Component)`; ClickSorted's own commands show that reason, falling back to a generic `preferenceChangeBlocked` lang key when none is set. Only fires when the new value actually differs from the old one.

See the new [Development → Events](development/events/player-preference-change-event.md) docs page for full details and an example listener.

### `PreferenceResult`

`PlayerSortingPrefs` mutators now return a `PreferenceResult` record (`APPLIED`, `UNCHANGED`, or `CANCELLED` with an optional `cancelReason()`), letting ClickSorted's commands (and any third-party caller) distinguish a value that was already set from one a listener blocked, and report each correctly to the player.

### Configurable update-check cadence

`check_for_updates_interval_hours` (default `24`, minimum `1`, lower values clamped up) controls how often `UpdateChecker` repeats its Modrinth check while the server keeps running, in addition to the existing startup/reload check. `/clicksorted admin reload` now restarts the full update-check schedule (previously it only fired a one-off check).

### Development documentation

New `docs/development/` section covering building & testing from source and the events ClickSorted publishes (`InventorySortEvent`, `PlayerPreferenceChangeEvent`), for plugin authors integrating with ClickSorted.

## Bug Fixes

- **Bundle-blacklist add/remove/clear couldn't distinguish "already in that state" from "a listener blocked it"** — these now report a listener veto as blocked rather than silently falling through to the "already present"/"not present" message.

## Other Improvements

- `ClickSortedCommands` deduplicated: `blockedAndReported`/`reportBlacklistResult` helpers own the cancel-report half of every preference-setting command once, instead of per call site.
- `PlayerSortingPrefs` deduplicated: enum/boolean setters delegate to shared `setEnumPref`/`setBoolPref` helpers, and the set-valued blacklist mutators to `mutateBlacklist`, which own the fire→write→return sequence once; set-valued stores re-read PDC after the event fires so re-entrant listener mutations aren't clobbered.
- A no-op preference setter call that matches the config default still pins the value in PDC (without firing the event), so an explicit choice survives a later default change.
- `ProtectedItems.blocks(ItemStack, String)` overload added so callers that already resolved an item's display name (e.g. `InventorySortEvent.matchesExcludedItem`) avoid a second lookup.

## Compatibility

✔️ Paper/Folia 1.21.5 – 26.1.x
✔️ Java 21+

## Upgrading

1. Stop your server.
2. Replace the old jar in `plugins/` with this release.
3. Start your server. `check_for_updates_interval_hours` is added to `config.yml` automatically (default `24`); no other config or stored-preference migration is required.

> **Note for plugin developers:** if you call `PlayerSortingPrefs` setters directly and use their return value, update to the new `PreferenceResult` return type (see Breaking Changes above).

## What's Changed

- Add `InventorySortEvent` slot classification, item exclusions, and cancel reasons by @kccricket
- Add configurable update-check cadence and cancellable preference events by @kccricket
- Introduce `PreferenceResult` to distinguish no-op, applied, and cancelled preference changes by @kccricket
- Fix: distinguish cancelled bundle-blacklist changes from no-ops; dedupe/cache event helpers by @kccricket
- Refactor: pin explicit preference no-ops to PDC and dedupe command/event helpers by @kccricket
- Refuse to sort cross-region-unsafe shared plugin inventories on Folia by @kccricket

---

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