# Locking Slots

Run `/clicksorted lock-slots` to open the slot-lock GUI. The top of the screen shows a grid of glass panes mirroring your inventory — three rows for the main inventory and one row for the hotbar, separated by a divider.

Items in locked slots won't be sorted, merged, packed into a bundle, or otherwise moved by the plugin. If a bundle sits in a locked slot, no items will be moved into or out of it.

| Pane | Meaning |
|---|---|
| Lime pane | Slot is unlocked. |
| Barrier icon | Slot is locked by you. |
| Iron bars | Slot is locked by the server. |

Click on a slot to change its state. Changes are saved immediately. Close the GUI when done — your locks are active right away.

Locked slots apply only to your own player inventory (both the main region and the hotbar). Container inventories (chests, barrels, etc.) are always sorted in full.
