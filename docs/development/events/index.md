# Events

ClickSorted publishes two custom Bukkit events for third-party plugins:

| Event | Package | Fired when |
|---|---|---|
| [`InventorySortEvent`](inventory-sort-event.md) | `net.kccricket.clicksorted.events` | A click matches a player's sort trigger, after locks/permissions are resolved and before any inventory writes. |
| [`PlayerPreferenceChangeEvent`](player-preference-change-event.md) | `net.kccricket.clicksorted.events` | A per-player preference (click/sort method, start corner, fill axis, `enabled`, sort-over-items, bundle-packing settings, a locked-slot toggle, or a bundle blacklist change) is about to change, before it's applied or persisted. |

Both events are cancellable, and both let a cancelling listener attach a `Component` explaining why via `setCancelReason(Component)` — surfaced to the affected player instead of a generic message. Neither event does anything with the reason unless the event is also cancelled.

Register listeners the normal Bukkit way (`@EventHandler` + `PluginManager#registerEvents`), depending on ClickSorted as a `softdepend`/`depend` in your plugin's `paper-plugin.yml` so its classes are on your classpath.
