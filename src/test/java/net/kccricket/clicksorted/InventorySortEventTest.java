package net.kccricket.clicksorted;

import net.kccricket.clicksorted.events.InventorySortEvent;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for the public {@link InventorySortEvent} extension point. Third-party plugins rely
 * on it firing before write-back, being cancellable, and honouring per-slot exclusion — so all three
 * behaviours are exercised through the real sort path.
 */
class InventorySortEventTest extends AbstractClickSortedTest {

    /** A listener that records the event and can optionally cancel it or exclude a slot. */
    private static final class SortListener implements Listener {
        boolean fired;
        InventorySortEvent captured;
        boolean cancel;
        Integer excludeSlot;

        @EventHandler
        public void onSort(InventorySortEvent event) {
            fired = true;
            captured = event;
            if (excludeSlot != null) {
                event.excludeSlot(excludeSlot);
            }
            if (cancel) {
                event.setCancelled(true);
            }
        }
    }

    private long stoneSlots(Inventory inv) {
        long n = 0;
        for (var item : inv.getContents()) {
            if (item != null && item.getType() == Material.STONE) n++;
        }
        return n;
    }

    @Test
    void eventFiresBeforeWriteBack() {
        SortListener listener = new SortListener();
        server.getPluginManager().registerEvents(listener, plugin);

        PlayerMock player = addOpPlayer("Alice");
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, stack(Material.STONE, 5));
        chest.setItem(1, stack(Material.STONE, 10));
        InventoryView view = player.openInventory(chest);

        server.getPluginManager().callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 0));

        assertTrue(listener.fired, "InventorySortEvent should fire");
        assertNotNull(listener.captured.getSortableSlots(), "event should expose its sortable slots");
        assertEquals(1, stoneSlots(chest), "and the sort should proceed when not cancelled");
    }

    @Test
    void cancellingTheEventAbortsTheSort() {
        SortListener listener = new SortListener();
        listener.cancel = true;
        server.getPluginManager().registerEvents(listener, plugin);

        PlayerMock player = addOpPlayer("Bob");
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, stack(Material.STONE, 5));
        chest.setItem(1, stack(Material.STONE, 10));
        InventoryView view = player.openInventory(chest);

        server.getPluginManager().callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 0));

        assertTrue(listener.fired, "event should still fire");
        assertEquals(2, stoneSlots(chest), "cancelled sort must leave the inventory untouched");
    }

    @Test
    void excludedSlotIsNeitherReadNorWritten() {
        SortListener listener = new SortListener();
        listener.excludeSlot = 0; // protect slot 0 from the sort
        server.getPluginManager().registerEvents(listener, plugin);

        PlayerMock player = addOpPlayer("Carol");
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, stack(Material.DIRT, 1));   // excluded → must stay put
        chest.setItem(1, stack(Material.STONE, 5));
        chest.setItem(2, stack(Material.STONE, 10));
        InventoryView view = player.openInventory(chest);

        server.getPluginManager().callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 1));

        assertEquals(Material.DIRT, chest.getItem(0).getType(),
                "excluded slot 0 should be neither read nor overwritten");
        assertEquals(1, chest.getItem(0).getAmount(), "excluded DIRT should be unchanged");
        assertEquals(15, countByMaterial(chest).getOrDefault(Material.STONE, 0),
                "STONE outside the excluded slot should still be merged");
    }
}
