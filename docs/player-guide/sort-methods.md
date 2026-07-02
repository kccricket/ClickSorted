# Sort Methods

Change your sort method with `/clicksorted sort method <name|group|treemap>`.

## Name

Items are sorted **alphabetically by display name**. Custom names (set via anvil or data pack) take priority. For standard items, the sort key is the item's material name unless the server admin has configured display-name overrides.

This is the default sort method.

## Group

Items are sorted into **named buckets** (building blocks, food, combat, etc.), then alphabetically within each bucket. The server controls which items belong to which group and what order the groups appear in.

`GROUP` is only available when your server has groups configured. If it's not available, you'll see a notice when you try to select it.

## Treemap

Each item type claims its own **contiguous near-square block** in the inventory grid, sized in proportion to how many of that item you have. Items you have more of get bigger blocks. When the inventory is very full, smaller types fall back to a compact linear fill to avoid wasting space.

`TREEMAP` is always available.
