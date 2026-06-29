package net.kccricket.clicksorted;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the "sorting off, bundle packing on" in-place consolidation path.
 *
 * <p>Key invariants:
 * <ul>
 *   <li>Items never move to a slot that was empty before the trigger click.</li>
 *   <li>Visible order of slots is unchanged (no re-layout).</li>
 *   <li>Same-material stacks consolidate within their existing slots (full stacks first,
 *       remainder last, trailing lane slots cleared).</li>
 *   <li>Bundle-eligible remainders pack into bundles already in the inventory;
 *       bundles stay in their original slots.</li>
 *   <li>When both sorting and packing are off, the trigger click is a complete no-op.</li>
 * </ul>
 */
class PackOnlyInPlaceTest extends AbstractClickSortedTest {

    // ---- Shared trigger helpers ----

    /**
     * Fire a SWAP_OFFHAND (the default test click method) on the player's main storage
     * (rawSlot 27 → main slot 9) with sort-over-items forced on so the slot-occupied gate
     * does not block the trigger.
     */
    private void triggerMainStorage(PlayerMock player) {
        plugin.getSortingPrefs().setSortOverItems(player, true);
        Inventory dummy = server.createInventory(null, InventoryType.CHEST);
        InventoryView view = player.openInventory(dummy);
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 27, ClickType.SWAP_OFFHAND, InventoryAction.UNKNOWN) {
            @Override
            public ItemStack getCurrentItem() { return new ItemStack(Material.STONE, 1); }
        };
        server.getPluginManager().callEvent(event);
    }

    /** Fire a SWAP_OFFHAND on slot 0 of a chest view. */
    private void triggerContainer(PlayerMock player, InventoryView view) {
        plugin.getSortingPrefs().setSortOverItems(player, true);
        fireClick(view, ClickType.SWAP_OFFHAND, 0);
    }

    // ---- Bundle inspection helpers (copied from BundleSortIntegrationTest for locality) ----

    private static ItemStack findBundle(Inventory inv, int from, int to) {
        for (int i = from; i < to; i++) {
            ItemStack is = inv.getItem(i);
            if (is != null && is.getType() == Material.BUNDLE) return is;
        }
        return null;
    }

    private static boolean bundleHas(ItemStack bundleItem, Material mat) {
        BundleMeta meta = (BundleMeta) bundleItem.getItemMeta();
        for (ItemStack is : meta.getItems()) {
            if (is != null && is.getType() == mat) return true;
        }
        return false;
    }

    private static int looseSlots(Inventory inv, int from, int to, Material mat) {
        int n = 0;
        for (int i = from; i < to; i++) {
            ItemStack is = inv.getItem(i);
            if (is != null && is.getType() == mat) n++;
        }
        return n;
    }

    // =========================================================================
    // Player inventory (main storage, sorting off + packing on)
    // =========================================================================

    @Test
    void sortingOff_packingOn_consolidatesSameTypeInPlace() {
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setEnabled(player, false);
        plugin.getSortingPrefs().setBundlePackInInventory(player, true);

        // Place partial stacks of STONE in slots 9 and 11, DIRT in slot 13, gap at 10 and 12.
        player.getInventory().setItem(9, new ItemStack(Material.STONE, 5));
        player.getInventory().setItem(11, new ItemStack(Material.STONE, 10));
        player.getInventory().setItem(13, new ItemStack(Material.DIRT, 3));

        triggerMainStorage(player);

        Inventory inv = player.getInventory();
        // STONE should consolidate: 15 total, all in slot 9 (the first slot it occupied), slot 11 cleared.
        ItemStack slot9 = inv.getItem(9);
        assertNotNull(slot9, "Slot 9 should still have stone");
        assertEquals(Material.STONE, slot9.getType());
        assertEquals(15, slot9.getAmount(), "Stone should consolidate to 15 in slot 9");
        assertNull(inv.getItem(11), "Slot 11 should be cleared after consolidation");

        // DIRT: untouched (only one stack, no merging needed).
        ItemStack slot13 = inv.getItem(13);
        assertNotNull(slot13);
        assertEquals(Material.DIRT, slot13.getType());
        assertEquals(3, slot13.getAmount());

        // Previously empty slots should remain empty.
        assertNull(inv.getItem(10), "Slot 10 was empty and must stay empty");
        assertNull(inv.getItem(12), "Slot 12 was empty and must stay empty");
    }

    @Test
    void sortingOff_packingOn_packsPartialIntoBundle_bundleStaysInPlace() {
        PlayerMock player = addOpPlayer("Bob");
        plugin.getSortingPrefs().setEnabled(player, false);
        plugin.getSortingPrefs().setBundlePackInInventory(player, true);

        // Bundle at slot 9, loose COBBLESTONE (10 stacks → partial) at slot 10.
        player.getInventory().setItem(9, new ItemStack(Material.BUNDLE, 1));
        player.getInventory().setItem(10, new ItemStack(Material.COBBLESTONE, 10));
        // Slots 11+ are empty.

        triggerMainStorage(player);

        Inventory inv = player.getInventory();
        // Bundle must still be at slot 9 (not moved).
        ItemStack bundleAtSlot9 = inv.getItem(9);
        assertNotNull(bundleAtSlot9, "Bundle should remain at slot 9");
        assertEquals(Material.BUNDLE, bundleAtSlot9.getType());

        // Cobblestone partial is small enough to pack → slot 10 cleared, it went into the bundle.
        assertNull(inv.getItem(10), "Cobblestone slot should be cleared after packing into bundle");
        assertTrue(bundleHas(bundleAtSlot9, Material.COBBLESTONE),
                "Bundle should contain the packed cobblestone");

        // No previously-empty slot should now be occupied.
        for (int s = 11; s < 36; s++) {
            assertNull(inv.getItem(s), "Slot " + s + " was empty and must stay empty");
        }
    }

    @Test
    void sortingOff_packingOn_fullStacksStayLoose_remainderPacks() {
        PlayerMock player = addOpPlayer("Carol");
        plugin.getSortingPrefs().setEnabled(player, false);
        plugin.getSortingPrefs().setBundlePackInInventory(player, true);

        // COBBLESTONE max-stack = 64. Put 64 (full) in slot 9 and a partial 10 in slot 10.
        player.getInventory().setItem(9, new ItemStack(Material.BUNDLE, 1));
        player.getInventory().setItem(10, new ItemStack(Material.COBBLESTONE, 64)); // full stack
        player.getInventory().setItem(11, new ItemStack(Material.COBBLESTONE, 10)); // partial

        triggerMainStorage(player);

        Inventory inv = player.getInventory();
        // Bundle at slot 9 unchanged (it's the bin).
        assertEquals(Material.BUNDLE, inv.getItem(9).getType());
        // Full stack of 64 stays in slot 10.
        ItemStack slot10 = inv.getItem(10);
        assertNotNull(slot10, "Full stack should remain loose");
        assertEquals(Material.COBBLESTONE, slot10.getType());
        assertEquals(64, slot10.getAmount(), "Full stack should be 64");
        // Partial (10) was the remainder → packed into bundle, slot 11 cleared.
        assertNull(inv.getItem(11), "Partial-stack slot should be cleared after packing");
        assertTrue(bundleHas(inv.getItem(9), Material.COBBLESTONE),
                "Bundle should contain the packed remainder");
    }

    @Test
    void sortingOff_packingOff_completeNoOp() {
        PlayerMock player = addOpPlayer("Dave");
        plugin.getSortingPrefs().setEnabled(player, false);
        plugin.getSortingPrefs().setBundlePackInInventory(player, false);
        plugin.getSortingPrefs().setBundlePackInContainers(player, false);

        // Arrange a messy layout.
        player.getInventory().setItem(9, new ItemStack(Material.STONE, 5));
        player.getInventory().setItem(11, new ItemStack(Material.STONE, 10));
        player.getInventory().setItem(13, new ItemStack(Material.DIRT, 3));

        // Snapshot the inventory before the trigger.
        ItemStack[] before = player.getInventory().getContents().clone();

        InventoryClickEvent event = triggerAndCapture(player);

        // Inventory must be byte-identical.
        assertArrayEquals(before, player.getInventory().getContents(),
                "Inventory should be unchanged when sorting and packing are both off");
        // The originating click must NOT have been cancelled (no vanilla side-effect to suppress).
        assertFalse(event.isCancelled(), "Event should not be cancelled on a no-op trigger");
    }

    /**
     * Fire the trigger and return the event so the caller can inspect whether it was cancelled.
     */
    private InventoryClickEvent triggerAndCapture(PlayerMock player) {
        plugin.getSortingPrefs().setSortOverItems(player, true);
        Inventory dummy = server.createInventory(null, InventoryType.CHEST);
        InventoryView view = player.openInventory(dummy);
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 27, ClickType.SWAP_OFFHAND, InventoryAction.UNKNOWN) {
            @Override
            public ItemStack getCurrentItem() { return new ItemStack(Material.STONE, 1); }
        };
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void sortingOff_packingOn_bundleWithNoLooseCounterpart_doesNotThrow() {
        // Regression: NPE at InPlacePacker.consolidate when a bundle in the sortable region
        // already contains an item type that has no loose stack elsewhere in the region.
        // BundlePacker.packIntoBundles merges bundle contents into loosePool, introducing keys
        // that have no matching Lane — the lane-update loop must guard against null.
        PlayerMock player = addOpPlayer("Heather");
        plugin.getSortingPrefs().setEnabled(player, false);
        plugin.getSortingPrefs().setBundlePackInInventory(player, true);

        // Build a bundle that already contains COBBLESTONE x10, with no loose cobblestone anywhere.
        ItemStack bundle = new ItemStack(Material.BUNDLE, 1);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        meta.addItem(new ItemStack(Material.COBBLESTONE, 10));
        bundle.setItemMeta(meta);
        player.getInventory().setItem(9, bundle);
        // No loose cobblestone in slots 9-35.

        // Must not throw; the bundle should remain at slot 9 with its contents intact.
        assertDoesNotThrow(() -> triggerMainStorage(player),
                "consolidate should not NPE when bundle contains an item type with no loose stacks");

        ItemStack slot9 = player.getInventory().getItem(9);
        assertNotNull(slot9, "Bundle should remain at slot 9");
        assertEquals(Material.BUNDLE, slot9.getType(), "Item at slot 9 should still be a bundle");
        assertTrue(bundleHas(slot9, Material.COBBLESTONE),
                "Bundle should still contain its original cobblestone");
    }

    // =========================================================================
    // Container (sorting off + bundle-in-containers on)
    // =========================================================================

    @Test
    void sortingOff_packingOn_container_consolidatesAndPacksInPlace() {
        PlayerMock player = addOpPlayer("Eve");
        plugin.getSortingPrefs().setEnabled(player, false);
        plugin.getSortingPrefs().setBundlePackInContainers(player, true);

        // Use STONE (maxStack=64). Place 2 full stacks (64+64=128) split across slots 1 and 3,
        // plus a remainder (10) in slot 4. The packer treats full stacks as "full stacks" that
        // stay loose, and packs the remainder into the bundle.
        // Total = 138 → 2 full stacks (128) stay loose, remainder 10 gets packed.
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, new ItemStack(Material.BUNDLE, 1));
        chest.setItem(1, new ItemStack(Material.STONE, 64)); // full stack
        chest.setItem(3, new ItemStack(Material.STONE, 64)); // full stack
        chest.setItem(4, new ItemStack(Material.STONE, 10)); // partial → to be packed
        // Slots 2, 5+ are empty.
        InventoryView view = player.openInventory(chest);

        triggerContainer(player, view);

        // Bundle should still be at slot 0.
        ItemStack bundleItem = chest.getItem(0);
        assertNotNull(bundleItem, "Bundle should remain at slot 0");
        assertEquals(Material.BUNDLE, bundleItem.getType());

        // Partial (10) should be packed into the bundle; slot 4 cleared.
        assertTrue(bundleHas(bundleItem, Material.STONE), "Bundle should contain packed stone remainder");
        assertNull(chest.getItem(4), "Partial-stack slot should be cleared after packing");

        // Full stacks remain in slots 1 and 3 (their original lanes).
        ItemStack slot1 = chest.getItem(1);
        assertNotNull(slot1, "Full stack should remain at slot 1");
        assertEquals(64, slot1.getAmount());
        ItemStack slot3 = chest.getItem(3);
        assertNotNull(slot3, "Full stack should remain at slot 3");
        assertEquals(64, slot3.getAmount());

        // Slot 2 was empty → must remain empty.
        assertNull(chest.getItem(2), "Empty slot 2 must stay empty");
    }

    @Test
    void sortingOff_packingOn_container_packingOff_onlyConsolidates() {
        // Even when packing is off for containers, consolidation of same-material stacks
        // does NOT happen when only inventory-packing is on.
        PlayerMock player = addOpPlayer("Frank");
        plugin.getSortingPrefs().setEnabled(player, false);
        plugin.getSortingPrefs().setBundlePackInInventory(player, true);  // inventory-only
        plugin.getSortingPrefs().setBundlePackInContainers(player, false); // containers off

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, new ItemStack(Material.BUNDLE, 1));
        chest.setItem(1, new ItemStack(Material.GRAVEL, 20));
        chest.setItem(3, new ItemStack(Material.GRAVEL, 10));
        ItemStack[] before = chest.getContents().clone();
        InventoryView view = player.openInventory(chest);

        triggerContainer(player, view);

        // Container should be unchanged (container packing is off).
        assertArrayEquals(before, chest.getContents(),
                "Container inventory should be unchanged when container packing is off");
    }

    // =========================================================================
    // Sorting on + packing on: existing sort/pack path still works
    // =========================================================================

    @Test
    void sortingOn_packingOn_existingSortPackBehaviorUnchanged() {
        PlayerMock player = addOpPlayer("Grace");
        // Sorting ON (default), packing ON.
        plugin.getSortingPrefs().setEnabled(player, true);
        plugin.getSortingPrefs().setBundlePackInInventory(player, true);
        plugin.getSortingPrefs().setSortOverItems(player, true);

        player.getInventory().setItem(9, new ItemStack(Material.BUNDLE, 1));
        player.getInventory().setItem(14, new ItemStack(Material.COBBLESTONE, 10)); // not in slot 9 area
        player.getInventory().setItem(20, new ItemStack(Material.DIRT, 5));

        triggerMainStorage(player);

        // After a full sort, items get re-laid-out: BUNDLE should be somewhere in 9..35,
        // Cobblestone partial should be in the bundle.
        Inventory inv = player.getInventory();
        ItemStack bundle = findBundle(inv, 9, 36);
        assertNotNull(bundle, "Bundle should remain after sort+pack");
        assertTrue(bundleHas(bundle, Material.COBBLESTONE), "Bundle should contain cobblestone partial");
        assertEquals(0, looseSlots(inv, 9, 36, Material.COBBLESTONE),
                "No loose cobblestone should remain after packing");
    }
}
