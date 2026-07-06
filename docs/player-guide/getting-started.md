# Getting Started

> **Tip:** every preference on this page (and the rest of the player guide) can also be set from one screen with `/clicksorted menu` — see [Preferences Menu](preferences-menu.md).

## Sorting an inventory

By default, sorting is triggered by pressing the **swap-offhand key** (usually `F`) while hovering over any empty slot in a sortable inventory. Open a chest, your ender chest, a shulker box — any sortable container — hover any empty slot, and press `F` (or whatever your offhand swap key is).

The available trigger modes are:

| Trigger mode | How to sort |
|---|---|
| **swap** *(default)* | Hover over any slot and press the swap-offhand key (`F`, usually). |
| **double_click** | Double-click any slot. |
| **single_click** | Left-click an **empty** slot. |
| **control_drop** | Press the drop key (`Ctrl+Q`) over a slot. |
| **shift_left_click** | Shift-left-click a slot. |
| **shift_right_click** | Shift-right-click a slot. |

Change your trigger with `/clicksorted click method <mode>`.

To disable click-sorting entirely, run `/clicksorted sort enabled no`, or just run `/clicksorted` to toggle.

## Bundle packing

ClickSorted can also move loose items into bundles to most efficiently use your bundle space. See [Bundle Packing](bundle-packing.md) for details.

Sorting and bundle packing can be enabled or disabled independently — you can sort without packing, pack without sorting, or use both together.

## Sorting over occupied slots

By default a sort only fires when your cursor is over an **empty slot**, so your normal clicks on items are never hijacked. To have your trigger fire even while hovering an occupied slot, run `/clicksorted click allow-on-hover` to toggle "sort over items" on (and again to turn it off). When enabled, your original click or button press is suppressed so the item underneath isn't picked up or moved.

> **Note:** Some click methods govern this automatically. `single_click` forces `allow-on-hover` to be off (otherwise you couldn't use your inventory), and `control_drop` forces it to be on (the game ignores CTRL-Q over empty slots).

## Your player inventory: two regions

When you sort your own inventory, the **main inventory** (slots 9–35) and the **hotbar** (slots 0–8) are treated as two independent regions — each sorts within itself. Trigger a sort while hovering a slot in the main area to sort the main area; hover a hotbar slot to sort the hotbar.
