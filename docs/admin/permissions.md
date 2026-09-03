# Permissions

Out of the box, all players can click-sort everything and change their own preferences. Only ops can reload configs, print config values, change the debug level, run the benchmark, run the self-test, or bypass the action throttle.

## Static permission nodes

| Permission node | Default | Description |
|---|---|---|
| `clicksorted` | `true` | Master kill-switch: denying disables click-sorting, bundle packing, and all player commands. Does not affect admin access. |
| `clicksorted.admin` | `op` | Grants all command, sort, bundle, and throttle-bypass permissions. |
| `clicksorted.commands` | `true` | Access to the `/clicksorted` command tree (umbrella node). |
| `clicksorted.commands.sort` | `true` | Access to `/clicksorted sort` subcommands. |
| `clicksorted.commands.sort.enabled` | `true` | Toggle click-sorting on/off. |
| `clicksorted.commands.sort.method` | `true` | Use `/clicksorted sort method`. |
| `clicksorted.commands.sort.start-corner` | `true` | Use `/clicksorted sort start-corner`. |
| `clicksorted.commands.sort.fill-axis` | `true` | Use `/clicksorted sort fill-axis`. |
| `clicksorted.commands.click` | `true` | Access to `/clicksorted click` subcommands. |
| `clicksorted.commands.click.method` | `true` | Use `/clicksorted click method`. |
| `clicksorted.commands.click.hover` | `true` | Use `/clicksorted click allow-on-hover`. |
| `clicksorted.commands.bundle` | `true` | Use `/clicksorted bundle` subcommands. |
| `clicksorted.commands.lock` | `true` | Use `/clicksorted lock-slots`. |
| `clicksorted.commands.status` | `true` | Use `/clicksorted status`. |
| `clicksorted.commands.menu` | `true` | Use `/clicksorted menu` (the dialog-based preferences UI). |
| `clicksorted.admin.commands` | `op` | Access to `/clicksorted admin` subcommands. |
| `clicksorted.admin.commands.reload` | `op` | Use `/clicksorted admin reload`. |
| `clicksorted.admin.commands.config` | `op` | Use `/clicksorted admin config`. |
| `clicksorted.admin.commands.debug` | `op` | Use `/clicksorted admin debug`. |
| `clicksorted.admin.commands.benchmark` | `op` | Use `/clicksorted admin benchmark`. Also requires `enable_benchmark: true` in `config.yml` (default `false`). |
| `clicksorted.admin.commands.selftest` | `op` | Use `/clicksorted admin selftest`. Also requires `enable_selftest: true` in `config.yml` (default `false`). |
| `clicksorted.throttle.bypass` | `op` | Exempt from the per-player action throttle (`action_cooldown_ms`). |
| `clicksorted.sort` | `true` | Allow click-sorting all inventory types. Parent of the three nodes below. |
| `clicksorted.sort.player` | `true` | Allow sorting the player's own main inventory (excluding hotbar). |
| `clicksorted.sort.hotbar` | `true` | Allow sorting the player's hotbar. |
| `clicksorted.sort.container` | `true` | Allow sorting container inventories (chests, barrels, etc.). |
| `clicksorted.bundle` | `true` | Allow bundle packing during a sort (both regions). |
| `clicksorted.bundle.inventory` | `true` | Allow bundle packing in the player's own inventory. |
| `clicksorted.bundle.container` | `true` | Allow bundle packing in containers. |

## Dynamic permission nodes

The following nodes are **not declared in `paper-plugin.yml`**. Grant them via a permissions plugin (e.g. LuckPerms). They use `isPermissionSet` before `hasPermission` so OPs without an explicit grant are unaffected.

| Permission node | Description |
|---|---|
| `clicksorted.blacklist.material.<material>` | Prevent a specific material from being sorted, moved, or packed by anyone. See [Admin item blacklist](item-blacklist.md). |
| `clicksorted.blacklist.name.<slug>` | Prevent items matching a display-name slug from being sorted, moved, or packed. See [Admin item blacklist](item-blacklist.md). |
| `clicksorted.lock.player.slot.<n>` | Lock slot `n` (0–35) in all player inventories server-wide. See [Admin slot locks](slot-locks.md). |
