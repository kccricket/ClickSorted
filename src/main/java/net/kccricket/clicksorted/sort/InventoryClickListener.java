package net.kccricket.clicksorted.sort;

/*
 * This file is part of ClickSorted
 *
 * ClickSorted is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * ClickSorted is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with ClickSorted. If not, see <http://www.gnu.org/licenses/>.
 */

import net.kccricket.clicksorted.ClickSortedPlugin;
import net.kccricket.clicksorted.gui.ClickSortedHolder;
import net.kccricket.clicksorted.logging.Log;
import net.kccricket.clicksorted.model.ClickMethod;
import net.kccricket.clicksorted.model.PlayerSortingPrefs;
import net.kccricket.clicksorted.security.Permissions;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Thin event dispatcher. Reads player preferences, checks the trigger via
 * {@link ClickMethod#matchesSortTrigger}, applies the universal "sort over items" gate,
 * and hands sorting off to {@link InventorySortService}.
 */
public class InventoryClickListener implements Listener {

    private final ClickSortedPlugin plugin;
    private final PlayerSortingPrefs prefs;
    private final InventorySortService sortService;

    public InventoryClickListener(ClickSortedPlugin plugin,
                                  InventorySortService sortService) {
        this.plugin = plugin;
        this.prefs = plugin.getSortingPrefs();
        this.sortService = sortService;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInventoryClicked(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // Never treat one of our own GUIs as a sortable target. They are CHEST-type inventories, so
        // with ignore_plugin_inventory=false (the default) they would otherwise match the sortable set
        // and a sort-trigger click would rearrange their contents (or the real inventory below them)
        // before the GUI's own listener — which runs after us at the same priority — cancels the
        // interaction. The marker interface covers every current and future ClickSorted GUI.
        if (event.getInventory().getHolder() instanceof ClickSortedHolder) {
            return;
        }
        if (!Permissions.isAllowedTo(player, "clicksorted.sort")) {
            return;
        }

        Log.debug("inventory click by player " + player.getName() + ": type=" + event.getClick()
                + " slot=" + event.getSlot() + " rawslot=" + event.getRawSlot());

        ClickMethod clickMethod = prefs.getClickMethod(player);

        if (clickMethod.matchesSortTrigger(event) && sortService.isSortableTarget(event)) {
            // Universal "sort over items" gate: unless enabled, sorting only fires on an empty slot.
            ItemStack current = event.getCurrentItem();
            boolean slotOccupied = current != null && current.getType() != Material.AIR;
            if (slotOccupied && !prefs.getSortOverItems(player)) {
                return;
            }
            // Whether the originating click would otherwise perform a vanilla side-effect we must suppress.
            // shouldCancelEvent() covers the methods that are destructive regardless of slot contents
            // (SWAP swaps the offhand item, CONTROL_DROP drops the stack, shift-click moves it). A plain
            // LEFT click (SINGLE_CLICK) has a side-effect only when the slot is occupied — it would pick
            // the just-sorted stack onto the cursor — so it must be cancelled in exactly that case.
            // DOUBLE_CLICK is intentionally left uncancelled for now, pending manual testing of its
            // two-event collect-to-cursor behavior.
            boolean cancelVanilla = clickMethod.shouldCancelEvent()
                    || (slotOccupied && clickMethod == ClickMethod.SINGLE_CLICK);
            if (plugin.getActionThrottle().throttled(player)) {
                // The trigger matched but we're rate-limited, so no sort runs. We must still cancel the
                // originating click when it has a vanilla side-effect; otherwise a throttled sort-click
                // silently performs that vanilla action instead.
                if (cancelVanilla) {
                    event.setCancelled(true);
                }
                return;
            }
            if (sortService.sortInventory(event, prefs.getSortingMethod(player)) && cancelVanilla) {
                // Cancelling the event is sufficient to suppress the vanilla side-effect on all tested
                // server versions (Paper 1.20.6 and 1.26.1.2); no explicit offhand resync is required.
                event.setCancelled(true);
            }
        }
    }
}
