# Sort Configuration

ClickSorted offers three sort methods. Players set their preference with `/clicksorted sort method <name|group|treemap>`; admins set the default with [`defaults.sort_mode`](configuration.md).

## Name sort

Items are sorted **alphabetically by display name**. Custom names (set via anvil or data pack) take priority. For standard items with no custom name, the Bukkit Material enum name is used as the sort key (e.g. `OAK_LOG`, `DIAMOND_SWORD`) unless an override is defined in `items.yml`.

### Customising display names (`items.yml`)

`items.yml` ships empty. Add entries to give standard materials human-readable sort labels:

```yaml
DIAMOND: "Diamond"
OAK_LOG: "Oak Log"
```

The Material keys (left side) are Bukkit enum names. The display-name values (right side) control how the item sorts under the NAME method. For example, setting `DIRT: "Dirt (Terrible)"` sorts dirt under `T`.

## Treemap sort

Items are grouped by type and each type is assigned its own **contiguous near-square block** in the inventory grid, sized in proportion to that type's stack count. The largest type claims the biggest block and anchors to the `start_corner`; the `fill_axis` setting controls whether shelves of blocks grow horizontally or vertically. When the inventory is nearly full and clean rectangles no longer fit, smaller types degrade to a gap-free linear fill so the inventory stays tightly packed.

`TREEMAP` is always available — no `groups.yml` setup required.

## Group sort

Items are sorted into **named buckets** defined in `groups.yml`, then alphabetically by display name within each bucket. The group sort order is purely alphabetical by group key — so the bundled groups use a numeric prefix (e.g. `010-building-blocks`, `020-colored-blocks`) to control their display order. Items not listed in any group fall into the `default_group_name` bucket (`000-default` by default), which sorts first because `0` precedes `1` alphabetically.

`GROUP` sort is only selectable when `groups.yml` defines at least one mapping.

### `groups.yml` structure

```yaml
# Group name → list of Bukkit Material enum names (case-insensitive).
# Unknown material names are logged as warnings and skipped.

010-building-blocks: [ oak_log, oak_wood, stripped_oak_log, ... ]
020-colored-blocks:  [ white_wool, orange_wool, ... ]
# etc.
```

Each key is a group name mapped to an inline list of [Bukkit Material](https://jd.papermc.io/paper/1.21.5/org/bukkit/Material.html) enum names.

### Default groups (bundled)

The bundled `groups.yml` was generated from Minecraft 26.1.2 creative tabs.

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

### Adding or editing groups

To create a new group or move items between groups, edit `groups.yml` and run `/clicksorted admin reload`:

```yaml
# Example: a custom "my-valuables" group that sorts after the defaults
110-my-valuables: [ diamond, emerald, netherite_ingot, ancient_debris ]
```

To add items to an existing group, find the group key and append the material names to its list.
