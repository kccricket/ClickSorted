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
            if (plugin.getActionThrottle().throttled(player)) {
                return;
            }
            if (sortService.sortInventory(event, prefs.getSortingMethod(player))
                    && clickMethod.shouldCancelEvent()) {
                if (clickMethod.needsOffhandReset()) {
                    // Use the Paper entity scheduler so the offhand reset is bound to this player
                    // entity (Folia-safe).
                    player.getScheduler().runDelayed(plugin, task ->
                            player.getInventory().setItemInOffHand(player.getInventory().getItemInOffHand()),
                            null, 1L);
                }
                event.setCancelled(true);
            }
        }
    }
}
