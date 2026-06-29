package net.kccricket.clicksorted;

import net.kccricket.clicksorted.events.InventorySortEvent;
import net.kccricket.clicksorted.model.ClickMethod;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
 * Item-conservation invariants: asserts that every sort/pack path leaves the total item count
 * unchanged — no duplication and no loss.
 *
 * <p>These tests are the primary guard against the two failure modes that matter most for an
 * inventory-mutating plugin. Each test targets a distinct mechanism that could break conservation
 * if the code's assumptions were violated.
 */
class ConservationTest extends AbstractClickSortedTest {

    // -------------------------------------------------------------------------
    // Cursor conservation — SWAP sort with a non-empty cursor
    // -------------------------------------------------------------------------

    @Test
    void heldCursorItemNotFoldedInOrLostOnSwapSort() {
        // SWAP sort with a non-empty held cursor: the cursor item must not be folded into the
        // inventory (duplication risk) and the existing inventory totals must be conserved.
        // We override getCursor() on the event to simulate a held cursor from the sort code's
        // perspective; the critical assertion is that DIAMOND never appears in the chest.
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setSortOverItems(player, true);

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, stack(Material.STONE, 5));
        chest.setItem(1, stack(Material.STONE, 10));
        chest.setItem(2, stack(Material.DIRT, 3));

        // DIAMOND on the cursor — must never appear in the chest after the sort.
        ItemStack cursor = stack(Material.DIAMOND, 1);

