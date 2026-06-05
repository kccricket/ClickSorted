package me.desht.clicksort.sort;

/*
 * This file is part of ClickSort
 *
 * ClickSort is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * ClickSort is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with ClickSort. If not, see <http://www.gnu.org/licenses/>.
 */

import me.desht.clicksort.ClickMethod;
import me.desht.clicksort.ClickSortPlugin;
import me.desht.clicksort.SortingMethod;
import me.desht.dhutils.MessageUtil;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Handles the shift-click-on-empty-slot gestures that let players cycle their personal
 * sort method (shift-left) and click method (shift-right) in-inventory.
 */
public class PrefsCycleHandler {

    private final ClickSortPlugin plugin;

    public PrefsCycleHandler(ClickSortPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Attempt to handle the event as a preference-cycling gesture.
     *
     * @return {@code true} if the event was a cycle gesture and has been handled — the caller
     *         should stop processing it further; {@code false} if it was not a cycle gesture.
     */
    public boolean tryCycle(InventoryClickEvent event, Player player,
                            SortingMethod sortMethod, ClickMethod clickMethod, boolean allowShiftClick) {
        if (event.getCurrentItem() == null
                || event.getCurrentItem().getType() != Material.AIR
                || !event.isShiftClick()
                || !allowShiftClick) {
            return false;
        }

        if (event.isLeftClick() && clickMethod != ClickMethod.NONE) {
            // shift-left-clicking an empty slot cycles sort method for the player
            do {
                sortMethod = sortMethod.cycle();
            } while (!sortMethod.isAvailable());
            plugin.getSortingPrefs().setSortingMethod(player, sortMethod);
            MessageUtil.statusMessage(player,
                    plugin.getConfigManager().lang().getColoredMessage("sortBy",
                            Placeholder.unparsed("method", sortMethod.toString()),
                            Placeholder.unparsed("instruction", clickMethod.getInstruction())));
            plugin.getMessenger().message(player, "leftclick", 60,
                    plugin.getConfigManager().lang().getColoredMessage("shiftLeftToChange")
                            .colorIfAbsent(NamedTextColor.GRAY)
                            .decorate(TextDecoration.ITALIC));
            return true;
        } else if (event.isRightClick()) {
            // shift-right-clicking an empty slot cycles click method for the player
            clickMethod = clickMethod.cycle();
            plugin.getSortingPrefs().setClickMethod(player, clickMethod);
            MessageUtil.statusMessage(player, clickMethod.getInstruction());
            plugin.getMessenger().message(player, "rightclick", 60,
                    plugin.getConfigManager().lang().getColoredMessage("shiftRightToChange")
                            .colorIfAbsent(NamedTextColor.GRAY)
                            .decorate(TextDecoration.ITALIC));
            return true;
        }

        return false;
    }
}
