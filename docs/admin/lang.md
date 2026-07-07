# Localisation & lang overrides

All player-facing messages are rendered with **[MiniMessage](https://docs.advntr.dev/minimessage)**. You may use any MiniMessage formatting tags (`<red>`, `<bold>`, `<#ff0000>`, `<gradient:...>`, etc.).

## How message resolution works

ClickSorted ships its default English strings **inside the jar**, not as a file in your data folder. A changed default in a new release takes effect for every server automatically, with no action required.

To customize a message, create a **sparse override file** at `plugins/ClickSorted/lang/<locale>.yml`, where `<locale>` is a lowercase Minecraft-style locale token — `en_us.yml`, `de_de.yml`, `pt_br.yml`, or any other locale your players use. A language-only filename (`de.yml`) is also honored, as a fallback tier below the full `lang_country` token. Only add the keys you actually want to change; every key you omit keeps resolving to the plugin's internal default, so your customizations survive future releases that change unrelated strings.

A commented copy of every available key is written to `plugins/ClickSorted/lang/en_us.yml` the first time the plugin starts, as documentation — every line is prefixed with `# ` so the file overrides nothing until you uncomment and edit a line. Changes to an override file take effect after `/clicksorted admin reload`.

**Per-player locale:** each player's client locale (e.g. `en_us`, `de_de`) is used to pick which override/internal file applies, so different players can see different languages once translations exist. The resolution order for a player's locale token, its language-only token, and the server's configured `default_locale` (in `config.yml`, default `en_us`) is:

```
override[token] → override[lang] → override[default_locale]
  → internal[token] → internal[lang] → internal[default_locale] → internal[en_us]
  → (missing) a visible "[missing lang key: ...]" placeholder, logged as an error
```

`default_locale` also selects the language used for console output and any non-player command sender.

**Adding a translation:** create `plugins/ClickSorted/lang/<locale>.yml` with just the keys you're translating (or a full copy — the per-key/placeholder tables below list every available key). No other locales need to be present, and no code changes are required — a new locale file is a pure drop-in.

**If a `lang.yml` file is present** in your data folder, it is migrated automatically the next time the server starts: any key whose value differs from the bundled English default is copied into `plugins/ClickSorted/lang/en_us.yml` as an override, and `lang.yml` is renamed to `lang.yml.bak` (kept for reference, no longer read). Keys left at their default are dropped, so you immediately get the current release's default wording for anything you didn't customize; genuine customizations are preserved.

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
