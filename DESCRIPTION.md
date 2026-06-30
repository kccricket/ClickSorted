# ClickSorted

Sort any inventory — and pack your bundles — with a mouse click.

ClickSorted lets players sort their player inventory, chests, ender chests, shulker boxes, barrels, hoppers, and more with a single mouse gesture. You can lock individual slots so they're left untouched, and optionally pack loose items into bundles as you sort. Preferences are saved per-player and persist across sessions.

## Quick start

Out of the box: open any inventory and press **F** (swap-offhand) over any slot to sort it. No configuration required.

Tune it to taste with commands:

- `/clicksorted set click-method <swap|single_click|double_click|control_drop|shift_left_click|shift_right_click|none>` — choose your sort trigger.
- `/clicksorted set sort-method <name|group|treemap>` — choose the sort order (treemap packs each item type into its own near-square block).
- `/clicksorted set start-corner <…>` / `set fill-axis <horizontal|vertical>` — choose which corner items fill from and which way.
- `/clicksorted set lock` — open a GUI to lock slots that should never be sorted.
- `/clicksorted set bundle <inventory|others> on` — pack partial stacks into your bundles while sorting.
- `/clicksorted status` — see your current settings.

Fully customisable item groups, messages, and sortable inventory types are available for admins.

## Requirements

- Paper 1.21.5+
- No dependencies

## Documentation

Full admin and player documentation is in the **[README](https://github.com/kccricket/ClickSorted/blob/release/README.md)**.

---

*ClickSorted is a modernised Paper-API fork of the original [ClickSort](https://dev.bukkit.org/projects/clicksort) by Des Herriott (desht). GPL v3.*
