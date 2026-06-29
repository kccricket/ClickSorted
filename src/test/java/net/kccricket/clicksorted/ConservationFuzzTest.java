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

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Seeded conservation fuzz test: the strongest structural guard against item duplication and loss.
 * Generates 200 randomized chest inventories (fixed seed → deterministic, failures always reproduce)
 * and runs each through one of the three production sort/pack paths, asserting that the total
 * per-material item count (including bundle contents) is identical before and after.
 *
 * <p>Paths exercised, one-third each:
 * <ol>
 *   <li>Sort-with-layout: {@code SortEngine.sortAndMerge} via a real sort click (sorting on,
 *       packing off)</li>
 *   <li>Pack-and-sort: {@code packAndSort} via a real sort click (sorting on, packing on)</li>
 *   <li>In-place consolidation: {@code InPlacePacker.consolidate} via a real sort click (sorting
 *       off, packing on)</li>
 * </ol>
 *
 * <p>To reproduce a failure: fix the same seed ({@code 0xC1EA3_5EEDL}) and examine the iteration
 * index printed in the assertion message.
 */
class ConservationFuzzTest extends AbstractClickSortedTest {

    /** Stackable materials for the random inventory palette. */
    private static final Material[] STACKABLE = {
            Material.STONE, Material.DIRT, Material.COBBLESTONE, Material.SAND, Material.OAK_LOG
    };

    /**
     * Count all leaf (non-BUNDLE) materials across all inventory slots, including materials
     * stored inside BUNDLEs. BUNDLEs themselves are containers, not items; their slot presence
     * is tracked as part of the conservation invariant separately.
     */
    private Map<Material, Integer> countLeafItems(Inventory inv) {
        Map<Material, Integer> counts = new HashMap<>();
        for (ItemStack item : inv.getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            if (item.getType() == Material.BUNDLE) {
                BundleMeta meta = (BundleMeta) item.getItemMeta();
                for (ItemStack bundleItem : meta.getItems()) {
                    if (bundleItem != null && bundleItem.getType() != Material.AIR) {
                        counts.merge(bundleItem.getType(), bundleItem.getAmount(), Integer::sum);
                    }
                }
            } else {
                counts.merge(item.getType(), item.getAmount(), Integer::sum);
            }
        }
        return counts;
    }

    /** Count BUNDLE items in the inventory (should be conserved across all paths). */
    private int countBundles(Inventory inv) {
        int n = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() == Material.BUNDLE) n++;
        }
        return n;
    }

    /**
     * Generate a random 27-slot chest inventory.
     * Distribution per slot (roughly):
     * - 35 % empty
     * - 50 % partial stackable (amount 1–32)
     * - 10 % full stack of a stackable (amount 64)
     * - 5  % BUNDLE (possibly with 0-2 stackable items inside)
     */
    private Inventory generateRandomInventory(Random rng) {
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        for (int slot = 0; slot < chest.getSize(); slot++) {
            int roll = rng.nextInt(100);
            if (roll < 35) {
                // empty
            } else if (roll < 85) {
                // partial or full stackable stack
                Material mat = STACKABLE[rng.nextInt(STACKABLE.length)];
                int amount = (roll < 75) ? (rng.nextInt(63) + 1) : 64;
                chest.setItem(slot, new ItemStack(mat, amount));
            } else {
                // BUNDLE — possibly pre-populated with some items
                ItemStack bundle = new ItemStack(Material.BUNDLE, 1);
                BundleMeta meta = (BundleMeta) bundle.getItemMeta();
                int bundleItems = rng.nextInt(3); // 0, 1, or 2
                for (int k = 0; k < bundleItems; k++) {
                    Material mat = STACKABLE[rng.nextInt(STACKABLE.length)];
                    int amount = rng.nextInt(8) + 1;
                    meta.addItem(new ItemStack(mat, amount));
                }
                bundle.setItemMeta(meta);
                chest.setItem(slot, bundle);
            }
        }
        return chest;
    }

    /**
     * Fire a SWAP_OFFHAND sort trigger on slot 0 of the given chest view.
     * Uses slot 0 with {@code getCurrentItem()} overridden to a non-null sentinel so the
     * sort-over-items gate does not short-circuit (both sort and packing run regardless of
     * whether slot 0 is actually occupied in the randomized inventory).
     */
    private void triggerSort(PlayerMock player, InventoryView view) {
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 0, ClickType.SWAP_OFFHAND, InventoryAction.UNKNOWN) {
            @Override
            public ItemStack getCurrentItem() { return new ItemStack(Material.STONE, 1); }
        };
        server.getPluginManager().callEvent(event);
    }

    @Test
    void randomInventoriesConserveItemsAcrossAllPaths() {
        final Random rng = new Random(0xC1EA3_5EEDL);
        final int iterations = 200;

        PlayerMock player = addOpPlayer("Fuzzer");
        plugin.getSortingPrefs().setSortOverItems(player, true);

        for (int i = 0; i < iterations; i++) {
            Inventory chest = generateRandomInventory(rng);
            Map<Material, Integer> leafBefore = countLeafItems(chest);
            int bundlesBefore = countBundles(chest);

            // Three paths, cycled by iteration index.
            int path = i % 3;
            switch (path) {
                case 0 -> {
                    // Sort-with-layout: sorting on, bundle packing off
                    plugin.getSortingPrefs().setEnabled(player, true);
                    plugin.getSortingPrefs().setBundlePackInContainers(player, false);
                }
                case 1 -> {
                    // Pack-and-sort: sorting on, bundle packing on
                    plugin.getSortingPrefs().setEnabled(player, true);
                    plugin.getSortingPrefs().setBundlePackInContainers(player, true);
                }
                default -> {
                    // In-place consolidation: sorting off, bundle packing on
                    plugin.getSortingPrefs().setEnabled(player, false);
                    plugin.getSortingPrefs().setBundlePackInContainers(player, true);
                }
            }

            InventoryView view = player.openInventory(chest);
            triggerSort(player, view);

            String ctx = "iteration=" + i + " path=" + path + " seed=0xC1EA3_5EEDL";
            assertEquals(leafBefore, countLeafItems(chest),
                    "Item conservation violated: " + ctx);
            assertEquals(bundlesBefore, countBundles(chest),
                    "Bundle count changed (bundle created or destroyed): " + ctx);
        }
    }
}
