package net.kccricket.clicksorted;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that crafting-grid / non-sortable top inventories are never read or written by the
 * sort/pack pipeline.
 *
 * <p>The gating path is {@code InventoryClickListener} → {@code isSortableTarget} →
 * {@code shouldSort}, which checks whether the clicked inventory's type appears in
 * {@code sortable_inventories}. Inventory types outside that list (WORKBENCH, FURNACE, HOPPER,
 * and the player's CRAFTING grid) are structurally identical from the plugin's perspective;
 * HOPPER is used here because it is already exercised by existing tests and is guaranteed to
 * be supported by MockBukkit.
 *
 * <p>The result slot is the only slot that could phantom-dupe a crafted item if it were ever
 * read and written; {@code clickCraftingResultSlotDoesNotSort} pins that this can never happen.
 */
class CraftingExclusionTest extends AbstractClickSortedTest {

    /**
     * Build a click event whose {@code getClickedInventory()} returns the given inventory,
     * using the SWAP_OFFHAND trigger that matches the default test click method.
     */
    private InventoryClickEvent clickOnInventory(
            InventoryView view, Inventory clickedInv, int rawSlot, InventoryType.SlotType slotType) {
        return new InventoryClickEvent(
                view, slotType, rawSlot, ClickType.SWAP_OFFHAND, InventoryAction.UNKNOWN) {
            @Override
            public Inventory getClickedInventory() { return clickedInv; }
        };
    }

    @Test
    void clickCraftingResultSlotDoesNotSort() {
        // A sort-trigger click whose getClickedInventory() is a non-sortable inventory type
        // (representing the crafting result slot) must be a complete no-op. This pins that the
        // result slot — the only slot that would dupe a phantom crafted item if read+written — is
        // never touched by the sort/pack pipeline.
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setSortOverItems(player, true);

        Inventory nonSortable = server.createInventory(null, InventoryType.HOPPER);
        nonSortable.setItem(0, stack(Material.STONE, 5));
        nonSortable.setItem(1, stack(Material.STONE, 5));

        InventoryView view = player.openInventory(nonSortable);
        InventoryClickEvent event = clickOnInventory(
                view, nonSortable, 0, InventoryType.SlotType.RESULT);
        server.getPluginManager().callEvent(event);

        long stoneSlots = 0;
        for (ItemStack item : nonSortable.getContents()) {
            if (item != null && item.getType() == Material.STONE) stoneSlots++;
        }
        assertEquals(2, stoneSlots, "non-sortable result slot click must not sort");
        assertFalse(event.isCancelled(), "non-sortable result slot click must not be cancelled");
    }

    @Test
    void clickCraftingGridSlotDoesNotSort() {
        // A sort-trigger click on a crafting-grid input slot of a non-sortable inventory must
        // be a complete no-op, regardless of slot type.
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setSortOverItems(player, true);

        Inventory nonSortable = server.createInventory(null, InventoryType.HOPPER);
        nonSortable.setItem(0, stack(Material.COBBLESTONE, 32));
        nonSortable.setItem(1, stack(Material.COBBLESTONE, 16));

        InventoryView view = player.openInventory(nonSortable);
        InventoryClickEvent event = clickOnInventory(
                view, nonSortable, 0, InventoryType.SlotType.CRAFTING);
        server.getPluginManager().callEvent(event);

        long cobbleSlots = 0;
        for (ItemStack item : nonSortable.getContents()) {
            if (item != null && item.getType() == Material.COBBLESTONE) cobbleSlots++;
        }
        assertEquals(2, cobbleSlots, "crafting-grid input slot click must not sort");
        assertFalse(event.isCancelled(), "crafting-grid input slot click must not be cancelled");
    }

    @Test
    void playerInventorySortLeavesOpenNonSortableTopUntouched() {
        // With a non-sortable top inventory (HOPPER) open, a sort trigger on a player-main-storage
        // slot sorts the player inventory while leaving the top inventory byte-identical. Items
        // sitting in the top inventory — analogous to the crafting grid — are never read or pulled
        // into the player-inventory sort.
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setSortOverItems(player, true);

        Inventory hopper = server.createInventory(null, InventoryType.HOPPER);
        hopper.setItem(0, stack(Material.IRON_INGOT, 7));
        hopper.setItem(1, stack(Material.GOLD_INGOT, 3));
        ItemStack[] topBefore = hopper.getContents().clone();

        // Two SAND stacks in the player's main storage — they should merge after the sort.
        player.getInventory().setItem(9, stack(Material.SAND, 10));
        player.getInventory().setItem(10, stack(Material.SAND, 15));

        InventoryView view = player.openInventory(hopper);

        // HOPPER has 5 slots. rawSlot = 5 + (mainSlot - 9) = 5 + 0 = 5 → player slot 9.
        // Override getCurrentItem and getSlot to ensure the listener routes to main storage.
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 5, ClickType.SWAP_OFFHAND, InventoryAction.UNKNOWN) {
            @Override
            public Inventory getClickedInventory() { return player.getInventory(); }
            @Override
            public int getSlot() { return 9; }
            @Override
            public ItemStack getCurrentItem() { return new ItemStack(Material.SAND, 10); }
        };
        server.getPluginManager().callEvent(event);

        // Player main storage SAND must have merged (confirming the sort actually ran).
        long sandSlots = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack is = player.getInventory().getItem(i);
            if (is != null && is.getType() == Material.SAND) sandSlots++;
        }
        assertEquals(1, sandSlots, "player main-storage SAND should have merged (confirming sort ran)");
        assertEquals(25, countByMaterial(player.getInventory()).getOrDefault(Material.SAND, 0),
                "SAND total must be conserved");

        // The foreign top inventory must be byte-identical.
        ItemStack[] topAfter = hopper.getContents();
        for (int i = 0; i < topBefore.length; i++) {
            if (topBefore[i] == null) {
                assertNull(topAfter[i], "top-inv slot " + i + " must remain null");
            } else {
                assertNotNull(topAfter[i], "top-inv slot " + i + " must still have an item");
                assertEquals(topBefore[i].getType(), topAfter[i].getType(),
                        "top-inv slot " + i + " material must be unchanged");
                assertEquals(topBefore[i].getAmount(), topAfter[i].getAmount(),
                        "top-inv slot " + i + " amount must be unchanged");
            }
        }
    }
}
