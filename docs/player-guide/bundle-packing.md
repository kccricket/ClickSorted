# Bundle Packing

If you keep empty or partly-filled **bundles** in an inventory, ClickSorted can fold the loose odds-and-ends of each item type into them as part of a sort — reclaiming slots without shuffling items by hand.

Bundle packing is off by default. You can turn it on for all containers, or only your inventory or other containers:

| Command | Effect |
|---|---|
| `/clicksorted bundle enabled` | Toggle bundle packing on/off. |
| `/clicksorted bundle enabled yes` | Enable packing for both your inventory and containers. |
| `/clicksorted bundle enabled in-inventory yes` | Pack while sorting your own inventory. |
| `/clicksorted bundle enabled in-containers yes` | Pack while sorting containers you open. |

When packing, each item type's full stacks stay loose in the inventory — only the leftover remainder is bundled, and only when it's an efficient trade. You can cap how many distinct item types land in a single bundle:

`/clicksorted bundle stack-limit <n|off>`

The default is `12`, which prevents the number of item stacks in the bundle from exceeding the bundle's preview limit. Set it to `off` to remove the stack count limit.


## Bundle blacklist

You can exclude specific item types or display names from bundle packing. Blacklisted items are never packed into or unpacked from bundles, and blacklisted bundle colors are never used as packing targets.

### Via GUI

Run `/clicksorted bundle blacklist` to open the blacklist GUI.

- **Click an item in your real inventory** — adds it to the blacklist by material (for vanilla items) or by display name (for custom-named or data-pack-named items).
- **Click a listed entry in the GUI** — removes it from the blacklist.

### Via commands

| Command | Description |
|---|---|
| `/clicksorted bundle blacklist add material <material>` | Add a material to your blacklist. |
| `/clicksorted bundle blacklist add item-name <text>` | Add a display name to your blacklist (case-insensitive). |
| `/clicksorted bundle blacklist remove material <material>` | Remove a material from your blacklist. |
| `/clicksorted bundle blacklist remove item-name <text>` | Remove a display name from your blacklist. |
| `/clicksorted bundle blacklist list` | List all blacklisted materials and display names. |
| `/clicksorted bundle blacklist clear` | Clear your entire blacklist (materials and display names). |
