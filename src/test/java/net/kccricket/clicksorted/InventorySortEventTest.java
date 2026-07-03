package net.kccricket.clicksorted;

import net.kccricket.clicksorted.events.InventorySortEvent;
import net.kccricket.clicksorted.events.InventorySortEvent.SlotStatus;
import net.kyori.adventure.text.Component;
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

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for the public {@link InventorySortEvent} extension point. Third-party plugins rely
 * on it firing before write-back, being cancellable, and honouring per-slot exclusion — so all three
 * behaviours are exercised through the real sort path.
 */
class InventorySortEventTest extends AbstractClickSortedTest {

    /** A listener that records the event and can optionally cancel it, exclude a slot, or exclude an item. */
    private static final class SortListener implements Listener {
        boolean fired;
        InventorySortEvent captured;
        boolean cancel;
        Integer excludeSlot;
        Material excludeMaterial;
        String excludeName;
        Component cancelReason;

        @EventHandler
        public void onSort(InventorySortEvent event) {
            fired = true;
            captured = event;
            if (excludeSlot != null) {
                event.excludeSlot(excludeSlot);
            }
            if (excludeMaterial != null) {
                event.excludeItem(excludeMaterial);
            }
            if (excludeName != null) {
                event.excludeItem(excludeName);
            }
            if (cancel) {
                event.setCancelled(true);
                if (cancelReason != null) {
                    event.setCancelReason(cancelReason);
                }
            }
        }
    }

    /** Fire a SWAP_OFFHAND click targeting rawSlot 27 (→ player main storage slot 9), as ProtectedSlotsTest/ProtectedItemsTest do. */
    private void sortMainStorage(PlayerMock player) {
        plugin.getSortingPrefs().setSortOverItems(player, true);
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        InventoryView view = player.openInventory(chest);
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 27, ClickType.SWAP_OFFHAND,
                InventoryAction.UNKNOWN) {
            @Override
            public ItemStack getCurrentItem() {
                return new ItemStack(Material.STONE, 1);
            }
        };
        server.getPluginManager().callEvent(event);
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
    void cancellingWithReasonMessagesThePlayer() {
        SortListener listener = new SortListener();
        listener.cancel = true;
        listener.cancelReason = Component.text("no sorting near the vault");
        server.getPluginManager().registerEvents(listener, plugin);

        PlayerMock player = addOpPlayer("Dave");
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, stack(Material.STONE, 5));
        InventoryView view = player.openInventory(chest);
        drainMessages(player);

