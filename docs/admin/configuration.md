# Configuration (`config.yml`)

All options with their defaults. Changes take effect after `/clicksorted admin reload` (except `enable_metrics`, which requires a restart).

```yaml
# Enables bStats anonymous usage metrics. Set to false to opt out.
enable_metrics: true

# Check Modrinth for a newer published release on startup and after reload.
# A notice is logged to the console if an update is available. Set to false to disable.
check_for_updates: true

# How often to repeat the update check while the server keeps running, in hours.
# Enforced minimum is 1 hour; lower values are clamped up. No effect when check_for_updates is false.
check_for_updates_interval_hours: 24

# Runtime log verbosity. Valid values: OFF, DEBUG, TRACE
# Can also be changed live with /clicksorted admin debug without editing this file.
debug_level: 'OFF'

# When a sort produces more items than fit in the available slots:
#   true  → overflow items are dropped on the ground (players are notified).
#   false → the sort is aborted entirely and the player is notified.
drop_excess: true

# The group name assigned to any item not listed in groups.yml.
# Uses a numeric prefix so it sorts to the front of the group list alphabetically.
default_group_name: '000-default'

# When true, only inventories held by vanilla Bukkit classes are sortable —
# custom plugin GUIs (shop menus, etc.) are excluded even if their InventoryType
# appears in sortable_inventories below.
ignore_plugin_inventory: false

# Per-player preference defaults. Players can override these with commands;
# their choices are stored persistently.
defaults:
  # Whether click-sorting is enabled. Toggle with /clicksorted or /clicksorted sort enabled.
  enabled: true
  # Sort trigger. Values: SWAP, SINGLE_CLICK, DOUBLE_CLICK, CONTROL_DROP,
  #                       SHIFT_LEFT_CLICK, SHIFT_RIGHT_CLICK
  click_mode: SWAP
  # Sort order. Values: NAME, GROUP, TREEMAP
  sort_mode: NAME
  # Corner of the inventory grid where sorted items are placed.
  # Values: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
  start_corner: TOP_LEFT
  # Whether rows or columns fill first from the start corner.
  # Values: HORIZONTAL (row-by-row), VERTICAL (column-by-column)
  fill_axis: HORIZONTAL
  # When true, the trigger also fires while hovering an occupied slot;
  # when false, sorting only fires on an empty slot. Toggle with /clicksorted click allow-on-hover.
  sort_over_items: false
  # When true, sorting a player's OWN inventory also packs partial stacks into any bundles there.
  bundle_in_inventory: false
  # When true, sorting a container (chest, barrel, …) also packs partial stacks into its bundles.
  bundle_in_containers: false
  # Default max distinct item entries packed per bundle. 12 = bundle tooltip-preview limit;
  # 0 disables the entry cap (weight-only limit applies instead).
  bundle_stack_limit: 12

# Player inventory slots (0–35) that are always excluded from sorting and cannot be
# toggled by the player in the lock GUI. 0–8 = hotbar, 9–35 = main inventory.
# Out-of-range values are warned and skipped. Also enforceable per-player via
# the clicksorted.lock.player.slot.<n> permission node.
locked_slots:
  player: []

# Items that ClickSorted will never sort, move, or pack/unpack for any player.
# materials: list of Bukkit Material names (exact, case-insensitive).
# names: list of plain-text display-name substrings to match case-insensitively.
# Unknown material names are warned and skipped on load/reload.
# Also enforceable per-player via clicksorted.blacklist.material.<m> and
# clicksorted.blacklist.name.<slug> permission nodes.
blacklist:
  materials: []
  names: []

# Minimum milliseconds between successive ClickSorted actions per player (sorting, bundle
# packing, lock-GUI toggles, commands). Caps how fast a scripted client can spam these;
# players with clicksorted.throttle.bypass (default op) are exempt. Set to 0 to disable.
action_cooldown_ms: 150

# List of Bukkit InventoryType names whose containers respond to click-sorting.
# Remove a type to prevent sorting in that container family.
# Full list of valid InventoryType names: https://jd.papermc.io/paper/1.21.5/org/bukkit/event/inventory/InventoryType.html
sortable_inventories:
  - "PLAYER"
  - "CHEST"
  - "CHEST_MINECART"
  - "ENDER_CHEST"
  - "SHULKER_BOX"
  - "BARREL"
  - "HOPPER"
  - "HOPPER_MINECART"
  - "DROPPER"
  - "DISPENSER"
```
