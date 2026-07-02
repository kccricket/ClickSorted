# Admin Slot Locks

Admins can lock specific **player inventory slots** (0–35) server-wide so they are always excluded from sorting and cannot be toggled by the player in the lock GUI. Two enforcement channels are unioned.

## Config channel

Add slot indices to `config.yml` under `locked_slots.player`. Slots 0–8 are the hotbar; slots 9–35 are the main inventory. Out-of-range values are warned and skipped on load/reload.

```yaml
locked_slots:
  player:
    - 0    # first hotbar slot
    - 9    # first main-inventory slot
```

## Permission channel

Grant dynamic permission nodes via a permissions plugin (e.g. LuckPerms):

`clicksorted.lock.player.slot.<n>` — e.g. `clicksorted.lock.player.slot.9`

Nodes can be granted per-player or per-group. The same `isPermissionSet`-before-`hasPermission` OP guard applies — OPs without an explicit grant are unaffected.

## Lock GUI appearance

Admin-locked slots render as non-toggleable **iron-bars panes** in the player's lock GUI (see [Locking slots](../player-guide/slot-locking.md)). The permission channel is checked per-player, so the GUI correctly reflects both config and permission locks for the viewing player.