        InventoryView view = player.openInventory(chest);
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 0, ClickType.SWAP_OFFHAND, InventoryAction.UNKNOWN) {
            @Override public ItemStack getCursor() { return cursor; }
        };
        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled(), "SWAP sort must cancel the event");

        Map<Material, Integer> invAfter = countByMaterial(chest);

        // DIAMOND must not have leaked from the cursor into the chest (no duplication).
        assertEquals(0, invAfter.getOrDefault(Material.DIAMOND, 0),
                "cursor DIAMOND must not appear in the chest after sort");

        // Inventory totals conserved (STONE merged, DIRT unchanged).
        assertEquals(15, invAfter.getOrDefault(Material.STONE, 0), "STONE total must be conserved");
        assertEquals(3, invAfter.getOrDefault(Material.DIRT, 0), "DIRT total must be conserved");
    }

    // -------------------------------------------------------------------------
    // InventorySortEvent mutation — contents are read after the event fires
    // -------------------------------------------------------------------------

    @Test
    void sortEventListenerMutationDoesNotDuplicate() {
        // A third-party listener that removes an item during InventorySortEvent must not cause
        // the sort to restore the removed item. The production code reads inventory contents
        // after the event fires (line 199+ in InventorySortService), so mutations are visible
        // and are respected — the removed item must not reappear after the sort.
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setSortOverItems(player, true);

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, stack(Material.STONE, 10));
        chest.setItem(1, stack(Material.DIRT, 5));
        chest.setItem(2, stack(Material.IRON_INGOT, 3));

        // Listener removes all IRON_INGOT during the sort event.
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onSort(InventorySortEvent e) {
                if (e.getInventory().equals(chest)) {
                    for (int i = 0; i < chest.getSize(); i++) {
                        ItemStack is = chest.getItem(i);
                        if (is != null && is.getType() == Material.IRON_INGOT) {
                            chest.clear(i);
                        }
                    }
                }
            }
        }, plugin);

        InventoryView view = player.openInventory(chest);
        server.getPluginManager().callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 0));

        // IRON_INGOT was removed during the event — the sort must not restore it.
        assertEquals(0, countByMaterial(chest).getOrDefault(Material.IRON_INGOT, 0),
                "removed IRON_INGOT must not be restored by the sort (would be a dupe)");
        // STONE and DIRT were untouched by the listener and must be conserved.
        assertEquals(10, countByMaterial(chest).getOrDefault(Material.STONE, 0),
                "STONE total must be conserved");
        assertEquals(5, countByMaterial(chest).getOrDefault(Material.DIRT, 0),
                "DIRT total must be conserved");
    }

    // -------------------------------------------------------------------------
    // Throttle path — DOUBLE_CLICK cursor stack is preserved when throttled
    // -------------------------------------------------------------------------

    @Test
    void throttledDoubleClickDoesNotStrandOrLoseLiftedStack() {
        // DOUBLE_CLICK while throttled: the sort does not run, but the cursor repair that would
        // normally deposit the lifted stack back into its origin slot (inside sortInventory) is also
        // skipped. The event IS cancelled (to suppress vanilla gather), and the cursor stack must be
        // preserved as-is — neither stranded in a lost state nor silently consumed.
        plugin.getConfig().set("action_cooldown_ms", 60_000);
        PlayerMock player = server.addPlayer("NonOp"); // non-op → no throttle bypass
        plugin.getSortingPrefs().setClickMethod(player, ClickMethod.DOUBLE_CLICK);
        plugin.getSortingPrefs().setSortOverItems(player, true);

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, stack(Material.STONE, 5));
        chest.setItem(1, stack(Material.STONE, 10));
        InventoryView view = player.openInventory(chest);

        // Consume the throttle window with a first sort.
        server.getPluginManager().callEvent(fireClick(view, ClickType.DOUBLE_CLICK, 0));
        drainMessages(player);

        // Re-stage: slot 0 empty (as if first click of a double-click lifted it), cursor holds the stack.
        chest.clear(0);
        ItemStack lifted = stack(Material.STONE, 5);
        player.setItemOnCursor(lifted.clone());

        InventoryClickEvent throttledEvent = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 0, ClickType.DOUBLE_CLICK, InventoryAction.UNKNOWN) {
            @Override public ItemStack getCurrentItem() { return new ItemStack(Material.AIR); }
            @Override public ItemStack getCursor() { return lifted; }
        };
        server.getPluginManager().callEvent(throttledEvent);

        // Event must be cancelled so the vanilla gather-to-cursor is suppressed.
        assertTrue(throttledEvent.isCancelled(),
                "throttled DOUBLE_CLICK must cancel the vanilla gather-to-cursor");

        // The lifted cursor stack must be preserved — it is not lost when the sort is dropped.
        ItemStack cursorAfter = player.getItemOnCursor();
        assertNotNull(cursorAfter, "cursor must still hold the lifted stack");
        assertTrue(cursorAfter.getType() == Material.STONE && cursorAfter.getAmount() == 5,
                "cursor must be unchanged: expected STONE×5 but got " + cursorAfter);
    }

    // -------------------------------------------------------------------------
    // Shared container — multiple viewers, conservation and refresh
    // -------------------------------------------------------------------------

    @Test
    void sharedContainerSortConservesAndRefreshesAllViewers() {
        // Two players viewing the same chest: one fires a sort. Asserts that totals are conserved
        // and that refreshViewers (which calls updateInventory on each viewer) does not throw or
        // corrupt state. This documents and guards the single-threaded safety contract for shared
        // containers and ensures the viewer-refresh path handles multiple viewers correctly.
        PlayerMock sorter = addOpPlayer("Sorter");
        PlayerMock viewer = server.addPlayer("Viewer");
        plugin.getSortingPrefs().setSortOverItems(sorter, true);

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, stack(Material.STONE, 5));
        chest.setItem(1, stack(Material.STONE, 10));
        chest.setItem(2, stack(Material.DIRT, 3));

        // Both players open the same chest instance.
        InventoryView sorterView = sorter.openInventory(chest);
        viewer.openInventory(chest);

        server.getPluginManager().callEvent(fireClick(sorterView, ClickType.SWAP_OFFHAND, 0));

        // Totals conserved.
        Map<Material, Integer> after = countByMaterial(chest);
        assertEquals(15, after.getOrDefault(Material.STONE, 0), "STONE total must be conserved");
        assertEquals(3, after.getOrDefault(Material.DIRT, 0), "DIRT total must be conserved");

        // Sort ran (stacks merged).
        long stoneSlots = 0;
        for (ItemStack item : chest.getContents()) {
            if (item != null && item.getType() == Material.STONE) stoneSlots++;
        }
        assertEquals(1, stoneSlots, "STONE stacks should have merged");

        // Both viewers still have the chest open (refreshViewers must not close or corrupt their view).
        assertEquals(chest, sorter.getOpenInventory().getTopInventory(),
                "sorter's view must still be the sorted chest");
        assertEquals(chest, viewer.getOpenInventory().getTopInventory(),
                "viewer's view must still be the sorted chest");
    }
}
