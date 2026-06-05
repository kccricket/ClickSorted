package net.kccricket.clicksort;

import net.kccricket.clicksort.model.ClickMethod;
import net.kccricket.clicksort.model.SortingMethod;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ClickSortPlugin.onInventoryClicked (and the private sortInventory /
 * sortAndMerge methods it delegates to).  The test fires real InventoryClickEvents via
 * Bukkit.getPluginManager().callEvent, exercising the same code path as production.
 *
 * NOTE: MockBukkit's simulated InventoryClickEvent always carries InventoryAction.UNKNOWN.
 * The plugin branches on getClick() (ClickType), not on InventoryAction, so this is fine.
 */
class InventorySortTest extends AbstractClickSortTest {

    // --- Helpers ---

    /**
     * Build a InventoryClickEvent with an explicit currentItem override.  We need this for
     * cases where the clicked slot is "empty" (AIR) but must not be null (the plugin's null
     * guard would short-circuit before we could test cycling logic).
     */
    private InventoryClickEvent clickEventWithCurrentItem(
            InventoryView view, ClickType clickType, int rawSlot, ItemStack currentItem) {
        return new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, rawSlot, clickType,
                InventoryAction.UNKNOWN) {
            @Override
            public ItemStack getCurrentItem() {
                return currentItem;
            }
        };
    }

    private void callEvent(InventoryClickEvent event) {
        server.getPluginManager().callEvent(event);
    }

    // --- Sort / merge tests ---

    @Test
    void swapClickSortsAndMergesChestInventory() {
        // SWAP is the default click mode in test-config.yml and is available on version ≥ 16.
        // Fill a chest with two partial STONE stacks and one DIRT stack (unsorted).
        // After the click the stacks should be merged and the total counts preserved.
        PlayerMock player = addOpPlayer("Alice");
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, stack(Material.STONE, 5));
        chest.setItem(1, stack(Material.STONE, 10));
        chest.setItem(2, stack(Material.DIRT, 3));
        InventoryView view = player.openInventory(chest);

        // Fire SWAP_OFFHAND at slot 0 (has a STONE item → getCurrentItem is non-null).
        callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 0));

        Map<Material, Integer> counts = countByMaterial(chest);
        assertEquals(15, counts.getOrDefault(Material.STONE, 0), "STONE stacks should have merged to 15");
        assertEquals(3, counts.getOrDefault(Material.DIRT, 0), "DIRT count should be unchanged");

        // Verify merge: exactly one non-empty STONE slot.
        long stoneSlots = 0;
        for (ItemStack item : chest.getContents()) {
            if (item != null && item.getType() == Material.STONE) stoneSlots++;
        }
        assertEquals(1, stoneSlots, "Two STONE stacks should have merged into one");
    }

    @Test
    void doubleClickSortsChest() {
        PlayerMock player = addOpPlayer("Alice");
        player.setOp(true);
        plugin.getSortingPrefs().setClickMethod(player, ClickMethod.DOUBLE);

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, stack(Material.SAND, 2));
        chest.setItem(1, stack(Material.SAND, 8));
        chest.setItem(2, stack(Material.GRAVEL, 4));
        InventoryView view = player.openInventory(chest);

        callEvent(fireClick(view, ClickType.DOUBLE_CLICK, 0));

        Map<Material, Integer> counts = countByMaterial(chest);
        assertEquals(10, counts.getOrDefault(Material.SAND, 0), "SAND stacks should merge to 10");
        assertEquals(4, counts.getOrDefault(Material.GRAVEL, 0));
    }

    @Test
    void singleClickOnAirSlotSortsChest() {
        // SINGLE mode: LEFT click on an empty (AIR) slot with empty cursor triggers sort.
        PlayerMock player = addOpPlayer("Alice");
        player.setOp(true);
        plugin.getSortingPrefs().setClickMethod(player, ClickMethod.SINGLE);

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, stack(Material.COBBLESTONE, 7));
        chest.setItem(1, stack(Material.COBBLESTONE, 3));
        chest.setItem(2, stack(Material.GRAVEL, 5));
        InventoryView view = player.openInventory(chest);

        // Click on slot 3 (empty) — getCurrentItem is overridden to return a non-null AIR item.
        InventoryClickEvent event = clickEventWithCurrentItem(
                view, ClickType.LEFT, 3, new ItemStack(Material.AIR));
        callEvent(event);

        Map<Material, Integer> counts = countByMaterial(chest);
        assertEquals(10, counts.getOrDefault(Material.COBBLESTONE, 0), "Cobblestone should merge to 10");
        assertEquals(5, counts.getOrDefault(Material.GRAVEL, 0));
    }

    @Test
    void swapClickCancelsEvent() {
        // For SWAP mode the event must be cancelled so the offhand-swap doesn't actually occur.
        PlayerMock player = addOpPlayer("Alice");
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, stack(Material.STONE, 1));
        InventoryView view = player.openInventory(chest);

        InventoryClickEvent event = fireClick(view, ClickType.SWAP_OFFHAND, 0);
        assertTrue(event.isCancelled(), "SWAP mode should cancel the InventoryClickEvent");
    }

    @Test
    void nonSortableInventoryTypeIsIgnored() {
        // HOPPER is not in the sortable_inventories list — no sort should occur.
        PlayerMock player = addOpPlayer("Alice");
        Inventory hopper = server.createInventory(null, InventoryType.HOPPER);
        hopper.setItem(0, stack(Material.STONE, 5));
        hopper.setItem(1, stack(Material.STONE, 3));
        InventoryView view = player.openInventory(hopper);

        callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 0));

        // Items should be unchanged — two separate STONE stacks still in slots 0 and 1.
        long stoneSlots = 0;
        for (ItemStack item : hopper.getContents()) {
            if (item != null && item.getType() == Material.STONE) stoneSlots++;
        }
        assertEquals(2, stoneSlots, "HOPPER inventory should not be sorted");
    }

    @Test
    void nullCurrentItemCausesEarlyReturn() {
        // The handler returns immediately if getCurrentItem() is null — no sort.
        PlayerMock player = addOpPlayer("Alice");
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(1, stack(Material.STONE, 5));
        chest.setItem(2, stack(Material.STONE, 3));
        InventoryView view = player.openInventory(chest);

        // Slot 0 is empty — view.getItem(0) returns null → InventoryClickEvent.getCurrentItem() null.
        callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 0));

        // STONE stacks should NOT be merged.
        long stoneSlots = 0;
        for (ItemStack item : chest.getContents()) {
            if (item != null && item.getType() == Material.STONE) stoneSlots++;
        }
        assertEquals(2, stoneSlots, "Null currentItem should cause early return; no sort should occur");
    }

    @Test
    void playerWithoutSortPermissionCannotSort() {
        // A player explicitly denied clicksort.sort should not trigger a sort.
        PlayerMock player = server.addPlayer("Bob");
        player.addAttachment(plugin, "clicksort.sort", false);  // explicitly deny

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, stack(Material.STONE, 5));
        chest.setItem(1, stack(Material.STONE, 10));
        InventoryView view = player.openInventory(chest);

        callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 0));

        // Stacks should remain un-merged.
        long stoneSlots = 0;
        for (ItemStack item : chest.getContents()) {
            if (item != null && item.getType() == Material.STONE) stoneSlots++;
        }
        assertEquals(2, stoneSlots, "Player without clicksort.sort should not trigger a sort");
    }

    // --- Shift-click cycling tests ---

    @Test
    void shiftLeftOnAirSlotCyclesSortMethod() {
        // Shift-left-clicking an empty slot advances the player's sort method.
        PlayerMock player = addOpPlayer("Alice");
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        InventoryView view = player.openInventory(chest);

        SortingMethod before = plugin.getSortingPrefs().getSortingMethod(player);
        drainMessages(player);

        // Override getCurrentItem() to return non-null AIR so the handler doesn't short-circuit.
        InventoryClickEvent event = clickEventWithCurrentItem(
                view, ClickType.SHIFT_LEFT, 0, new ItemStack(Material.AIR));
        callEvent(event);

        SortingMethod after = plugin.getSortingPrefs().getSortingMethod(player);
        assertNotEquals(before, after, "Shift-left should cycle the sort method");

        // A status message containing the new method name should have been sent.
        boolean messageFound = false;
        String msg;
        while ((msg = player.nextMessage()) != null) {
            if (msg.contains(after.toString())) {
                messageFound = true;
            }
        }
        assertTrue(messageFound, "Expected a status message mentioning the new sort method");
    }

    @Test
    void shiftRightOnAirSlotCyclesClickMethod() {
        // Shift-right-clicking an empty slot advances the player's click method.
        PlayerMock player = addOpPlayer("Alice");
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        InventoryView view = player.openInventory(chest);

        ClickMethod before = plugin.getSortingPrefs().getClickMethod(player);
        drainMessages(player);

        InventoryClickEvent event = clickEventWithCurrentItem(
                view, ClickType.SHIFT_RIGHT, 0, new ItemStack(Material.AIR));
        callEvent(event);

        ClickMethod after = plugin.getSortingPrefs().getClickMethod(player);
        assertNotEquals(before, after, "Shift-right should cycle the click method");
    }

    // --- Player inventory sort ---

    @Test
    void sortPlayerMainInventory() {
        // Clicking inside the player-inventory portion of an open-chest view should sort the
        // player's main inventory (slots 9–35, i.e. player_sort_min..player_sort_max).
        PlayerMock player = addOpPlayer("Alice");

        // Fill main inventory (player slots 9-11) with mergeable stacks.
        player.getInventory().setItem(9, stack(Material.IRON_INGOT, 10));
        player.getInventory().setItem(10, stack(Material.IRON_INGOT, 20));
        player.getInventory().setItem(11, stack(Material.GOLD_INGOT, 5));

        // Open a chest so we have a two-pane view; clicking in the bottom pane sorts player inv.
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        InventoryView view = player.openInventory(chest);

        // The player inventory starts at rawSlot = chest.getSize() = 27.
        // Main inventory slot 9 is at rawSlot 27 + 9 = 36.
        // SWAP_OFFHAND at rawSlot 36 → currentItem = player.getInventory().getItem(9) = IRON_INGOT.
        callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 36));

        Map<Material, Integer> counts = countByMaterial(player.getInventory());
        assertEquals(30, counts.getOrDefault(Material.IRON_INGOT, 0), "IRON_INGOT stacks should merge to 30");
        assertEquals(5, counts.getOrDefault(Material.GOLD_INGOT, 0));
    }

    @Test
    void sortPlayerHotbar() {
        // Clicking a hotbar slot (player inv slots 0-8) sorts the hotbar range.
        PlayerMock player = addOpPlayer("Alice");

        player.getInventory().setItem(0, stack(Material.STONE, 5));
        player.getInventory().setItem(1, stack(Material.STONE, 10));
        player.getInventory().setItem(2, stack(Material.OAK_LOG, 3));

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        InventoryView view = player.openInventory(chest);

        // Player hotbar slot 0 is at rawSlot 27 + 0 = 27 in the chest view.
        callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 27));

        Map<Material, Integer> counts = countByMaterial(player.getInventory());
        assertEquals(15, counts.getOrDefault(Material.STONE, 0), "Hotbar STONE stacks should merge to 15");
        assertEquals(3, counts.getOrDefault(Material.OAK_LOG, 0));
    }
}
