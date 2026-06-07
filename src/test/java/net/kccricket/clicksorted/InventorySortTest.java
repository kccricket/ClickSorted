package net.kccricket.clicksorted;

import net.kccricket.clicksorted.model.ClickMethod;
import net.kccricket.clicksorted.model.SortingMethod;
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
 * Integration tests for ClickSortedPlugin.onInventoryClicked (and the private sortInventory /
 * sortAndMerge methods it delegates to).  The test fires real InventoryClickEvents via
 * Bukkit.getPluginManager().callEvent, exercising the same code path as production.
 *
 * NOTE: MockBukkit's simulated InventoryClickEvent always carries InventoryAction.UNKNOWN.
 * The plugin branches on getClick() (ClickType), not on InventoryAction, so this is fine.
 */
class InventorySortTest extends AbstractClickSortedTest {

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

    /**
     * Build a click event that simulates a click on an equipment slot (armor/offhand).
     * MockBukkit's convertSlot cannot produce getSlot() 36–40 from any real rawSlot, so we
     * override getSlot(), getClickedInventory(), and getCurrentItem() directly.
     */
    private InventoryClickEvent armorSlotClickEvent(
            InventoryView view, int armorSlot, ItemStack currentItem) {
        Inventory playerInv = view.getPlayer().getInventory();
        return new InventoryClickEvent(
                view, InventoryType.SlotType.ARMOR, 63, ClickType.SWAP_OFFHAND,
                InventoryAction.UNKNOWN) {
            @Override
            public int getSlot() { return armorSlot; }
            @Override
            public Inventory getClickedInventory() { return playerInv; }
            @Override
            public ItemStack getCurrentItem() { return currentItem; }
        };
    }

    /**
     * Count how many slots in [fromSlot, toSlot) of the given inventory contain the given material.
     */
    private long countSlotsWithMaterial(Inventory inv, Material mat, int fromSlot, int toSlot) {
        ItemStack[] contents = inv.getContents();
        long count = 0;
        for (int i = fromSlot; i < toSlot && i < contents.length; i++) {
            if (contents[i] != null && contents[i].getType() == mat) count++;
        }
        return count;
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
        // A player explicitly denied clicksorted.sort should not trigger a sort.
        PlayerMock player = server.addPlayer("Bob");
        player.addAttachment(plugin, "clicksorted.sort", false);  // explicitly deny

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
        assertEquals(2, stoneSlots, "Player without clicksorted.sort should not trigger a sort");
    }

