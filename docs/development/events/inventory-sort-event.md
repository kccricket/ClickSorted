# InventorySortEvent

`net.kccricket.clicksorted.events.InventorySortEvent`

Fired by `InventorySortService` after a click matches a player's sort trigger and before any writes to the inventory, so third-party plugins can inspect or intervene. It applies to both sort paths — sort-with-layout (sorting on) and in-place consolidation (sorting off, packing on).

`InventorySortService` resolves the click's region slots and, for player inventories, the player's own locked slots (`/clicksorted lock-slots`) and admin-enforced slot locks (`config.yml`'s `locked_slots.player` or a `clicksorted.lock.player.slot.<n>` permission node) before constructing the event. Non-player inventories always resolve to no locks. The admin item blacklist (`ProtectedItems`, from `config.yml`'s `blacklist.*` plus the sorting player's `clicksorted.blacklist.*` permission nodes) is attached as read-only metadata.

## Cancelling

Cancelling the event aborts the sort entirely. A cancelling listener may call `setCancelReason(Component)` to explain why. Because this event fires on **every** matching click, the reason (or a fallback console-only silence when none is set) is shown to the player rate-limited via `CooldownMessenger`, not on every single cancelled click — unlike `PlayerPreferenceChangeEvent`, there is no generic fallback message when no reason is given; the cancellation is simply silent to the player.

## Slot classification

The sortable set is not a contiguous range — locks and admin rules can carve arbitrary holes in it.

- `getRegionSlots()` — the full slot range targeted by this click (e.g. hotbar, player main, or container storage), before any lock or item exclusion.
- `getUserLockedSlots()` / `getAdminLockedSlots()` — the two lock sets factored into the region; always empty for non-player inventories.
- `getSortableSlots()` — the current sortable set: `getRegionSlots()` minus both lock sets, already reflecting lock state at dispatch time. This is what will actually be read, moved, or overwritten by the sort, subject to further narrowing below. Read-only from outside the event — it can only be *narrowed*, via `excludeSlot(int)`; locked slots must never re-enter the sort.
- `statusOf(int slot)` — classifies a single slot as one of `SlotStatus.OUT_OF_RANGE`, `ADMIN_LOCKED`, `USER_LOCKED`, or `SORTABLE`, in that precedence order when more than one reason could apply.
- `getSlots()` — classifies every slot in the clicked inventory (`0` through `getInventory().getSize() - 1`) via `statusOf(int)`.

This classification reflects range and lock structure only. Item-based exclusions (the admin blacklist, and any `excludeItem` additions — see below) are content-dependent and resolved separately, so they are **not** reflected in `getSlots()`/`statusOf(int)`.

## Narrowing the sort

Listeners can narrow what the sort touches without cancelling it entirely:

- `excludeSlot(int slot)` — removes `slot` from `getSortableSlots()` for this sort only.
- `excludeItem(Material material)` — excludes any slot currently holding an item of this material.
- `excludeItem(String name)` — excludes any slot holding an item whose resolved plain-text display name matches `name`, case-insensitively.

`getExcludedMaterials()` / `getExcludedItemNames()` return unmodifiable views of what a listener has added so far during this event. `matchesExcludedItem(ItemStack)` is the single oracle combining the admin blacklist (`getProtectedItems()`) and any listener-contributed exclusions — after listeners run, `InventorySortService` excludes any remaining sortable slot whose item matches it, for all inventory types (not just player), so it can see content edits listeners made during the event.

## The target inventory

`getInventory()` returns the **live** target inventory, captured before any write — at dispatch time its contents are the pre-sort state, but it is not a snapshot. A listener that reads it after the event returns (e.g. on a later tick) will see post-sort contents, since there is no post-sort inventory to hand out at dispatch — the sort hasn't run yet and the event is still cancellable.

## Constructor

```java
InventorySortEvent(InventoryView transaction, Inventory sortInv, Set<Integer> regionSlots,
                    Set<Integer> userLockedSlots, Set<Integer> adminLockedSlots,
                    ProtectedItems protectedItems)
```

An older `(InventoryView, Inventory, int min, int max)` constructor is deprecated: it materializes a contiguous `[min, max)` region with empty locks and no blacklist metadata. It exists only for backward compatibility with earlier ClickSorted versions and should not be used by new code.

## Example

```java
@EventHandler
public void onSort(InventorySortEvent event) {
    // Never let this event touch slot 0, regardless of locks.
    event.excludeSlot(0);

    // Don't let creative-menu items get swept up by name.
    event.excludeItem("Creative Menu");

    // Veto sorting a specific inventory outright, with a reason shown to the player.
    if (event.getInventory().getHolder() instanceof MyProtectedHolder) {
        event.setCancelled(true);
        event.setCancelReason(Component.text("This container can't be sorted."));
    }
}
```
