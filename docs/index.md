# ClickSorted

*Sort any inventory and pack your bundles with a click.*

ClickSorted turns any inventory into a tidy one with a single click. Open a chest, your own inventory, an ender chest, a shulker box and push a button to collapse loose items into merged, ordered stacks. Every player picks their own trigger, sort order, and extras — their choices stick across sessions. Beyond plain sorting, it can lock slots you want left untouched and pack stray remainders into your bundles, so a single action both sorts and consolidates.

## Features

- Sort player inventories, chests, ender chests, shulker boxes, barrels, hoppers, droppers, dispensers, and any other configured inventory type.
- Three sort orders: **name** (alphabetical by display name), **group** (creative-tab-style buckets defined in `groups.yml`), and **treemap** (each item type fills its own contiguous near-square block, sized to its stack count).
- **Configurable sort direction** — each player chooses which corner items are placed from and whether rows or columns fill first.
- **Lock individual inventory slots** so they are never moved by sorting.
- **Optional bundle packing** — consolidate partial stacks into bundles as part of a sort, with a configurable per-bundle entry limit.
- **Per-player bundle blacklist** — exclude specific item types or display names from bundle packing via a GUI or commands.
- **Admin item blacklist** — server admins can declare materials and display names that ClickSorted will never sort, move, or pack.
- **Admin slot locks** — server admins can lock specific player inventory slots server-wide via `config.yml` or permission nodes.
- Per-player preferences persist across sessions.
- Identical items are automatically merged into full stacks before sorting.
- A per-player action throttle caps how fast scripted clients can drive plugin work.
- Automatic update check at startup via Modrinth.
- Fully configurable: messages (MiniMessage), item groups, sortable inventory types, and more.

## Installation

**Requirements:** Paper or Folia 1.21.5 or newer. No other plugins required.

1. Download `ClickSorted.jar` from [Releases](https://github.com/kccricket/clicksorted/releases).
2. Drop the JAR into your server's `plugins/` folder.
3. Restart or reload your server.

The bundled `groups.yml` was generated from Minecraft 26.1.2 creative tabs.

## Acknowledgements

ClickSorted is a Paper/Folia fork of the original **[ClickSort](https://dev.bukkit.org/projects/clicksort)** plugin by **Des Herriott (desht)**, with contributions from **chengzi**. The original plugin made inventory sorting delightful; this fork updates it to use the Paper API.

> Original ClickSort © Des Herriott — licensed under the GNU GPL v3. ClickSorted retains that licence.
