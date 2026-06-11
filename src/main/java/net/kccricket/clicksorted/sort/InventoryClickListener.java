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
import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.security.Permissions;
import net.kccricket.clicksorted.text.MessageUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;

/**
 * Thin event dispatcher. Reads player preferences, delegates pre-sort cycling to
 * {@link PrefsCycleHandler}, checks the trigger via {@link ClickMethod#matchesSortTrigger},
 * and hands sorting off to {@link InventorySortService}.
 */
public class InventoryClickListener implements Listener {

    private final ClickSortedPlugin plugin;
    private final PlayerSortingPrefs prefs;
    private final InventorySortService sortService;
    private final PrefsCycleHandler cycleHandler;

    public InventoryClickListener(ClickSortedPlugin plugin,
                                  InventorySortService sortService,
                                  PrefsCycleHandler cycleHandler) {
        this.plugin = plugin;
        this.prefs = plugin.getSortingPrefs();
        this.sortService = sortService;
        this.cycleHandler = cycleHandler;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInventoryClicked(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getCurrentItem() == null) {
            return;
        }
        if (!Permissions.isAllowedTo(player, "clicksorted.sort")) {
            return;
        }

        Log.debug("inventory click by player " + player.getName() + ": type=" + event.getClick()
                + " slot=" + event.getSlot() + " rawslot=" + event.getRawSlot());

        SortingMethod sortMethod = prefs.getSortingMethod(player);
        ClickMethod clickMethod = prefs.getClickMethod(player);
        boolean allowShiftClick = prefs.getShiftClickAllowed(player);

        if (cycleHandler.tryCycle(event, player, sortMethod, clickMethod, allowShiftClick)) {
            return;
        }

        // Ctrl+Q (CONTROL_DROP) on a bundle in the player's own inventory triggers pack-only.
        if (event.getClick() == ClickType.CONTROL_DROP
                && event.getCurrentItem().getType() == Material.BUNDLE
                && event.getClickedInventory() != null
                && event.getClickedInventory().getType() == InventoryType.PLAYER) {
            event.setCancelled(true);
            int entryCap = prefs.getBundleCapEnabled(player)
                    ? plugin.getConfigManager().main().getBundleEntryCap() : 0;
            int packed = sortService.packOnly(player, entryCap);
            if (packed >= 0) {
                MessageUtil.statusMessage(player,
                        plugin.getConfigManager().lang().getColoredMessage("bundlePacked",
                                Placeholder.unparsed("count", String.valueOf(packed))));
            }
            return;
        }

        if (clickMethod.matchesSortTrigger(event) && sortService.isSortableTarget(event)) {
            if (sortService.sortInventory(event, sortMethod) && clickMethod.shouldCancelEvent()) {
                // Use the Paper entity scheduler so the offhand reset is bound to this player
                // entity (Folia-safe).
                player.getScheduler().runDelayed(plugin, task ->
                        player.getInventory().setItemInOffHand(player.getInventory().getItemInOffHand()),
                        null, 1L);
                event.setCancelled(true);
            }
        }
    }
}
