package net.kccricket.clicksorted.gui;

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
import net.kccricket.clicksorted.model.PlayerSortingPrefs;
import net.kccricket.clicksorted.sort.ProtectedSlots;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Handles all inventory interaction for the {@link LockGuiHolder} GUI.
 *
 * <p>Every click and drag while the GUI is open is cancelled unconditionally so the player's
 * real inventory (rendered in the bottom half of the view) cannot be modified. Clicks on
 * interactive panes in the top inventory toggle the corresponding player inventory slot's
 * lock state and update the pane colour/name in place.
 */
public class LockGuiListener implements Listener {

    private final ClickSortedPlugin plugin;

    public LockGuiListener(ClickSortedPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        // Use InventoryEvent.getInventory() (the top inventory) rather than
        // getView().getTopInventory(): invoking methods on InventoryView from our own
        // bytecode breaks across versions where InventoryView is a class (≤1.20.6) vs an
        // interface (1.21+), throwing IncompatibleClassChangeError.
        if (!(event.getInventory().getHolder() instanceof LockGuiHolder)) {
            return;
        }

        // Cancel every click unconditionally — protects the real inventory below.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int rawSlot = event.getRawSlot();

        // Only process clicks within our top inventory; ignore real-inventory clicks (rawSlot >= 45).
        if (rawSlot < 0 || rawSlot >= LockGuiHolder.GUI_SIZE) {
            return;
        }

        int invSlot = LockGuiHolder.chestSlotToInvSlot(rawSlot);
        // Divider slots map to -1; admin-locked slots are non-toggleable.
        if (invSlot < 0) {
            return;
        }
        if (ProtectedSlots.forSort(player, plugin.getConfigManager().main()).blocks(invSlot)) {
            return;
        }

        // A confirmed lock-toggle action — subject to the shared per-player throttle.
        if (plugin.getActionThrottle().throttled(player)) {
            return;
        }

        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        boolean nowLocked = prefs.toggleSlotLocked(player, invSlot);

        event.getInventory().setItem(rawSlot,
                LockGuiHolder.buildPane(plugin.getConfigManager().lang(), nowLocked, rawSlot));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof LockGuiHolder) {
            event.setCancelled(true);
        }
    }
}
