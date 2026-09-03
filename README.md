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
- Per-player localisation with self-updating default messages — admin customizations are sparse overrides that never shadow a future default-text improvement

## Quick start

Out of the box: open any inventory and press **F** (swap-offhand) over any empty slot to sort the inventory. No configuration required.

Tune it to taste with commands:

- `/clicksorted click method <…>` — choose your sort trigger.
- `/clicksorted sort method <…>` — choose the sort order.
- `/clicksorted sort start-corner <…>` / `sort fill-axis <…>` — choose which corner items fill from and which way.
- `/clicksorted sort enabled` — enable or disable sorting on click.
- `/clicksorted lock-slots` — open a GUI to lock slots that should never be sorted.
- `/clicksorted bundle enabled` — enabled or disable packing stacks into your bundles on click.
- `/clicksorted bundle blacklist` — open a GUI to exclude specific item types or display names from bundle packing.
- `/clicksorted status` — see your current settings.
- `/clicksorted menu` — open a single dialog listing every sorting preference at once, with buttons to launch the slot-lock and bundle-blacklist GUIs. Requires Paper's Dialog API (1.21.6+) and is unavailable on older servers or Bedrock/Geyser clients — use the commands above instead.

Fully customisable item groups, messages, and sortable inventory types are available for admins. Admins can also declare a server-wide item blacklist (items that are never sorted or packed) and lock specific player inventory slots across all players.

Messages are stored as sparse per-locale override files under `plugins/ClickSorted/lang/` (e.g. `en_us.yml`); anything you don't override keeps resolving to the plugin's built-in default, so a new release's improved wording reaches you automatically. Existing `lang.yml` files are migrated to this format automatically on first load. See [`docs/admin/lang.md`](docs/admin/lang.md).

## Requirements

- Paper or Folia 1.20.6+ (the `/clicksorted menu` dialog UI additionally requires 1.21.6+)
- No dependencies

## Documentation

Full admin and player documentation is at **[kccricket.github.io/ClickSorted](https://kccricket.github.io/ClickSorted/)**.

---

*ClickSorted is a modernised Paper-API fork of the original [ClickSort](https://dev.bukkit.org/projects/clicksort) by Des Herriott (desht). GPL v3.*