        server.getPluginManager().callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 0));

        assertTrue(anyMessageContains(player, "no sorting near the vault"),
                "a listener-supplied cancel reason should be shown to the player");
    }

    @Test
    void cancellingWithNoReasonSendsNoMessage() {
        SortListener listener = new SortListener();
        listener.cancel = true;
        server.getPluginManager().registerEvents(listener, plugin);

        PlayerMock player = addOpPlayer("Erin");
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, stack(Material.STONE, 5));
        InventoryView view = player.openInventory(chest);
        drainMessages(player);

        server.getPluginManager().callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 0));

        assertEquals(0, drainMessageList(player).size(),
                "a cancellation with no reason should not message the player (this fires on every click)");
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

    @Test
    void containerSlotsAreAllSortable() {
        SortListener listener = new SortListener();
        server.getPluginManager().registerEvents(listener, plugin);

        PlayerMock player = addOpPlayer("Dave");
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, stack(Material.STONE, 5));
        InventoryView view = player.openInventory(chest);

        server.getPluginManager().callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 0));

        assertTrue(listener.fired);
        for (var entry : listener.captured.getSlots().entrySet()) {
            assertEquals(SlotStatus.SORTABLE, entry.getValue(),
                    "container slot " + entry.getKey() + " should be SORTABLE");
        }
        assertEquals(chest.getSize(), listener.captured.getRegionSlots().size());
        assertTrue(listener.captured.getUserLockedSlots().isEmpty());
        assertTrue(listener.captured.getAdminLockedSlots().isEmpty());
    }

    @Test
    void playerSlotsAreClassifiedByRangeAndLocks() {
        // Admin-lock slot 9 (config), user-lock slot 10, leave slot 11 sortable.
        plugin.getConfig().set("locked_slots.player", List.of(9));
        plugin.getConfigManager().main().load();

        SortListener listener = new SortListener();
        server.getPluginManager().registerEvents(listener, plugin);

        PlayerMock player = server.addPlayer("Erin"); // non-OP: real permission defaults apply
        plugin.getSortingPrefs().toggleSlotLocked(player, 10);

        sortMainStorage(player);

        assertTrue(listener.fired, "event should fire for the main-storage sort");
        InventorySortEvent event = listener.captured;

        assertEquals(SlotStatus.OUT_OF_RANGE, event.statusOf(0), "hotbar slot is out of range for a main-storage click");
        assertEquals(SlotStatus.ADMIN_LOCKED, event.statusOf(9), "slot 9 is admin-locked via config");
        assertEquals(SlotStatus.USER_LOCKED, event.statusOf(10), "slot 10 is user-locked via the lock GUI toggle");
        assertEquals(SlotStatus.SORTABLE, event.statusOf(11), "slot 11 is in range and unlocked");
        assertEquals(SlotStatus.OUT_OF_RANGE, event.statusOf(36), "armor/offhand slots are out of range");

        assertEquals(Set.of(9), event.getAdminLockedSlots());
        assertEquals(Set.of(10), event.getUserLockedSlots());
        assertFalse(event.getSortableSlots().contains(9), "admin-locked slot must already be excluded from getSortableSlots()");
        assertFalse(event.getSortableSlots().contains(10), "user-locked slot must already be excluded from getSortableSlots()");
    }

    @Test
    void excludeItemByMaterialProtectsMatchingStacks() {
        SortListener listener = new SortListener();
        listener.excludeMaterial = Material.DIRT;
        server.getPluginManager().registerEvents(listener, plugin);

        PlayerMock player = addOpPlayer("Frank");
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, stack(Material.DIRT, 1));
        chest.setItem(1, stack(Material.STONE, 5));
        chest.setItem(2, stack(Material.STONE, 10));
        InventoryView view = player.openInventory(chest);

        server.getPluginManager().callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 1));

        assertEquals(Material.DIRT, chest.getItem(0).getType(), "DIRT excluded by material must stay put");
        assertEquals(1, chest.getItem(0).getAmount());
        assertEquals(15, countByMaterial(chest).getOrDefault(Material.STONE, 0));
        assertEquals(Set.of(Material.DIRT), listener.captured.getExcludedMaterials());
    }

    @Test
    void excludeItemByNameProtectsMatchingStacks() {
        SortListener listener = new SortListener();
        listener.excludeName = "Heirloom";
        server.getPluginManager().registerEvents(listener, plugin);

        PlayerMock player = addOpPlayer("Grace");
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        ItemStack heirloom = stack(Material.DIAMOND, 1);
        var meta = heirloom.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Heirloom"));
        heirloom.setItemMeta(meta);
        chest.setItem(0, heirloom);
        chest.setItem(1, stack(Material.STONE, 5));
        chest.setItem(2, stack(Material.STONE, 10));
        InventoryView view = player.openInventory(chest);

        server.getPluginManager().callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 1));

        assertEquals(Material.DIAMOND, chest.getItem(0).getType(), "name-excluded item must stay put");
        assertEquals(1, chest.getItem(0).getAmount());
        assertEquals(15, countByMaterial(chest).getOrDefault(Material.STONE, 0));
        assertEquals(Set.of("heirloom"), listener.captured.getExcludedItemNames(), "excluded names are lower-cased");
    }

    @Test
    void getProtectedItemsReflectsAdminConfigBlacklist() {
        plugin.getConfig().set("blacklist.materials", List.of("OBSIDIAN"));
        plugin.getConfigManager().main().load();

        SortListener listener = new SortListener();
        server.getPluginManager().registerEvents(listener, plugin);

        PlayerMock player = addOpPlayer("Hank");
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, stack(Material.OBSIDIAN, 1));
        chest.setItem(1, stack(Material.STONE, 5));
        InventoryView view = player.openInventory(chest);

        server.getPluginManager().callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 1));

        assertTrue(listener.captured.getProtectedItems().config().materials().contains(Material.OBSIDIAN),
                "event metadata should expose the admin blacklist in effect for this sort");
        assertEquals(Material.OBSIDIAN, chest.getItem(0).getType(), "config-blacklisted OBSIDIAN must stay put");
    }

    @Test
    void deprecatedConstructorStillYieldsContiguousRange() {
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        InventoryView view = addOpPlayer("Ivy").openInventory(chest);

        @SuppressWarnings("deprecation")
        InventorySortEvent event = new InventorySortEvent(view, chest, 2, 5);

        assertEquals(Set.of(2, 3, 4), event.getSortableSlots());
        assertEquals(Set.of(2, 3, 4), event.getRegionSlots());
        assertTrue(event.getUserLockedSlots().isEmpty());
        assertTrue(event.getAdminLockedSlots().isEmpty());
        assertEquals(SlotStatus.SORTABLE, event.statusOf(3));
        assertEquals(SlotStatus.OUT_OF_RANGE, event.statusOf(1));
    }
}
