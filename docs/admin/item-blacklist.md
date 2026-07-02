# Admin Item Blacklist

Admins can prevent ClickSorted from ever touching specific items — they are never sorted, moved, or packed/unpacked regardless of player preferences. Two enforcement channels are unioned and both checked at sort time.

## Config channel

Add entries to `config.yml` under `blacklist.materials` and `blacklist.names`. These rules apply to every player server-wide.

- `materials` — a list of Bukkit Material names (case-insensitive, exact match).
- `names` — a list of plain-text display names matched case-insensitively against the item's resolved name (custom name → `item_name` data component → vanilla name).

Unknown material names are warned and skipped on load/reload.

```yaml
blacklist:
  materials:
    - nether_star
    - totem_of_undying
  names:
    - "Creative Menu"
```

## Permission channel

Grant dynamic permission nodes via a permissions plugin (e.g. LuckPerms):

- `clicksorted.blacklist.material.<material>` — e.g. `clicksorted.blacklist.material.nether_star`
- `clicksorted.blacklist.name.<slug>` — e.g. `clicksorted.blacklist.name.creative_menu`

**Name slug rule:** strip legacy `§X` color codes → lowercase (`Locale.ROOT`) → collapse non-`[a-z0-9]` runs to `_` → strip edge underscores. For example, `"Creative Menu"` → `creative_menu`.

Both channels use `isPermissionSet` before `hasPermission` so OPs without an explicit grant are unaffected.

This blacklist is admin-only and not exposed to players via any command or GUI. For player-controlled bundle exclusions, see [Bundle packing](../player-guide/bundle-packing.md).
