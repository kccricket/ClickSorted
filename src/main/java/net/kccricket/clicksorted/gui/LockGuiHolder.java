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
import net.kccricket.clicksorted.config.LangConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Set;

/**
 * Holds the 45-slot (5-row) chest inventory used as the lock-toggle GUI.
 *
 * <p>Layout (top inventory, slots 0-44):
 * <ul>
 *   <li>Rows 1-3 (slots 0-26): lime/red panes for main-storage slots 9-35</li>
 *   <li>Row 4  (slots 27-35): gray divider panes; slot 35 is a help head</li>
 *   <li>Row 5  (slots 36-44): lime/red panes for hotbar slots 0-8</li>
 * </ul>
 *
 * <p>Slot mapping:
 * <ul>
 *   <li>Chest slot 0-26  ↔  Player inventory slot (chestSlot + 9)</li>
 *   <li>Chest slot 27-35  =  divider (no inventory mapping)</li>
 *   <li>Chest slot 36-44 ↔  Player inventory slot (chestSlot - 36)</li>
 * </ul>
 */
public class LockGuiHolder implements InventoryHolder {

    /** First chest slot of the divider row. */
    public static final int DIVIDER_START = 27;
    /** Last chest slot of the divider row (exclusive). */
    public static final int DIVIDER_END = 36;
    /** Total size of the top chest inventory. */
    public static final int GUI_SIZE = 45;

    private final Inventory inventory;

    public LockGuiHolder(ClickSortedPlugin plugin, Player player) {
        LangConfig lang = plugin.getConfigManager().lang();
        this.inventory = Bukkit.createInventory(this, GUI_SIZE,
                lang.getColoredMessage("lockGuiTitle"));

        Set<Integer> locked = plugin.getSortingPrefs().getLockedSlots(player);
        ItemStack unsortable = buildUnsortablePane(lang);

        // Rows 1-3: main storage slots 9-35 → chest slots 0-26
        for (int chestSlot = 0; chestSlot < DIVIDER_START; chestSlot++) {
            int invSlot = chestSlotToInvSlot(chestSlot);
            if (plugin.getConfigManager().main().isPlayerSlotSortable(invSlot)) {
                inventory.setItem(chestSlot, buildPane(lang, locked.contains(invSlot), chestSlot));
            } else {
                inventory.setItem(chestSlot, unsortable.clone());
            }
        }

        // Row 4: divider panes, with the rightmost slot replaced by the help head
        ItemStack divider = buildDividerPane(lang);
        for (int chestSlot = DIVIDER_START; chestSlot < DIVIDER_END - 1; chestSlot++) {
            inventory.setItem(chestSlot, divider.clone());
        }
        inventory.setItem(DIVIDER_END - 1, buildHelpHead(lang));

        // Row 5: hotbar slots 0-8 → chest slots 36-44
        for (int chestSlot = DIVIDER_END; chestSlot < GUI_SIZE; chestSlot++) {
            int invSlot = chestSlotToInvSlot(chestSlot);
            inventory.setItem(chestSlot, buildPane(lang, locked.contains(invSlot), chestSlot));
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Build a lime (unlocked) or red (locked) glass pane with a two-line lore:
     * line 1 — slot label ("Inventory slot N" or "Hotbar slot N");
     * line 2 — click-to-toggle instruction.
     */
    public static ItemStack buildPane(LangConfig lang, boolean locked, int chestSlot) {
        String slotLangKey;
        int displayNumber;
        if (chestSlot < DIVIDER_START) {
            slotLangKey = "lockPaneSlotInventory";
            displayNumber = chestSlot + 1;
        } else {
            slotLangKey = "lockPaneSlotHotbar";
            displayNumber = chestSlot - DIVIDER_END + 1;
        }

        ItemStack pane = new ItemStack(locked ? Material.BARRIER : Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(lang.getColoredMessage(locked ? "lockPaneLocked" : "lockPaneUnlocked"));
        meta.lore(List.of(
                lang.getColoredMessage(slotLangKey, Placeholder.unparsed("number", String.valueOf(displayNumber))),
                lang.getColoredMessage(locked ? "lockPaneLockedLore" : "lockPaneUnlockedLore")));
        pane.setItemMeta(meta);
        return pane;
    }

    public static ItemStack buildUnsortablePane(LangConfig lang) {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(lang.getColoredMessage("lockPaneUnsortable"));
        meta.lore(List.of(lang.getColoredMessage("lockPaneUnsortableLore")));
        pane.setItemMeta(meta);
        return pane;
    }

    private static ItemStack buildDividerPane(LangConfig lang) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(lang.getColoredMessage("lockDividerName"));
        pane.setItemMeta(meta);
        return pane;
    }

    private static ItemStack buildHelpHead(LangConfig lang) {
        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta meta = book.getItemMeta();
        meta.displayName(lang.getColoredMessage("lockHelpHeadName"));
        meta.lore(List.of(lang.getColoredMessage("lockHelpHeadLore")));
        book.setItemMeta(meta);
        return book;
    }

    /**
     * Convert a chest slot index to the corresponding player inventory slot index.
     * Returns -1 for divider slots (27-35).
     */
    public static int chestSlotToInvSlot(int chestSlot) {
        if (chestSlot < DIVIDER_START) {
            // Rows 1-3: main storage
            return chestSlot + 9;
        } else if (chestSlot < DIVIDER_END) {
            // Divider row — no mapping
            return -1;
        } else {
            // Row 5: hotbar
            return chestSlot - DIVIDER_END;
        }
    }

}
