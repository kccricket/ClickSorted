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
import net.kccricket.clicksorted.text.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Holds the 54-slot (double-chest) inventory used as the bundle blacklist GUI.
 *
 * <p>Layout (top inventory, slots 0–53):
 * <ul>
 *   <li>Rows 1–5 (slots 0–44): current blacklist entries for the active page (up to
 *       {@value PAGE_SIZE} per page). Material entries display as the vanilla item;
 *       name entries display as a glinting name tag with the blacklisted name.</li>
 *   <li>Row 6 (slots 45–53): pagination controls and help.
 *       Slot 45 = previous-page arrow (when page &gt; 0);
 *       slot 49 = help book;
 *       slot 53 = next-page arrow (when another page exists);
 *       all other slots = black stained-glass-pane filler.</li>
 * </ul>
 *
 * <p>Clicking a top-section entry removes it from the player's blacklist immediately and
 * re-renders the page. Clicking an item in the player's real inventory (raw slot ≥ 54)
 * adds it to the blacklist — by material if it has no custom display name, or by display
 * name if it does.
 */
public class BlacklistGuiHolder implements ClickSortedHolder {

    /** A single blacklist entry, either a material or a display-name string. */
    public sealed interface Entry permits MaterialEntry, NameEntry {}

    /** A blacklist entry that blocks a specific material from being packed. */
    public record MaterialEntry(Material material) implements Entry {}

    /** A blacklist entry that blocks any item whose plain-text display name matches. */
    public record NameEntry(String name) implements Entry {}

    /** Total size of the top chest inventory. */
    public static final int GUI_SIZE = 54;
    /** Maximum number of blacklist entries shown per page. */
    public static final int PAGE_SIZE = 45;
    /** Raw slot index of the previous-page arrow control. */
    public static final int SLOT_PREV = 45;
    /** Raw slot index of the help book. */
    public static final int SLOT_HELP = 49;
    /** Raw slot index of the next-page arrow control. */
    public static final int SLOT_NEXT = 53;

    private final ClickSortedPlugin plugin;
    private final Player player;
    private final Inventory inventory;

    // Full sorted entry list, refreshed on each mutation.
    private List<Entry> entries;
    // Current page index (0-based).
    private int page;

    public BlacklistGuiHolder(ClickSortedPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        LangConfig lang = plugin.getConfigManager().lang();
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, lang.getColoredMessage("blacklistGuiTitle"));
        this.page = 0;
        refresh();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Reloads the entry list from the player's current prefs and re-renders the active page.
     * Call this after any blacklist mutation so the GUI stays in sync.
     */
    public void refresh() {
        this.entries = loadSortedEntries();
        int maxPage = Math.max(0, totalPages(entries.size()) - 1);
        if (page > maxPage) {
            page = maxPage;
        }
        renderPage();
    }

    /**
     * Moves to page {@code current + delta}, clamped to valid range, and re-renders.
     * Positive delta = next page, negative = previous.
     */
    public void navigate(int delta) {
        int newPage = Math.max(0, Math.min(totalPages(entries.size()) - 1, page + delta));
        if (newPage != page) {
            page = newPage;
            renderPage();
        }
    }

    /**
     * Returns the blacklist entry occupying top-inventory slot {@code guiSlot} on the current
     * page, or {@code null} if the slot is empty or out of range.
     */
    public Entry getEntryAt(int guiSlot) {
        if (guiSlot < 0 || guiSlot >= PAGE_SIZE) {
            return null;
        }
        int idx = page * PAGE_SIZE + guiSlot;
        return idx < entries.size() ? entries.get(idx) : null;
    }

    // --- Static helpers (exposed for tests) ---

    /**
     * Returns the total number of pages needed to display {@code entryCount} entries.
     * Always at least 1.
     */
    public static int totalPages(int entryCount) {
        return Math.max(1, (entryCount + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /**
     * Returns the sort label for {@code entry}: the material name for material entries,
     * the stored string for name entries.
     */
    public static String entryLabel(Entry entry) {
        return switch (entry) {
            case MaterialEntry m -> m.material().name();
            case NameEntry n -> n.name();
        };
    }

    // --- Private helpers ---

    private List<Entry> loadSortedEntries() {
        List<Entry> list = new ArrayList<>();
        for (Material m : plugin.getSortingPrefs().getBundleBlacklist(player)) {
            list.add(new MaterialEntry(m));
        }
        for (String n : plugin.getSortingPrefs().getBundleBlacklistNames(player)) {
            list.add(new NameEntry(n));
        }
        // Sort alphabetically ascending (A→Z), case-insensitive.
        list.sort(Comparator.comparing(BlacklistGuiHolder::entryLabel, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    private void renderPage() {
        LangConfig lang = plugin.getConfigManager().lang();

        // Clear top slots.
        for (int i = 0; i < PAGE_SIZE; i++) {
            inventory.setItem(i, null);
        }

        // Populate top slots with the current page's entries.
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < entries.size(); i++) {
            inventory.setItem(i, buildEntryItem(lang, entries.get(start + i)));
        }

        // Bottom row: fill all with filler, then overlay controls.
        ItemStack filler = ClickSortedHolder.buildFiller(lang);
        for (int slot = PAGE_SIZE; slot < GUI_SIZE; slot++) {
            inventory.setItem(slot, filler.clone());
        }
        inventory.setItem(SLOT_HELP, ClickSortedHolder.buildHelpBook(lang, "blacklistHelpBookLore"));
        if (page > 0) {
            inventory.setItem(SLOT_PREV, buildArrow(lang, false));
        }
        if (page < totalPages(entries.size()) - 1) {
            inventory.setItem(SLOT_NEXT, buildArrow(lang, true));
        }
    }

    private static ItemStack buildEntryItem(LangConfig lang, Entry entry) {
        return switch (entry) {
            case MaterialEntry m -> buildMaterialItem(lang, m.material());
            case NameEntry n -> buildNameTagItem(lang, n.name());
        };
    }

    /** Vanilla item of the blacklisted material with a click-to-remove lore line. */
    private static ItemStack buildMaterialItem(LangConfig lang, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        // Non-item materials (e.g. a stale entry stored before validation) have no ItemMeta; show the
        // bare item rather than throwing and breaking the whole GUI.
        if (meta != null) {
            meta.lore(MessageUtil.toLore(lang.getColoredMessage("blacklistEntryLore")));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Glinting name tag whose display name is the blacklisted plain-text string, plus a
     * click-to-remove lore line.
     */
    private static ItemStack buildNameTagItem(LangConfig lang, String name) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        meta.setEnchantmentGlintOverride(true);
        meta.lore(MessageUtil.toLore(lang.getColoredMessage("blacklistEntryLore")));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildArrow(LangConfig lang, boolean next) {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        meta.displayName(lang.getColoredMessage(next ? "blacklistArrowNext" : "blacklistArrowPrev"));
        arrow.setItemMeta(meta);
        return arrow;
    }
}