    @Test
    void sortNonChestContainer() {
        // BARREL is in the sortable_inventories list; sorting should work the same as for CHEST.
        PlayerMock player = addOpPlayer("Alice");
        Inventory barrel = server.createInventory(null, InventoryType.BARREL);
        barrel.setItem(0, stack(Material.STONE, 5));
        barrel.setItem(1, stack(Material.STONE, 10));
        barrel.setItem(2, stack(Material.DIRT, 3));
        InventoryView view = player.openInventory(barrel);

        callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 0));

        Map<Material, Integer> counts = countByMaterial(barrel);
        assertEquals(15, counts.getOrDefault(Material.STONE, 0), "STONE stacks should merge to 15");
        assertEquals(3, counts.getOrDefault(Material.DIRT, 0));

        long stoneSlots = 0;
        for (ItemStack item : barrel.getContents()) {
            if (item != null && item.getType() == Material.STONE) stoneSlots++;
        }
        assertEquals(1, stoneSlots, "Two STONE stacks should have merged into one");
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
    void clickHotbarSortsHotbarOnly() {
        // Clicking a hotbar slot (player slots 0–8) sorts only the hotbar; main inventory is untouched.
        // In a 27-slot chest view, rawSlot 54 maps to getSlot() 0 (first hotbar slot).
        PlayerMock player = addOpPlayer("Alice");
        player.getInventory().setItem(0, stack(Material.STONE, 5));
        player.getInventory().setItem(1, stack(Material.STONE, 10));
        player.getInventory().setItem(9, stack(Material.DIRT, 3));
        player.getInventory().setItem(10, stack(Material.DIRT, 7));

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        InventoryView view = player.openInventory(chest);

        // getCurrentItem() via view.getItem(54) = bottomInv.getItem(27), which is empty — override it.
        InventoryClickEvent event = clickEventWithCurrentItem(
                view, ClickType.SWAP_OFFHAND, 54, stack(Material.STONE, 5));
        assertEquals(0, event.getSlot(), "rawSlot 54 in a 27-slot chest view must map to hotbar slot 0");
        callEvent(event);

        // Hotbar STONE must collapse to one slot; main-inv DIRT must remain two separate slots.
        assertEquals(1, countSlotsWithMaterial(player.getInventory(), Material.STONE, 0, 9),
                "Hotbar STONE should have merged to one slot");
        assertEquals(2, countSlotsWithMaterial(player.getInventory(), Material.DIRT, 9, 36),
                "Main-inventory DIRT should be untouched (still two slots)");
    }

    @Test
    void clickMainSortsMainOnly() {
        // Clicking a main-inventory slot (player slots 9–35) sorts only the main inventory;
        // the hotbar is untouched.
        // In a 27-slot chest view, rawSlot 27 maps to getSlot() 9 (first main-inventory slot).
        PlayerMock player = addOpPlayer("Alice");
        player.getInventory().setItem(9, stack(Material.DIRT, 3));
        player.getInventory().setItem(10, stack(Material.DIRT, 7));
        player.getInventory().setItem(0, stack(Material.STONE, 5));
        player.getInventory().setItem(1, stack(Material.STONE, 10));

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        InventoryView view = player.openInventory(chest);

        InventoryClickEvent event = fireClick(view, ClickType.SWAP_OFFHAND, 27);
        assertEquals(9, event.getSlot(), "rawSlot 27 in a 27-slot chest view must map to main-inv slot 9");

        // Main-inv DIRT must collapse to one slot; hotbar STONE must remain two separate slots.
        assertEquals(1, countSlotsWithMaterial(player.getInventory(), Material.DIRT, 9, 36),
                "Main-inventory DIRT should have merged to one slot");
        assertEquals(2, countSlotsWithMaterial(player.getInventory(), Material.STONE, 0, 9),
                "Hotbar STONE should be untouched (still two slots)");
    }

    @Test
    void equipmentUntouchedByHotbarSort() {
        // Sorting the hotbar must not disturb armor or offhand slots.
        PlayerMock player = addOpPlayer("Alice");
        player.getInventory().setHelmet(stack(Material.DIAMOND_HELMET));
        player.getInventory().setItemInOffHand(stack(Material.TORCH));
        player.getInventory().setItem(0, stack(Material.STONE, 5));
        player.getInventory().setItem(1, stack(Material.STONE, 10));

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        InventoryView view = player.openInventory(chest);
        // getCurrentItem() via view.getItem(54) = bottomInv.getItem(27), which is empty — override it.
        callEvent(clickEventWithCurrentItem(view, ClickType.SWAP_OFFHAND, 54, stack(Material.STONE, 5)));

        assertEquals(1, countSlotsWithMaterial(player.getInventory(), Material.STONE, 0, 9),
                "Hotbar STONE should have merged (confirming sort ran)");
        assertEquals(Material.DIAMOND_HELMET, player.getInventory().getHelmet().getType(),
                "Helmet must be unchanged after hotbar sort");
        assertEquals(Material.TORCH, player.getInventory().getItemInOffHand().getType(),
                "Offhand item must be unchanged after hotbar sort");
    }

    @Test
    void equipmentUntouchedByMainSort() {
        // Sorting the main inventory must not disturb armor or offhand slots.
        PlayerMock player = addOpPlayer("Alice");
        player.getInventory().setHelmet(stack(Material.DIAMOND_HELMET));
        player.getInventory().setItemInOffHand(stack(Material.TORCH));
        player.getInventory().setItem(9, stack(Material.STONE, 5));
        player.getInventory().setItem(10, stack(Material.STONE, 10));

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        InventoryView view = player.openInventory(chest);
        // getCurrentItem() via view.getItem(27) = bottomInv.getItem(0), which is empty — override it.
        callEvent(clickEventWithCurrentItem(view, ClickType.SWAP_OFFHAND, 27, stack(Material.STONE, 5)));

        assertEquals(1, countSlotsWithMaterial(player.getInventory(), Material.STONE, 9, 36),
                "Main-inventory STONE should have merged (confirming sort ran)");
        assertEquals(Material.DIAMOND_HELMET, player.getInventory().getHelmet().getType(),
                "Helmet must be unchanged after main-inventory sort");
        assertEquals(Material.TORCH, player.getInventory().getItemInOffHand().getType(),
                "Offhand item must be unchanged after main-inventory sort");
    }

    @Test
    void clickEquipmentSlotDoesNotSort() {
        // Clicking an armor/offhand slot (getSlot() 36–40) must not trigger any sort.
        // Without the equipment-slot guard in InventorySortService, such a click falls through
        // to the main-inventory branch and incorrectly sorts slots 9–35.
        PlayerMock player = addOpPlayer("Alice");
        player.getInventory().setItem(9, stack(Material.STONE, 5));
        player.getInventory().setItem(10, stack(Material.STONE, 10));
        player.getInventory().setHelmet(stack(Material.DIAMOND_HELMET));

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        InventoryView view = player.openInventory(chest);

        // No real rawSlot in MockBukkit produces getSlot() 36–40, so we override the event directly.
        InventoryClickEvent event = armorSlotClickEvent(view, 39, stack(Material.DIAMOND_HELMET));
        callEvent(event);

        // Main-inventory STONE must remain two separate slots — no sort should have occurred.
        assertEquals(2, countSlotsWithMaterial(player.getInventory(), Material.STONE, 9, 36),
                "Equipment slot click must not sort the main inventory");
        assertEquals(Material.DIAMOND_HELMET, player.getInventory().getHelmet().getType(),
                "Helmet must remain unchanged");
    }
}
