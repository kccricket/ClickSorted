# ClickSorted

*Sort any inventory with a click.*

<!-- TODO: replace placeholder badge URLs once distribution pages are live
![Version](https://img.shields.io/badge/version-TODO-blue)
![License](https://img.shields.io/badge/license-GPL%20v3-green)
![Paper](https://img.shields.io/badge/Paper-1.20.6%2B-orange)
![Build](https://img.shields.io/badge/build-TODO-lightgrey)
-->
---

## Contents

- [ClickSorted](#clicksorted)
  - [Contents](#contents)
  - [Acknowledgements](#acknowledgements)
  - [Features](#features)
  - [Download \& Installation](#download--installation)
  - [Player Guide](#player-guide)
    - [Sorting an inventory](#sorting-an-inventory)
    - [Changing preferences in-inventory (shift-click)](#changing-preferences-in-inventory-shift-click)
    - [Your player inventory: two regions](#your-player-inventory-two-regions)
    - [Locking slots](#locking-slots)
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
      - [Shift-click toggle feedback](#shift-click-toggle-feedback)
      - [Debug commands](#debug-commands)
      - [Reload](#reload)
      - [Inventory overflow](#inventory-overflow)
      - [Sort trigger instructions](#sort-trigger-instructions)
      - [Shift-cycle prompts](#shift-cycle-prompts)
      - [Tips](#tips)
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
- Per-player preferences (trigger, sort order, shift-cycling, locked slots) persist across sessions.
- Change preferences entirely **in-inventory with the mouse** — commands are optional.
- Identical items are automatically merged into full stacks before sorting.
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

There are four available trigger modes:

| Trigger mode | How to sort |
|---|---|
| **swap** *(default)* | Hover over any slot and press the swap-offhand key (`F`). |
| **double** | Double-click any slot. |
| **single** | Left-click an **empty** slot. |
| **none** | Click-sorting disabled. |

### Changing preferences in-inventory (shift-click)

You don't need to type any commands. While an inventory is open, shift-click an **empty slot**:

| Gesture | Effect |
|---|---|
| **Shift + left-click** an empty slot | Cycle sort order: **name → group → …** |
| **Shift + right-click** an empty slot | Cycle trigger: **double → single → swap → none** |

> **Note:** These gestures only activate on empty slots, so normal shift-clicking to move items is unaffected — *unless* you shift-click an empty slot, in which case the cycle fires instead. If this gets in the way, run `/clicksorted shiftclick` to disable it and use commands instead.

### Your player inventory: two regions

When you sort your own inventory, the **main inventory** (slots 9–35, excluding hotbar and armor) and the **hotbar** (slots 0–8) are treated as two independent regions — each sorts within itself. Trigger a sort while your cursor is in the main area to sort the main area; hover a hotbar slot and trigger to sort the hotbar.

*(Admins can adjust which slot range counts as "main" — see [`player_sort_min` / `player_sort_max`](#configyml) below.)*

### Locking slots

Run `/clicksorted lock` to open the slot-lock GUI. The top of the screen shows a grid of glass panes mirroring your inventory — three rows for the main inventory and one row for the hotbar, separated by a divider.

- **Lime pane** — slot is unlocked and will be sorted normally.
- **Barrier icon** — slot is locked and will be skipped by sorting.

Click any pane to toggle its state. Changes are saved immediately. Close the GUI when done — your locks are active right away.

Locked slots apply only to your own player inventory (both the main region and the hotbar). Container inventories (chests, barrels, etc.) are always sorted in full.

### Player commands

All four commands are available to every player by default.

| Command | Description |
|---|---|
| `/clicksorted sort <name\|group>` | Set your sort order. `group` is only available when `groups.yml` is configured. |
| `/clicksorted click <swap\|single\|double\|none>` | Set your sort trigger. |
| `/clicksorted shiftclick` | Toggle in-inventory shift-click mode-cycling on or off. |
| `/clicksorted lock` | Open the slot-lock GUI to lock or unlock individual inventory slots. |

## Admin Reference

### Admin commands

These commands require the `clicksorted.commands.*` op permissions (see [Permissions](#permissions) below).

| Command | Description |
|---|---|
| `/clicksorted reload` | Reload all config files (`config.yml`, `groups.yml`, `items.yml`, `lang.yml`) without a server restart. |
| `/clicksorted getcfg` | Print every `config.yml` key/value to the console or chat. |
| `/clicksorted debug [off\|debug\|trace]` | Set logging verbosity at runtime (not persisted to `config.yml`). With no argument, toggles between `off` and `debug`. |

### Permissions

| Permission node | Default | Description |
|---|---|---|
| `clicksorted.admin` | `op` | Grants all command permissions listed below. |
| `clicksorted.commands.reload` | `op` | Use `/clicksorted reload`. |
| `clicksorted.commands.getcfg` | `op` | Use `/clicksorted getcfg`. |
| `clicksorted.commands.debug` | `op` | Use `/clicksorted debug`. |
| `clicksorted.commands.sort` | `true` | Use `/clicksorted sort`. |
| `clicksorted.commands.click` | `true` | Use `/clicksorted click`. |
| `clicksorted.commands.shiftclick` | `true` | Use `/clicksorted shiftclick`. |
| `clicksorted.commands.lock` | `true` | Use `/clicksorted lock`. |
| `clicksorted.sort` | `true` | Master gate: allow a player to click-sort any inventory at all. Parent of the three nodes below. |
| `clicksorted.sort.player` | `true` | Allow sorting the player's own main inventory (excluding hotbar). |
| `clicksorted.sort.hotbar` | `true` | Allow sorting the player's hotbar. |
| `clicksorted.sort.container` | `true` | Allow sorting container inventories (chests, barrels, etc.). |

Out of the box, all players can click-sort everything and change their own preferences. Only ops can reload configs, dump config values, or change the debug level.

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

# Per-player preference defaults. Players can override these with commands or
# shift-click gestures; their choices are stored persistently.
defaults:
  # Sort trigger. Values: SWAP, SINGLE, DOUBLE, NONE
  click_mode: SWAP
  # Sort order. Values: NAME, GROUP
  sort_mode: NAME
  # Whether shift-click in-inventory mode cycling is enabled for new players.
  shift_click: true
  # Slot bounds of the player main-inventory sort region (inclusive lower, exclusive upper).
  # 9–36 covers the full main inventory (below hotbar, above armor/off-hand).
  # Set player_sort_min: 0 to include the hotbar in the same sort region,
  # but note that clicksorted.sort.hotbar controls whether the hotbar can be sorted at all.
  player_sort_min: 9
  player_sort_max: 36

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

#### Shift-click toggle feedback

| Key | Default text | Placeholders |
|---|---|---|
| `setShiftClickStatus` | `Shift-click mode cycling: <status>.` | `<status>` — `ENABLED` or `DISABLED` |

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
| `instructionDisabled` | `Click-sorting has been disabled.` |

#### Shift-cycle prompts

Shown periodically as tips alongside mode-cycle confirmations.

| Key | Default text |
|---|---|
| `shiftLeftToChange` | `Shift-left-click an empty slot to change.` |
| `shiftRightToChange` | `Shift-right-click an empty slot to change.` |

#### Tips

| Key | Default text |
|---|---|
| `tipToChangeMode` | `(Use <white>/clicksorted sort</white> and <white>/clicksorted click</white> to change modes)` |
| `tipToReEnable` | `Run <white>/clicksorted shiftclick</white> to re-enable.` |
| `tipToDisable` | `Run <white>/clicksorted shiftclick</white> to disable.` |

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
