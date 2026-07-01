# ClickSorted

[![License](https://img.shields.io/badge/license-GPLv3-blue.svg)](LICENSE.md)
[![Latest release](https://img.shields.io/github/v/release/kccricket/ClickSorted)](https://github.com/kccricket/ClickSorted/releases)
[![Build status](https://img.shields.io/github/actions/workflow/status/kccricket/ClickSorted/ci.yml?branch=develop)](https://github.com/kccricket/ClickSorted/actions/workflows/ci.yml)

Sort any inventory — and pack your bundles — with a mouse click.

ClickSorted lets players sort their player inventory, chests, ender chests, shulker boxes, barrels, hoppers, and more with a single mouse gesture. You can lock individual slots so they're left untouched, optionally pack loose items into bundles as you sort, and control exactly which item types land in your bundles with a per-player blacklist. Preferences are saved per-player and persist across sessions.

- Three sort orders — name, group, or treemap (near-square blocks per item type)
- Configurable fill direction — choose the starting corner and whether rows or columns fill first
- Lock individual slots so sorting leaves them alone
- Optional bundle packing, with a per-player blacklist for item types or display names
- Server admins can enforce a global item blacklist and lock specific player slots

## Quick start

Out of the box: open any inventory and press **F** (swap-offhand) over any slot to sort it. No configuration required.

Tune it to taste with commands:

- `/clicksorted click method <swap|single_click|double_click|control_drop|shift_left_click|shift_right_click>` — choose your sort trigger.
- `/clicksorted sort method <name|group|treemap>` — choose the sort order (treemap packs each item type into its own near-square block).
- `/clicksorted sort start-corner <…>` / `sort fill-axis <horizontal|vertical>` — choose which corner items fill from and which way.
- `/clicksorted sort enabled [yes|no]` — enable or disable click-sorting without changing your trigger.
- `/clicksorted lock-slots` — open a GUI to lock slots that should never be sorted.
- `/clicksorted bundle in-inventory yes` / `bundle in-containers yes` — pack partial stacks into your bundles while sorting.
- `/clicksorted bundle blacklist` — open a GUI to exclude specific item types or display names from bundle packing.
- `/clicksorted status` — see your current settings.

Fully customisable item groups, messages, and sortable inventory types are available for admins. Admins can also declare a server-wide item blacklist (items that are never sorted or packed) and lock specific player inventory slots across all players.

## Requirements

- Paper 1.21.5+
- No dependencies

## Documentation

Full admin and player documentation is at **[kccricket.github.io/ClickSorted](https://kccricket.github.io/ClickSorted/)**.

---

*ClickSorted is a modernised Paper-API fork of the original [ClickSort](https://dev.bukkit.org/projects/clicksort) by Des Herriott (desht). GPL v3.*
