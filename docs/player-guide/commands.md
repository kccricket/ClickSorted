# Player Commands

These commands are available to every player by default.

## Sorting

| Command | Description |
|---|---|
| `/clicksorted` | Toggle click-sorting on or off. |
| `/clicksorted sort enabled [yes\|no]` | Toggle (or explicitly set) click-sorting on or off. |
| `/clicksorted sort method <name\|group\|treemap>` | Set your sort order. `group` is only available when `groups.yml` is configured. |
| `/clicksorted sort start-corner <TOP_LEFT\|TOP_RIGHT\|BOTTOM_LEFT\|BOTTOM_RIGHT>` | Set which corner sorted items are placed from. |
| `/clicksorted sort fill-axis <HORIZONTAL\|VERTICAL>` | Set whether sorted items fill rows first (`HORIZONTAL`) or columns first (`VERTICAL`). |
| `/clicksorted status` | Show your current settings (enabled state, click method, sort method, start corner, fill direction, sort-over-items, bundle packing). |
| `/clicksorted menu` | Open the [preferences menu](preferences-menu.md) — every sorting preference on one screen. |

## Click trigger

| Command | Description |
|---|---|
| `/clicksorted click method <swap\|single_click\|double_click\|control_drop\|shift_left_click\|shift_right_click>` | Set your sort trigger. |
| `/clicksorted click allow-on-hover [yes\|no]` | Toggle (or explicitly set) whether sorting fires while hovering an occupied slot. Some click methods govern this automatically. |

## Slot locking

| Command | Description |
|---|---|
| `/clicksorted lock-slots` | Open the slot-lock GUI to lock or unlock individual inventory slots. |

## Bundle packing

| Command | Description |
|---|---|
| `/clicksorted bundle enabled [yes\|no]` | Toggle bundle packing for both your inventory and containers. |
| `/clicksorted bundle enabled in-inventory <yes\|no>` | Toggle bundle packing when sorting your own inventory. |
| `/clicksorted bundle enabled in-containers <yes\|no>` | Toggle bundle packing when sorting containers (chests, barrels, …). |
| `/clicksorted bundle stack-limit <n\|off>` | Max distinct item entries packed per bundle (`off` = weight-only limit). |
| `/clicksorted bundle blacklist` | Open the bundle blacklist GUI. |
| `/clicksorted bundle blacklist add material <material>` | Add a material to your bundle blacklist. |
| `/clicksorted bundle blacklist add item-name <text>` | Add a display name to your bundle blacklist. |
| `/clicksorted bundle blacklist remove material <material>` | Remove a material from your bundle blacklist. |
| `/clicksorted bundle blacklist remove item-name <text>` | Remove a display name from your bundle blacklist. |
| `/clicksorted bundle blacklist list` | List all blacklisted materials and display names. |
| `/clicksorted bundle blacklist clear` | Clear your entire bundle blacklist. |
