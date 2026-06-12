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

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the unified pack-and-sort pipeline: bundle packing folded into a normal sort,
 * gated by the per-player {@code bundle inventory} / {@code bundle others} preferences.
 */
class BundleSortIntegrationTest extends AbstractClickSortedTest {

    /**
     * Fire a SWAP_OFFHAND click on the player's main storage (rawSlot 27 → main slot 9), with a
     * forced non-null currentItem so the listener's null-guard does not short-circuit.
     */
    private void sortMainStorage(PlayerMock player) {
        plugin.getSortingPrefs().setSortOverItems(player, true);
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        InventoryView view = player.openInventory(chest);
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 27, ClickType.SWAP_OFFHAND, InventoryAction.UNKNOWN) {
            @Override
            public ItemStack getCurrentItem() {
                return new ItemStack(Material.STONE, 1);
            }
        };
        server.getPluginManager().callEvent(event);
    }

    private static int looseSlots(Inventory inv, int from, int to, Material mat) {
        int n = 0;
        for (int i = from; i < to; i++) {
            ItemStack is = inv.getItem(i);
            if (is != null && is.getType() == mat) n++;
        }
        return n;
    }

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

    // -------------------------------------------------------------------------
    // Player inventory packing
    // -------------------------------------------------------------------------

    @Test
    void inventoryPackingOn_packsPartialsAndSorts() {
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setBundlePackInventory(player, true);

        player.getInventory().setItem(9, new ItemStack(Material.BUNDLE, 1));
        player.getInventory().setItem(10, new ItemStack(Material.COBBLESTONE, 10));
        player.getInventory().setItem(11, new ItemStack(Material.DIRT, 5));

        sortMainStorage(player);

        assertEquals(0, looseSlots(player.getInventory(), 9, 36, Material.COBBLESTONE),
                "Cobblestone partial should be packed away");
        assertEquals(0, looseSlots(player.getInventory(), 9, 36, Material.DIRT),
                "Dirt partial should be packed away");
        ItemStack bundle = findBundle(player.getInventory(), 9, 36);
        assertNotNull(bundle, "Bundle should still be present");
        assertTrue(bundleHas(bundle, Material.COBBLESTONE) && bundleHas(bundle, Material.DIRT),
                "Bundle should hold the packed partials");
    }

    @Test
    void inventoryPackingOff_onlySortsLeavesBundleEmpty() {
        PlayerMock player = addOpPlayer("Alice");
        // packing disabled by default; be explicit
        plugin.getSortingPrefs().setBundlePackInventory(player, false);

        player.getInventory().setItem(9, new ItemStack(Material.BUNDLE, 1));
        player.getInventory().setItem(10, new ItemStack(Material.COBBLESTONE, 10));

        sortMainStorage(player);

        ItemStack bundle = findBundle(player.getInventory(), 9, 36);
        assertNotNull(bundle);
        assertTrue(((BundleMeta) bundle.getItemMeta()).getItems().isEmpty(),
                "With packing off, the bundle stays empty");
        assertEquals(10, player.getInventory().all(Material.COBBLESTONE).values().stream()
                .mapToInt(ItemStack::getAmount).sum(), "Cobblestone stays loose");
    }

    @Test
    void mergeHappensWithoutBundle() {
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setBundlePackInventory(player, true);

        // No bundle present — the sort should still consolidate the two partials.
        player.getInventory().setItem(9, new ItemStack(Material.COBBLESTONE, 40));
        player.getInventory().setItem(11, new ItemStack(Material.COBBLESTONE, 12));

        sortMainStorage(player);

        assertEquals(1, looseSlots(player.getInventory(), 9, 36, Material.COBBLESTONE),
                "Two partials should merge into a single stack even with packing on and no bundle");
    }

    @Test
    void hotbarBundleNotUsedForMainStorageSort() {
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setBundlePackInventory(player, true);

        // Bundle in the hotbar (region-scoped packing must not use it for a main-storage sort).
        player.getInventory().setItem(0, new ItemStack(Material.BUNDLE, 1));
        player.getInventory().setItem(9, new ItemStack(Material.COBBLESTONE, 10));

        sortMainStorage(player);

        ItemStack hotbarBundle = player.getInventory().getItem(0);
        assertNotNull(hotbarBundle);
        assertEquals(Material.BUNDLE, hotbarBundle.getType());
        assertTrue(((BundleMeta) hotbarBundle.getItemMeta()).getItems().isEmpty(),
                "Hotbar bundle must not absorb a main-storage item");
        assertEquals(10, player.getInventory().all(Material.COBBLESTONE).values().stream()
                .mapToInt(ItemStack::getAmount).sum(), "Cobblestone stays loose with no in-region bundle");
    }

    @Test
    void lockedBundleAndItemUntouched() {
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setBundlePackInventory(player, true);

        player.getInventory().setItem(20, new ItemStack(Material.COBBLESTONE, 12));
        player.getInventory().setItem(21, new ItemStack(Material.BUNDLE, 1));
        plugin.getSortingPrefs().setLockedSlots(player, Set.of(20, 21));
        // An unlocked partial with no unlocked bundle in range stays loose.
        player.getInventory().setItem(25, new ItemStack(Material.DIRT, 8));

        sortMainStorage(player);

        ItemStack locked = player.getInventory().getItem(20);
        assertNotNull(locked, "Locked item must stay");
        assertEquals(Material.COBBLESTONE, locked.getType());
        assertEquals(12, locked.getAmount());

        ItemStack lockedBundle = player.getInventory().getItem(21);
        assertNotNull(lockedBundle, "Locked bundle must stay");
        assertEquals(Material.BUNDLE, lockedBundle.getType());
        assertTrue(((BundleMeta) lockedBundle.getItemMeta()).getItems().isEmpty(),
                "Locked bundle must never be used as a bin");
    }

    @Test
    void combineThenPackFreesSlots() {
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setBundlePackInventory(player, true);

        // Empty bundle + five 32-count cobblestone (=160). Merge → 64+64+32; the 32 (weight 32)
        // packs into the bundle → two full loose stacks + a bundle = 3 occupied slots.
        player.getInventory().setItem(9, new ItemStack(Material.BUNDLE, 1));
        for (int i = 0; i < 5; i++) {
            player.getInventory().setItem(10 + i, new ItemStack(Material.COBBLESTONE, 32));
        }

        sortMainStorage(player);

        assertEquals(2, looseSlots(player.getInventory(), 9, 36, Material.COBBLESTONE),
                "Two full loose cobblestone stacks remain");
        for (int i = 9; i < 36; i++) {
            ItemStack is = player.getInventory().getItem(i);
            if (is != null && is.getType() == Material.COBBLESTONE) {
                assertEquals(64, is.getAmount(), "Loose cobblestone stacks should be full");
            }
        }
        ItemStack bundle = findBundle(player.getInventory(), 9, 36);
        assertNotNull(bundle);
        assertTrue(bundleHas(bundle, Material.COBBLESTONE), "The 32-remainder packs into the bundle");
    }

    // -------------------------------------------------------------------------
    // CONTROL_DROP click method (Ctrl+Q)
    // -------------------------------------------------------------------------

    @Test
    void controlDropClickMethodTriggersSortAndCancels() {
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setClickMethod(player, net.kccricket.clicksorted.model.ClickMethod.CONTROL_DROP);
        plugin.getSortingPrefs().setBundlePackInventory(player, true);
        plugin.getSortingPrefs().setSortOverItems(player, true);

        player.getInventory().setItem(9, new ItemStack(Material.BUNDLE, 1));
        player.getInventory().setItem(10, new ItemStack(Material.COBBLESTONE, 10));

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        InventoryView view = player.openInventory(chest);
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 27, ClickType.CONTROL_DROP, InventoryAction.UNKNOWN) {
            @Override
            public ItemStack getCurrentItem() {
                return new ItemStack(Material.COBBLESTONE, 10);
            }
        };
        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled(), "Ctrl+Q sort must cancel the vanilla drop");
        ItemStack bundle = findBundle(player.getInventory(), 9, 36);
        assertNotNull(bundle);
        assertTrue(bundleHas(bundle, Material.COBBLESTONE), "Ctrl+Q should pack-and-sort");
    }

    // -------------------------------------------------------------------------
    // Container ("others") packing
    // -------------------------------------------------------------------------

    @Test
    void othersPackingOn_packsChestBundle() {
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setBundlePackOthers(player, true);
        plugin.getSortingPrefs().setSortOverItems(player, true);

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, new ItemStack(Material.BUNDLE, 1));
        chest.setItem(1, new ItemStack(Material.COBBLESTONE, 10));
        InventoryView view = player.openInventory(chest);

        fireClick(view, ClickType.SWAP_OFFHAND, 0);

        ItemStack bundle = findBundle(chest, 0, chest.getSize());
        assertNotNull(bundle, "Bundle should still be present in the chest");
        assertTrue(bundleHas(bundle, Material.COBBLESTONE), "Chest bundle should absorb the partial");
        assertEquals(0, looseSlots(chest, 0, chest.getSize(), Material.COBBLESTONE),
                "Cobblestone partial packed away");
    }

    @Test
    void othersPackingOff_leavesChestBundleEmpty() {
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setBundlePackOthers(player, false);
        plugin.getSortingPrefs().setSortOverItems(player, true);

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, new ItemStack(Material.BUNDLE, 1));
        chest.setItem(1, new ItemStack(Material.COBBLESTONE, 10));
        InventoryView view = player.openInventory(chest);

        fireClick(view, ClickType.SWAP_OFFHAND, 0);

        ItemStack bundle = findBundle(chest, 0, chest.getSize());
        assertNotNull(bundle);
        assertTrue(((BundleMeta) bundle.getItemMeta()).getItems().isEmpty(),
                "With others packing off, the chest bundle stays empty");
        assertEquals(1, looseSlots(chest, 0, chest.getSize(), Material.COBBLESTONE),
                "Cobblestone stays loose");
    }
}
