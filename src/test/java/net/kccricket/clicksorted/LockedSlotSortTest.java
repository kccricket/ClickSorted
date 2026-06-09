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

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests confirming that locked player inventory slots are skipped by the sorter
 * while all other slots are still sorted and merged normally.
 */
class LockedSlotSortTest extends AbstractClickSortedTest {

    /**
     * Build a SWAP_OFFHAND click event targeting the given rawSlot and override getCurrentItem()
     * so the plugin's null-guard doesn't short-circuit before sorting.
     */
    private InventoryClickEvent swapClickAt(InventoryView view, int rawSlot, ItemStack currentItem) {
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, rawSlot, ClickType.SWAP_OFFHAND,
                InventoryAction.UNKNOWN) {
            @Override
            public ItemStack getCurrentItem() {
                return currentItem;
            }
        };
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void lockedMainInvSlotIsUntouchedBySortWhileOthersSort() {
        PlayerMock player = addOpPlayer("Alice");

        // Place two STONE stacks in main inventory (should merge on sort).
        player.getInventory().setItem(9, stack(Material.STONE, 5));
        player.getInventory().setItem(10, stack(Material.STONE, 10));
        // Place DIRT in slot 11 and lock it.
        player.getInventory().setItem(11, stack(Material.DIRT, 3));
        plugin.getSortingPrefs().setLockedSlots(player, Set.of(11));

        // Open a chest so we get a view (sort triggers when player inventory is below).
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        InventoryView view = player.openInventory(chest);

        // Click rawSlot 27 → getSlot() 9 (main inventory) to trigger main-inv sort.
        swapClickAt(view, 27, stack(Material.STONE, 5));

        // STONE should have merged (sort ran).
        long stoneSlots = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == Material.STONE) stoneSlots++;
        }
        assertEquals(1, stoneSlots, "STONE stacks should have merged — sort must have run");

        // Locked DIRT slot must be untouched.
        ItemStack slot11 = player.getInventory().getItem(11);
        assertNotNull(slot11, "Locked slot 11 must still have an item");
        assertEquals(Material.DIRT, slot11.getType(), "Locked slot 11 must still contain DIRT");
        assertEquals(3, slot11.getAmount(), "Locked slot 11 item amount must be unchanged");
    }

    @Test
    void lockedHotbarSlotIsUntouchedBySortWhileOthersSort() {
        PlayerMock player = addOpPlayer("Alice");

        // Place two STONE stacks in hotbar (should merge on sort).
        player.getInventory().setItem(0, stack(Material.STONE, 5));
        player.getInventory().setItem(1, stack(Material.STONE, 10));
        // Place GRAVEL in hotbar slot 2 and lock it.
        player.getInventory().setItem(2, stack(Material.GRAVEL, 7));
        plugin.getSortingPrefs().setLockedSlots(player, Set.of(2));

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        InventoryView view = player.openInventory(chest);

        // Click rawSlot 54 → getSlot() 0 (hotbar) to trigger hotbar sort.
        swapClickAt(view, 54, stack(Material.STONE, 5));

        // STONE should have merged.
        long stoneSlots = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == Material.STONE) stoneSlots++;
        }
        assertEquals(1, stoneSlots, "Hotbar STONE stacks should have merged — sort must have run");

        // Locked GRAVEL slot must be untouched.
        ItemStack slot2 = player.getInventory().getItem(2);
        assertNotNull(slot2, "Locked hotbar slot 2 must still have an item");
        assertEquals(Material.GRAVEL, slot2.getType(), "Locked hotbar slot 2 must still contain GRAVEL");
        assertEquals(7, slot2.getAmount(), "Locked hotbar slot 2 amount must be unchanged");
    }

    @Test
    void sortWithNoLocksWorksNormally() {
        // Regression: no locked slots → full sort/merge works as before.
        PlayerMock player = addOpPlayer("Alice");
        player.getInventory().setItem(9, stack(Material.STONE, 5));
        player.getInventory().setItem(10, stack(Material.STONE, 10));

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        InventoryView view = player.openInventory(chest);
        swapClickAt(view, 27, stack(Material.STONE, 5));

        long stoneSlots = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == Material.STONE) stoneSlots++;
        }
        assertEquals(1, stoneSlots, "STONE stacks should merge when no slots are locked");
    }

    @Test
    void locksOnlyAffectPlayerInventoryNotContainers() {
        // Locked slots are only excluded for InventoryType.PLAYER; container sorts are unaffected.
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setLockedSlots(player, Set.of(0, 1, 2));  // chest slot indices

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, stack(Material.STONE, 5));
        chest.setItem(1, stack(Material.STONE, 10));
        InventoryView view = player.openInventory(chest);

        fireClick(view, ClickType.SWAP_OFFHAND, 0);

        long stoneSlots = 0;
        for (ItemStack item : chest.getContents()) {
            if (item != null && item.getType() == Material.STONE) stoneSlots++;
        }
        assertEquals(1, stoneSlots, "Container sort must not be affected by player locked slots");
    }
}
