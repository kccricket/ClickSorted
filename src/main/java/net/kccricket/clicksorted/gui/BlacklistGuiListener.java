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
import net.kccricket.clicksorted.text.ItemNames;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles all inventory interaction for the {@link BlacklistGuiHolder} GUI.
 *
 * <p>Every click and drag while the GUI is open is cancelled unconditionally so the player's
 * real inventory (rendered in the bottom half of the view) cannot be accidentally modified.
 *
 * <p>Interaction model:
 * <ul>
 *   <li>Click a <b>top-section slot</b> (0–44) containing a blacklist entry → remove that
 *       entry from the player's blacklist and refresh the GUI.</li>
 *   <li>Click the <b>previous/next arrow</b> (slot 45 / 53) → navigate one page.</li>
 *   <li>Click an item in the <b>player's real inventory</b> (raw slot ≥ 54) → add the item
 *       to the blacklist by name if it carries an explicit name (custom name, else item name),
 *       otherwise by material.</li>
 *   <li>All other slots (help book, filler panes): no effect.</li>
 * </ul>
 *
 * <p>All mutations (add/remove) are gated by the shared per-player {@code ActionThrottle};
 * page navigation is not throttled.
 */
public class BlacklistGuiListener implements Listener {

    private final ClickSortedPlugin plugin;

    public BlacklistGuiListener(ClickSortedPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        // Use InventoryEvent.getInventory() (the top inventory) rather than
        // getView().getTopInventory(): invoking methods on InventoryView from our own
        // bytecode breaks across versions where InventoryView is a class (≤1.20.6) vs an
        // interface (1.21+), throwing IncompatibleClassChangeError.
        if (!(event.getInventory().getHolder() instanceof BlacklistGuiHolder holder)) {
            return;
        }

        // Cancel every click unconditionally — protects the real inventory below.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int rawSlot = event.getRawSlot();

        if (rawSlot >= 0 && rawSlot < BlacklistGuiHolder.PAGE_SIZE) {
            // Top section: remove the clicked entry — throttled (only when an entry is present, so
            // clicking an empty slot neither consumes the throttle budget nor triggers its notice).
            BlacklistGuiHolder.Entry entry = holder.getEntryAt(rawSlot);
            if (entry == null) {
                return;
            }
            if (plugin.getActionThrottle().throttled(player)) {
                return;
            }
            removeEntry(player, entry);
            holder.refresh();

        } else if (rawSlot >= BlacklistGuiHolder.PAGE_SIZE && rawSlot < BlacklistGuiHolder.GUI_SIZE) {
            // Bottom control row — only the arrow slots do anything; filler and help book are no-ops.
            if (rawSlot == BlacklistGuiHolder.SLOT_PREV) {
                holder.navigate(-1);
            } else if (rawSlot == BlacklistGuiHolder.SLOT_NEXT) {
                holder.navigate(+1);
            }

        } else if (rawSlot >= BlacklistGuiHolder.GUI_SIZE) {
            // Player's real inventory: add the clicked item to the blacklist — throttled (only when a
            // real item is present, so clicking an empty slot is a free no-op).
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType() == Material.AIR) {
                return;
            }
            if (plugin.getActionThrottle().throttled(player)) {
                return;
            }
            addItem(player, item);
            holder.refresh();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof BlacklistGuiHolder) {
            event.setCancelled(true);
        }
    }

    private void removeEntry(Player player, BlacklistGuiHolder.Entry entry) {
        switch (entry) {
            case BlacklistGuiHolder.MaterialEntry m ->
                plugin.getSortingPrefs().removeFromBundleBlacklist(player, m.material());
            case BlacklistGuiHolder.NameEntry n ->
                plugin.getSortingPrefs().removeFromBundleBlacklistName(player, n.name());
        }
    }

    private void addItem(Player player, ItemStack item) {
        String name = ItemNames.explicitName(item);
        if (name != null) {
            plugin.getSortingPrefs().addToBundleBlacklistName(player, name);
        } else {
            plugin.getSortingPrefs().addToBundleBlacklist(player, item.getType());
        }
    }
}
