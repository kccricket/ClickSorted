# Localisation (`lang.yml`)

All player-facing messages are stored in `lang.yml` and rendered with **[MiniMessage](https://docs.advntr.dev/minimessage)**. You may use any MiniMessage formatting tags (`<red>`, `<bold>`, `<#ff0000>`, `<gradient:...>`, etc.). Changes take effect after `/clicksorted admin reload`.

> **Placeholders:** Tags like `<method>`, `<status>`, `<instruction>`, and `<level>` are filled in at runtime. Do not remove a placeholder from a message that requires it.

## Message prefix

| Key | Default value | Description |
|---|---|---|
| `prefix` | `<gray>[<aqua>ClickSorted<gray>] ` | Prepended to every status, error, and alert message sent to players. Set to `""` to send messages without a prefix. GUI item names are not affected. |

## Enabled toggle

| Key | Default text | Placeholders |
|---|---|---|
| `setEnabledStatus` | `Click-sorting is now <status> for you.` | `<status>` — `ENABLED` or `DISABLED` |

## Sort order

| Key | Default text | Placeholders |
|---|---|---|
| `setSortingMethodTo` | `Your sort mode is now set to: <method>.` | `<method>` — the new sort mode name |
| `sortingMethodNotAvailable` | `Sort mode <method> is not available — configure groups.yml first.` | `<method>` |

## Click trigger

| Key | Default text | Placeholders |
|---|---|---|
| `setClickMethodTo` | `Your click mode is now set to: <method>. <instruction>` | `<method>`, `<instruction>` |

## Sort direction

| Key | Default text | Placeholders |
|---|---|---|
| `setStartCornerTo` | `Your sort start corner is now set to: <corner>.` | `<corner>` — the new corner value |
| `setFillAxisTo` | `Your sort fill direction is now set to: <axis>.` | `<axis>` — the new fill axis value |

## Sort-over-items toggle

| Key | Default text | Placeholders |
|---|---|---|
| `setSortOverItemsStatus` | `Sorting while hovering over an item is now <status> for you.` | `<status>` — `ENABLED` or `DISABLED` |
| `hoverForcedByClickMethod` | `Sorting while hovering over an item is now <status> for you because your click mode is <method>.` | `<status>`, `<method>` |
| `hoverGovernedByClickMethod` | `Sorting while hovering over an item is restricted by your click mode (<method>) and can't be changed.` | `<method>` |

## Bundle packing

| Key | Default text | Placeholders |
|---|---|---|
| `setBundlePackEnabledStatus` | `Bundle packing is now <status> for you (inventory and containers).` | `<status>` — `ENABLED` or `DISABLED` |
| `setBundlePackInInventoryStatus` | `Bundle packing in your inventory is now <status>.` | `<status>` — `ENABLED` or `DISABLED` |
| `setBundlePackInContainersStatus` | `Bundle packing in other containers is now <status>.` | `<status>` — `ENABLED` or `DISABLED` |
| `setBundleStackLimitStatus` | `Your bundle stack limit is now <limit>.` | `<limit>` — entry count, or `off` for weight-only |

## Bundle blacklist

| Key | Default text | Placeholders |
|---|---|---|
| `setBundleBlacklistAdded` | `<material> added to your bundle blacklist.` | `<material>` |
| `setBundleBlacklistAlreadyPresent` | `<material> is already in your bundle blacklist.` | `<material>` |
| `setBundleBlacklistRemoved` | `<material> removed from your bundle blacklist.` | `<material>` |
| `setBundleBlacklistNotPresent` | `<material> is not in your bundle blacklist.` | `<material>` |
| `setBundleBlacklistMaterialsList` | `Blacklisted materials: <list>.` | `<list>` — comma-separated material names |
| `setBundleBlacklistEmpty` | `Your bundle blacklist is empty.` | — |
| `setBundleBlacklistCleared` | `Your bundle blacklist has been cleared.` | — |
| `setBundleBlacklistNameAdded` | `Display name <name> added to your bundle blacklist.` | `<name>` |
| `setBundleBlacklistNameAlreadyPresent` | `Display name <name> is already in your bundle blacklist.` | `<name>` |
| `setBundleBlacklistNameRemoved` | `Display name <name> removed from your bundle blacklist.` | `<name>` |
| `setBundleBlacklistNameNotPresent` | `Display name <name> is not in your bundle blacklist.` | `<name>` |
| `setBundleBlacklistNamesList` | `Blacklisted display names: <list>.` | `<list>` — comma-separated display names |

## Preference repair

| Key | Default text | Placeholders |
|---|---|---|
| `prefResetInvalid` | `Your saved <pref> value, <value>, was invalid and has been reset to <default>.` | `<pref>`, `<value>`, `<default>` |

## Status command

| Key | Default text | Placeholders |
|---|---|---|
| `statusEnabled` | `Click-sorting: <status>` | `<status>` — `ENABLED` or `DISABLED` |
| `statusClickMethod` | `Click method: <method>` | `<method>` |
| `statusSortMethod` | `Sort method: <method>` | `<method>` |
| `statusStartCorner` | `Start corner: <corner>` | `<corner>` |
| `statusFillAxis` | `Fill direction: <axis>` | `<axis>` |
| `statusHover` | `Sort over items: <status>` | `<status>` — `ENABLED` or `DISABLED` |
| `statusBundleInInventory` | `Bundle (inventory): <status>` | `<status>` — `ENABLED` or `DISABLED` |
| `statusBundleInContainers` | `Bundle (containers): <status>` | `<status>` — `ENABLED` or `DISABLED` |
| `statusBundleStackLimit` | `Bundle stack limit: <limit>` | `<limit>` — integer, or `off` |
| `statusBundleBlacklistMaterials` | `Bundle blacklist (materials): <list>` | `<list>` — comma-separated material names |
| `statusBundleBlacklistNames` | `Bundle blacklist (names): <list>` | `<list>` — comma-separated display-name entries |
| `statusBundleBlacklistEmpty` | `Bundle blacklist: (empty)` | — |

## GUI elements

### Shared

| Key | Default text | Description |
|---|---|---|
| `guiFillerName` | `--------` | Display name for inert black-pane filler slots in all ClickSorted GUIs. |
| `guiHelpBookName` | `Instructions` | Display name for the help/instructions book item in all ClickSorted GUIs. |

### Blacklist GUI

| Key | Default text | Description |
|---|---|---|
| `blacklistGuiTitle` | `Bundle Blacklist` | Title of the bundle blacklist GUI. |
| `blacklistEntryLore` | `Click to remove.` | Lore shown on every blacklist entry (material and name). |
| `blacklistHelpBookLore` | *(see lang.yml)* | Instructions shown on the help book in the blacklist GUI. |
| `blacklistArrowPrev` | `Previous page` | Label for the previous-page arrow item. |
| `blacklistArrowNext` | `Next page` | Label for the next-page arrow item. |

### Lock GUI

| Key | Default text | Placeholders |
|---|---|---|
| `lockGuiTitle` | `Inventory Sorting Locks` | — |
| `lockPaneUnlocked` | `Unlocked` | — |
| `lockPaneLocked` | `Locked` | — |
| `lockPaneAdmin` | `Server Locked` | — |
| `lockPaneSlotInventory` | `Inventory slot <number>` | `<number>` — slot number (1–27) |
| `lockPaneSlotHotbar` | `Hotbar slot <number>` | `<number>` — slot number (1–9) |
| `lockPaneUnlockedLore` | `Click to lock this slot.` | — |
| `lockPaneLockedLore` | `Click to unlock this slot.` | — |
| `lockPaneAdminLore` | `This slot is locked by the server and cannot be changed.` | — |
| `lockHelpHeadLore` | `Locked inventory slots will not be sorted and bundles in a locked slot will not be packed.` | — |

## Errors and system messages

### Invalid argument

| Key | Default text | Placeholders |
|---|---|---|
| `invalidValue` | `<red>Invalid value '<value>'. Valid values: <valid>.` | `<value>` — the text the player typed; `<valid>` — comma-joined valid options |

### Action throttle

| Key | Default text | Placeholders |
|---|---|---|
| `actionTooFast` | `<red>Slow down — you're acting too quickly.` | — |

### Debug commands

| Key | Default text | Placeholders |
|---|---|---|
| `setDebugLevelTo` | `Debug level is now temporarily set to <level>.` | `<level>` |
| `invalidDebugLevel` | `Invalid debug level '<level>'. Valid values: OFF, DEBUG, TRACE.` | `<level>` |

### Reload

| Key | Default text | Placeholders |
|---|---|---|
| `configReloaded` | `Configuration reloaded.` | — |
| `configReloadFailed` | `<red>Configuration reload failed: <reason>. The previous configuration is still active — see the server console for the full stack trace.` | `<reason>` — short failure description |

### Inventory overflow

| Key | Default text | Placeholders |
|---|---|---|
| `invOverFlow` | `Inventory overflow — items were not sorted.` | — |
| `dropItems` | `Some items couldn't fit and were dropped!` | — |

### Sort trigger instructions

These strings are substituted as `<instruction>` in `setClickMethodTo`.

| Key | Default text |
|---|---|
| `instructionSingle` | `Left-click an empty slot to sort.` |
| `instructionDouble` | `Double-click any slot to sort.` |
| `instructionSwap` | `Press the offhand-swap key to sort.` |
| `instructionControlDrop` | `Press Ctrl+Q (drop key) over a slot to sort.` |
| `instructionShiftLeftClick` | `Shift-left-click a slot to sort.` |
| `instructionShiftRightClick` | `Shift-right-click a slot to sort.` |

### Console / player-only

| Key | Default text |
|---|---|
| `notFromConsole` | `This command can only be used by a player.` |
| `noPermission` | `<red>You don't have permission to do that.` |
