# PlayerPreferenceChangeEvent

`net.kccricket.clicksorted.events.PlayerPreferenceChangeEvent`

Fired by `PlayerSortingPrefs` before a per-player ClickSorted preference is changed: click/sort method, start corner, fill axis, the `enabled` flag, sort-over-items, bundle-packing toggles and stack limit, a locked-slot toggle, or a bundle blacklist add/remove/clear.

The event is only fired when the new value actually differs from the old one — a command or GUI call that sets a preference to its current value never fires it. (An exception: a no-op call that matches the *config default* still gets pinned into the player's PDC storage without firing the event, so an explicit choice survives a later change to the server default.)

`setLockedSlots(Player, Set<Integer>)`, the bulk setter used internally by `toggleSlotLocked` and by tests, is a deliberate exception — it writes directly to PDC with no event.

## Reading the change

`getChange()` returns a `Change<?>` — the sealed payload carrying which preference changed and its before/after values:

- `Change.preference()` — the `Preference<T>` key identifying which setting changed.
- `Change.oldValue()` / `Change.newValue()` — nullable; a blacklist add has no old value, a remove has no new value.

Two concrete shapes:

- `ValueChange<T>` — the common case, no extra data.
- `LockedSlotChange` — a locked-slot toggle; adds `slot()` (the slot index) alongside the inherited boolean old/new locked state. Always keyed by `Preference.LOCKED_SLOT`.

For a type-safe read of one specific preference, use `getChange(Preference<T> pref)`, which returns `Change<T>` narrowed to `T` when the event is for `pref`, or `null` otherwise:

```java
@EventHandler
public void onPreferenceChange(PlayerPreferenceChangeEvent event) {
    var clickChange = event.getChange(Preference.CLICK_MODE);
    if (clickChange != null) {
        ClickMethod oldMethod = clickChange.oldValue();
        ClickMethod newMethod = clickChange.newValue();
        // ...
    }
}
```

This is safe because `Preference<T>` constants (`Preference.CLICK_MODE`, `Preference.SORT_MODE`, etc. — see `Preference`) are the only instances of that class, so reference identity at the check site proves the payload's type parameter.

## Cancelling

Cancelling the event prevents the preference from being applied or persisted. The command or GUI caller that triggered the change reports the block to the player instead of a success message.

A cancelling listener may call `setCancelReason(Component)` to explain why. Unlike `InventorySortEvent`, preference changes are explicit, low-frequency player actions (a command, not a per-click event), so callers show the reason verbatim and fall back to a generic `preferenceChangeBlocked` lang-key message when no reason was set — cancelling silently is never an option here.

## PreferenceResult

Every event-firing `PlayerSortingPrefs` mutator returns a `net.kccricket.clicksorted.model.PreferenceResult` instead of a bare `boolean`/`void`, so callers can distinguish three outcomes:

- `APPLIED` — the value changed and was persisted.
- `UNCHANGED` — the requested value already matched the stored one; no event fired.
- `CANCELLED` — a listener vetoed the change; carries the listener's `cancelReason()` if one was set.

`PreferenceResult.applied()` / `PreferenceResult.cancelled()` are convenience predicates. This is what lets ClickSorted's own commands tell a real no-op apart from a listener veto and report each correctly to the player.

## Example

```java
@EventHandler
public void onPreferenceChange(PlayerPreferenceChangeEvent event) {
    // Prevent players in a minigame region from disabling auto-sort.
    var enabledChange = event.getChange(Preference.ENABLED);
    if (enabledChange != null && Boolean.FALSE.equals(enabledChange.newValue())
            && isInMinigame(event.getPlayer())) {
        event.setCancelled(true);
        event.setCancelReason(Component.text("Sorting can't be disabled during a match."));
    }
}
```
