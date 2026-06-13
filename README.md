# ClickSorted

<!-- TODO: replace placeholder badge URLs once distribution pages are live
![Version](https://img.shields.io/badge/version-TODO-blue)
![License](https://img.shields.io/badge/license-GPL%20v3-green)
![Paper](https://img.shields.io/badge/Paper-1.20.6%2B-orange)
![Build](https://img.shields.io/badge/build-TODO-lightgrey)
-->

*Sort any inventory and pack your bundles with a click.*

ClickSorted turns any inventory into a tidy one with a single click. Open a chest, your own inventory, an ender chest, a shulker box and push a button to collapse loose items into merged, ordered stacks. Every player picks their own trigger, sort order, and extras; their choices stick across sessions. Beyond plain sorting, it can lock slots you want left untouched and pack stray remainders into your bundles, so a single action both sorts and consolidates.

---

## Contents

- [ClickSorted](#clicksorted)
  - [Contents](#contents)
  - [Acknowledgements](#acknowledgements)
  - [Features](#features)
  - [Download \& Installation](#download--installation)
  - [Player Guide](#player-guide)
    - [Sorting an inventory](#sorting-an-inventory)
    - [Sorting over occupied slots](#sorting-over-occupied-slots)
    - [Your player inventory: two regions](#your-player-inventory-two-regions)
    - [Locking slots](#locking-slots)
    - [Bundle packing](#bundle-packing)
    - [Player commands](#player-commands)
  - [Admin Reference](#admin-reference)
    - [Admin commands](#admin-commands)
    - [Permissions](#permissions)
    - [`config.yml`](#configyml)
    - [Sort orders \& `groups.yml`](#sort-orders--groupsyml)
      - [Name sort](#name-sort)
      - [Group sort](#group-sort)
      - [`groups.yml` structure](#groupsyml-structure)
      - [Default groups (bundled)](#default-groups-bundled)
      - [Adding or editing groups](#adding-or-editing-groups)
    - [`items.yml`](#itemsyml)
    - [`lang.yml`](#langyml)
      - [Sort order feedback](#sort-order-feedback)
      - [Click trigger feedback](#click-trigger-feedback)
      - [Sort-over-items toggle feedback](#sort-over-items-toggle-feedback)
      - [Bundle packing feedback](#bundle-packing-feedback)
      - [Status command feedback](#status-command-feedback)
      - [Action throttle](#action-throttle)
      - [Debug commands](#debug-commands)
      - [Reload](#reload)
      - [Inventory overflow](#inventory-overflow)
      - [Sort trigger instructions](#sort-trigger-instructions)
      - [Console / player-only error](#console--player-only-error)
      - [Lock GUI](#lock-gui)
  - [Building from Source](#building-from-source)
  - [License](#license)

---

## Acknowledgements

ClickSorted is a Paper/Folia fork of the original **[ClickSort](https://dev.bukkit.org/projects/clicksort)** plugin by **Des Herriott (desht)**,
with contributions from **chengzi**. The original plugin made inventory sorting delightful; this fork updates it to use the Paper API.

> Original ClickSort © Des Herriott — licensed under the GNU GPL v3. ClickSorted retains that licence.

## Features

- Sort player inventories, chests, ender chests, shulker boxes, barrels, hoppers, droppers, dispensers, and any other configured inventory.
- Two sort orders: **name** (alphabetical by display name) and **group** (creative-tab-style buckets defined in `groups.yml`).
- **Lock individual inventory slots** so they are never moved by sorting.
- **Optional bundle packing**: consolidate partial stacks into bundles as part of a sort, with a configurable per-bundle entry limit.
- Per-player preferences (trigger, sort order, sort-over-items, bundle packing, locked slots) persist across sessions.
- Identical items are automatically merged into full stacks before sorting.
- A per-player action throttle caps how fast scripted clients can drive plugin work.
- Fully configurable: messages (MiniMessage), item groups, sortable inventory types, slot ranges, and more.

## Download & Installation

<!-- TODO: add Modrinth / SpigotMC / GitHub Releases links when available -->

1. Download `ClickSorted.jar` from [Releases](https://github.com/kccricket/clicksorted/releases).
2. Drop the JAR into your server's `plugins/` folder.
3. Restart or reload your server.

**Requirements:** Paper 1.20.6 or newer. No other plugins required.
The bundled `groups.yml` was generated from Minecraft 26.1.2 creative tabs.

## Player Guide

### Sorting an inventory

By default (and out of the box for new players), sorting is triggered by pressing the **swap-offhand key** (usually `F`) while hovering over any slot in a sortable inventory. Your main inventory, chests, ender chest, shulker box, barrel — just open it and press `F` over any slot.

The available trigger modes are:

| Trigger mode | How to sort |
|---|---|
| **swap** *(default)* | Hover over any slot and press the swap-offhand key (`F`). |
| **double_click** | Double-click any slot. |
| **single_click** | Left-click an **empty** slot. |
| **control_drop** | Press the drop key (`Ctrl+Q`) over a slot. |
| **shift_left_click** | Shift-left-click a slot. |
| **shift_right_click** | Shift-right-click a slot. |
| **none** | Click-sorting disabled. |

Change your trigger with `/clicksorted set click-method <mode>` and your sort order with `/clicksorted set sort-method <name\|group>`.

### Sorting over occupied slots

By default a sort only fires when your cursor is over an **empty slot**, so your normal clicks on items are never hijacked. If you'd rather have your trigger fire even while hovering an occupied slot, run `/clicksorted set hover` to toggle "sort over items" on (and again to turn it off, or pass an explicit `on`/`off`). When enabled, the originating click is suppressed so the item underneath isn't picked up or moved. Admins set the default with [`defaults.sort_over_items`](#configyml).

### Your player inventory: two regions

When you sort your own inventory, the **main inventory** (slots 9–35, excluding hotbar and armor) and the **hotbar** (slots 0–8) are treated as two independent regions — each sorts within itself. Trigger a sort while your cursor is in the main area to sort the main area; hover a hotbar slot and trigger to sort the hotbar.

*(Admins can adjust which slot range counts as "main" — see [`player_sort_min` / `player_sort_max`](#configyml) below.)*

### Locking slots

Run `/clicksorted set lock` to open the slot-lock GUI. The top of the screen shows a grid of glass panes mirroring your inventory — three rows for the main inventory and one row for the hotbar, separated by a divider.

- **Lime pane** — slot is unlocked and will be sorted normally.
- **Barrier icon** — slot is locked and will be skipped by sorting.
- **Black pane** — slot is outside the sortable range (see `player_sort_min`/`player_sort_max`) and is always excluded.

Click any pane to toggle its state. Changes are saved immediately. Close the GUI when done — your locks are active right away.

Locked slots apply only to your own player inventory (both the main region and the hotbar). Container inventories (chests, barrels, etc.) are always sorted in full.

### Bundle packing

If you keep empty or partly-filled **bundles** in an inventory, ClickSorted can fold the loose odds-and-ends of each item type into them as part of a sort — reclaiming slots without you shuffling items by hand. It's off by default; turn it on per region:

- `/clicksorted set bundle inventory on` — pack while sorting your own inventory.
- `/clicksorted set bundle others on` — pack while sorting containers you open.

When packing, each item type's full stacks stay loose in the inventory and only the leftover remainder is bundled (and only when it's an efficient trade). You can cap how many distinct item types land in a single bundle with `/clicksorted set bundle stacklimit <n>` — `12` matches the bundle tooltip preview; `off` removes the entry cap and uses only the weight limit. Run `/clicksorted set bundle` with no argument to see your current settings. Admins set the defaults with [`defaults.bundle_inventory` / `defaults.bundle_others` / `defaults.bundle_stack_limit`](#configyml).

### Player commands

These commands are available to every player by default.

| Command | Description |
|---|---|
| `/clicksorted set sort-method <name\|group>` | Set your sort order. `group` is only available when `groups.yml` is configured. |
| `/clicksorted set click-method <swap\|single_click\|double_click\|control_drop\|shift_left_click\|shift_right_click\|none>` | Set your sort trigger. |
| `/clicksorted set hover [on\|off]` | Toggle (or explicitly set) whether sorting fires while hovering an occupied slot (off = empty slots only). |
| `/clicksorted set lock` | Open the slot-lock GUI to lock or unlock individual inventory slots. |
| `/clicksorted set bundle` | Show your current bundle-packing settings. |
| `/clicksorted set bundle inventory <on\|off>` | Toggle bundle packing when sorting your **own** inventory. |
| `/clicksorted set bundle others <on\|off>` | Toggle bundle packing when sorting **containers** (chests, barrels, …). |
| `/clicksorted set bundle stacklimit <n\|off>` | Max distinct item entries packed per bundle (`off` = weight-only limit). |
| `/clicksorted status` | Show your current click method, sort method, and sort-over-items state. |

## Admin Reference

### Admin commands

These commands require the `clicksorted.commands.*` op permissions (see [Permissions](#permissions) below).

| Command | Description |
|---|---|
| `/clicksorted reload` | Reload all config files (`config.yml`, `groups.yml`, `items.yml`, `lang.yml`) without a server restart. |
| `/clicksorted getcfg` | Print every `config.yml` key/value to the console or chat. |
| `/clicksorted debug [off\|debug\|trace]` | Set logging verbosity at runtime (not persisted to `config.yml`). With no argument, toggles between `off` and `debug`. |
| `/clicksorted benchmark [iterations]` | Run an in-situ micro-benchmark of the sort and bundle-repack paths and report per-operation timings. Runs synchronously, briefly pausing the server. Default 2000 iterations (100–50000). |

### Permissions

| Permission node | Default | Description |
|---|---|---|
| `clicksorted.admin` | `op` | Grants all command permissions listed below, plus `clicksorted.throttle.bypass`. |
| `clicksorted.commands.reload` | `op` | Use `/clicksorted reload`. |
| `clicksorted.commands.getcfg` | `op` | Use `/clicksorted getcfg`. |
| `clicksorted.commands.debug` | `op` | Use `/clicksorted debug`. |
| `clicksorted.commands.benchmark` | `op` | Use `/clicksorted benchmark`. |
| `clicksorted.commands.sort` | `true` | Use `/clicksorted set sort-method`. |
| `clicksorted.commands.click` | `true` | Use `/clicksorted set click-method`. |
| `clicksorted.commands.hover` | `true` | Use `/clicksorted set hover`. |
| `clicksorted.commands.lock` | `true` | Use `/clicksorted set lock`. |
| `clicksorted.commands.bundle` | `true` | Use `/clicksorted set bundle`. |
| `clicksorted.commands.status` | `true` | Use `/clicksorted status`. |
| `clicksorted.throttle.bypass` | `op` | Exempt from the per-player action throttle (`action_cooldown_ms`). |
| `clicksorted.sort` | `true` | Master gate: allow a player to click-sort any inventory at all. Parent of the three nodes below. |
| `clicksorted.sort.player` | `true` | Allow sorting the player's own main inventory (excluding hotbar). |
| `clicksorted.sort.hotbar` | `true` | Allow sorting the player's hotbar. |
| `clicksorted.sort.container` | `true` | Allow sorting container inventories (chests, barrels, etc.). |

Out of the box, all players can click-sort everything and change their own preferences. Only ops can reload configs, dump config values, change the debug level, run the benchmark, or bypass the action throttle.

### `config.yml`

All options with their defaults, explained. Changes take effect after `/clicksorted reload` (except `enable_metrics`, which requires a restart).

```yaml
# Enables bStats anonymous usage metrics. Set to false to opt out.
enable_metrics: true

# Runtime log verbosity. Valid values: OFF, DEBUG, TRACE
# Can also be changed live with /clicksorted debug without editing this file.
debug_level: OFF

# When a sort produces more items than fit in the available slots:
#   true  → overflow items are dropped on the ground (players are notified).
#   false → the sort is aborted entirely and the player is notified.
drop_excess: true

# The group name assigned to any item not listed in groups.yml.
# Uses a numeric prefix so it sorts to the front of the group list alphabetically.
default_group_name: '000-default'

# When true, only inventories held by vanilla Bukkit classes are sortable —
# custom plugin GUIs (shop menus, etc.) are excluded even if their InventoryType
# appears in sortable_inventories below.
ignore_plugin_inventory: false

# Per-player preference defaults. Players can override these with commands;
# their choices are stored persistently.
defaults:
  # Sort trigger. Values: SWAP, SINGLE_CLICK, DOUBLE_CLICK, CONTROL_DROP,
  #                       SHIFT_LEFT_CLICK, SHIFT_RIGHT_CLICK, NONE
  click_mode: SWAP
  # Sort order. Values: NAME, GROUP
  sort_mode: NAME
  # When true, the trigger also fires while hovering an occupied slot;
  # when false, sorting only fires on an empty slot. Toggle with /clicksorted set hover.
  sort_over_items: false
  # When true, sorting a player's OWN inventory also packs partial stacks into any bundles there.
  bundle_inventory: false
  # When true, sorting a container (chest, barrel, …) also packs partial stacks into its bundles.
  bundle_others: false
  # Default max distinct item entries packed per bundle. 12 = bundle tooltip-preview limit;
  # 0 disables the entry cap (weight-only limit applies instead).
  bundle_stack_limit: 12

# Slot bounds of the player main-inventory sort region (inclusive lower, exclusive upper).
# 9–36 covers the full main inventory (below hotbar, above armor/off-hand).
# Set player_sort_min: 0 to include the hotbar in the same sort region,
# but note that clicksorted.sort.hotbar controls whether the hotbar can be sorted at all.
player_sort_min: 9
player_sort_max: 36

# Minimum milliseconds between successive ClickSorted actions per player (sorting, bundle
# packing, lock-GUI toggles, commands). Caps how fast a scripted client can spam these;
# players with clicksorted.throttle.bypass (default op) are exempt. Set to 0 to disable.
action_cooldown_ms: 150

# List of Bukkit InventoryType names whose containers respond to click-sorting.
# Remove a type to prevent sorting in that container family.
# Full list of valid InventoryType names: https://jd.papermc.io/paper/1.21.5/org/bukkit/event/inventory/InventoryType.html
sortable_inventories:
  - "PLAYER"
  - "CHEST"
  - "CHEST_MINECART"
  - "ENDER_CHEST"
  - "SHULKER_BOX"
  - "BARREL"
  - "HOPPER"
  - "HOPPER_MINECART"
  - "DROPPER"
  - "DISPENSER"
```

### Sort orders & `groups.yml`

#### Name sort

Items are sorted **alphabetically by display name** (the English vanilla client name, or the custom name on renamed items). Display names are cached in `items.yml` as the server encounters items; no setup needed.

#### Group sort

Items are sorted into **named buckets** defined in `groups.yml`, then alphabetically by name within each bucket. The group sort order is purely alphabetical by the group's key name — so the bundled groups use a numeric prefix (e.g. `010-building-blocks`, `020-colored-blocks`) to control their display order. Items not listed in any group fall into the `default_group_name` bucket (`000-default` by default), which sorts first because `0` precedes `1` alphabetically.

`GROUP` sort is only selectable when `groups.yml` defines at least one mapping.

#### `groups.yml` structure

```yaml
# Group name → list of Bukkit Material enum names (case-insensitive).
# Unknown material names are logged as warnings and skipped.

010-building-blocks: [ oak_log, oak_wood, stripped_oak_log, ... ]
020-colored-blocks:  [ white_wool, orange_wool, ... ]
# etc.
```

Each key is a group name mapped to an inline list of [Bukkit Material](https://jd.papermc.io/paper/1.21.5/org/bukkit/Material.html) enum names.

#### Default groups (bundled)

| Group key | Contents |
|---|---|
| `010-building-blocks` | Logs, planks, stairs, slabs, fences, stone and deepslate variants, copper blocks, etc. |
| `020-colored-blocks` | Wool, carpet, terracotta, concrete, stained glass, shulker boxes, beds, candles, banners — all dye colours. |
| `030-natural-blocks` | Dirt, ores, leaves, saplings, flowers, crops, corals, mushrooms, etc. |
| `040-functional-blocks` | Torches, lamps, crafting/utility blocks, chests, signs, heads, decorative blocks. |
| `050-redstone` | Redstone components, pistons, rails, minecarts, doors, trapdoors, observers, etc. |
| `060-tools-and-utilities` | Tools, buckets, boats, music discs, and miscellaneous utility items. |
| `070-combat` | Swords, axes, armour sets, bows, crossbows, arrows, shields, potions, totems. |
| `080-food-and-drinks` | Foods, cooked meats, stews, honey, etc. |
| `090-ingredients` | Ores, ingots, dyes, banner patterns, pottery sherds, smithing templates, enchanted books. |
| `100-spawn-eggs` | Spawner, trial spawner, and all mob spawn eggs. |

#### Adding or editing groups

To create a new group or move items between groups, edit `groups.yml` and run `/clicksorted reload`:

```yaml
# Example: a custom "my-valuables" group that sorts after the defaults
110-my-valuables: [ diamond, emerald, netherite_ingot, ancient_debris ]
```

To add items to an existing group, find the group key and append the material names to its list.

### `items.yml`

`items.yml` is a **runtime-generated cache** that maps Bukkit Material names to display names, used by the **name** sort order.

```yaml
# Example entries (auto-populated at runtime)
DIAMOND: "Diamond"
OAK_LOG: "Oak Log"
```

- The file is automatically populated as items are encountered and is saved to disk on server shutdown.
- You may **edit the display-name values** (the right side of each entry) to customise how items are labelled for name sorting. For example, renaming `DIRT` to `"Dirt (Terrible)"` will sort it under `T`.
- **Do not change the Material keys** (the left side) — they are Bukkit enum names.
- If you delete `items.yml` it will repopulate over time as players interact with inventories.

### `lang.yml`

All player-facing messages are stored in `lang.yml` and rendered with **[MiniMessage](https://docs.advntr.dev/minimessage)**. You may use any MiniMessage formatting tags (`<red>`, `<bold>`, `<#ff0000>`, `<gradient:...>`, etc.). Changes take effect after `/clicksorted reload`.

> **Placeholders:** Tags like `<method>`, `<status>`, `<instruction>`, and `<level>` are filled in at runtime. They are listed in the table below — do not remove a placeholder from a message that requires it.

#### Sort order feedback

| Key | Default text | Placeholders |
|---|---|---|
| `setSortingMethodTo` | `Sort mode set to <method>.` | `<method>` — the new sort mode name |
| `sortingMethodNotAvailable` | `Sort mode <method> is not available — configure groups.yml first.` | `<method>` |

#### Click trigger feedback

| Key | Default text | Placeholders |
|---|---|---|
| `setClickMethodTo` | `Click mode set to <method>. <instruction>` | `<method>`, `<instruction>` |

#### Sort-over-items toggle feedback

| Key | Default text | Placeholders |
|---|---|---|
| `setSortOverItemsStatus` | `Sort over items: <status>.` | `<status>` — `ENABLED` or `DISABLED` |

#### Bundle packing feedback

| Key | Default text | Placeholders |
|---|---|---|
| `setBundlePackInventoryStatus` | `Bundle packing in your inventory: <status>.` | `<status>` — `ENABLED` or `DISABLED` |
| `setBundlePackOthersStatus` | `Bundle packing in other containers: <status>.` | `<status>` — `ENABLED` or `DISABLED` |
| `setBundleStackLimitStatus` | `Bundle stack limit: <limit>.` | `<limit>` — entry count, or `off` for weight-only |

#### Status command feedback

| Key | Default text | Placeholders |
|---|---|---|
| `statusClickMethod` | `Click method: <method>` | `<method>` |
| `statusSortMethod` | `Sort method: <method>` | `<method>` |
| `statusHover` | `Sort over items: <status>` | `<status>` — `ENABLED` or `DISABLED` |

#### Action throttle

| Key | Default text | Placeholders |
|---|---|---|
| `actionTooFast` | `<red>Slow down — you're acting too quickly.` | — |

#### Debug commands

| Key | Default text | Placeholders |
|---|---|---|
| `setDebugLevelTo` | `Debug level is now <level>` | `<level>` |
| `invalidDebugLevel` | `Invalid debug level '<level>'. Valid values: OFF, DEBUG, TRACE.` | `<level>` |

#### Reload

| Key | Default text | Placeholders |
|---|---|---|
| `configReloaded` | `ClickSorted configuration reloaded.` | — |

#### Inventory overflow

| Key | Default text | Placeholders |
|---|---|---|
| `invOverFlow` | `Inventory overflow — items were not sorted.` | — |
| `dropItems` | `Some items couldn't fit and were dropped!` | — |

#### Sort trigger instructions

These strings are substituted as `<instruction>` in `setClickMethodTo`.

| Key | Default text |
|---|---|
| `instructionSingle` | `Left-click an empty slot to sort.` |
| `instructionDouble` | `Double-click any slot to sort.` |
| `instructionSwap` | `Press the offhand-swap key to sort.` |
| `instructionControlDrop` | `Press Ctrl+Q (drop key) over a slot to sort.` |
| `instructionShiftLeftClick` | `Shift-left-click a slot to sort.` |
| `instructionShiftRightClick` | `Shift-right-click a slot to sort.` |
| `instructionDisabled` | `Click-sorting has been disabled.` |

#### Console / player-only error

| Key | Default text |
|---|---|
| `notFromConsole` | `This command can only be used by a player.` |

#### Lock GUI

| Key | Default text | Placeholders |
|---|---|---|
| `lockGuiTitle` | `Slot Locks` | — |
| `lockPaneUnlocked` | `Unlocked` | — |
| `lockPaneLocked` | `Locked` | — |
| `lockPaneSlotInventory` | `Inventory slot <number>` | `<number>` — slot number (1–27) |
| `lockPaneSlotHotbar` | `Hotbar slot <number>` | `<number>` — slot number (1–9) |
| `lockPaneUnlockedLore` | `Click to lock this slot.` | — |
| `lockPaneLockedLore` | `Click to unlock this slot.` | — |
| `lockPaneUnsortable` | `Not sortable` | — |
| `lockPaneUnsortableLore` | `This slot is always excluded from sorting.` | — |
| `lockDividerName` | `--------` | — |
| `lockHelpHeadName` | `What is this?` | — |
| `lockHelpHeadLore` | `Locked inventory slots will not be sorted.` | — |

---

## Building from Source

```bash
git clone https://github.com/kccricket/clicksorted.git
cd clicksorted
./gradlew clean build
```

The output JAR will be at `build/libs/clicksorted-<version>.jar`.

To run the test suite:

```bash
./gradlew clean test
```

## License

ClickSorted retains the original **GNU GPL v3** license from the original ClickSort plugin by Des Herriott.
See [LICENSE](LICENSE) or the [full licence text](http://www.gnu.org/licenses/gpl-3.0.html).
