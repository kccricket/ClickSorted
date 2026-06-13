package net.kccricket.clicksorted;

import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.sort.SortEngine;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SortEngine#sortAndMerge(java.util.Collection, SortingMethod)}: fungible stacks are
 * quantity-merged by key, but non-fungible, content-bearing items (bundles and shulker boxes — both
 * {@code maxStackSize <= 1}) must be kept as discrete stacks and never collapsed, so each retains its
 * own contents regardless of meta-equality.
 *
 * Uses MockBukkit (via AbstractClickSortedTest) because ItemStack/meta are server-side objects.
 */
class SortEngineTest extends AbstractClickSortedTest {

    @Test
    void sortAndMerge_fungibleStacksMergeByKey() {
        List<ItemStack> items = Arrays.asList(
                new ItemStack(Material.COBBLESTONE, 42),
                new ItemStack(Material.COBBLESTONE, 2),
                new ItemStack(Material.DIRT, 3));

        List<ItemStack> out = SortEngine.sortAndMerge(items, SortingMethod.NAME);

        int cobble = 0;
        int dirt = 0;
        for (ItemStack is : out) {
            if (is.getType() == Material.COBBLESTONE) cobble += is.getAmount();
            if (is.getType() == Material.DIRT) dirt += is.getAmount();
        }
        assertEquals(44, cobble, "Like fungible stacks merge");
        assertEquals(3, dirt);
        assertEquals(2, out.size(), "One cobble stack + one dirt stack");
    }

    @Test
    void sortAndMerge_distinctBundlesBothSurvive() {
        ItemStack a = bundleOf(new ItemStack(Material.DIAMOND, 5));
        ItemStack b = bundleOf(new ItemStack(Material.GOLD_INGOT, 7));

        List<ItemStack> out = SortEngine.sortAndMerge(Arrays.asList(a, b), SortingMethod.NAME);

        List<ItemStack> bundles = bundlesIn(out);
        assertEquals(2, bundles.size(), "Both bundles survive");
        assertTrue(bundlesContain(bundles, Material.DIAMOND, 5), "Diamond bundle's contents preserved");
        assertTrue(bundlesContain(bundles, Material.GOLD_INGOT, 7), "Gold bundle's contents preserved");
    }

    @Test
    void sortAndMerge_identicalBundlesRemainSeparate() {
        ItemStack a = bundleOf(new ItemStack(Material.DIAMOND, 5));
        ItemStack b = bundleOf(new ItemStack(Material.DIAMOND, 5));

        List<ItemStack> out = SortEngine.sortAndMerge(Arrays.asList(a, b), SortingMethod.NAME);

        assertEquals(2, bundlesIn(out).size(), "Two identical bundles must not collapse into one");
    }

    @Test
    void sortAndMerge_distinctShulkersBothSurvive() {
        ItemStack a = shulkerOf(new ItemStack(Material.REDSTONE, 12));
        ItemStack b = shulkerOf(new ItemStack(Material.EMERALD, 4));

        List<ItemStack> out = SortEngine.sortAndMerge(Arrays.asList(a, b), SortingMethod.NAME);

        long shulkers = out.stream().filter(is -> is.getType() == Material.SHULKER_BOX).count();
        assertEquals(2, shulkers, "Both shulker boxes survive");
        assertTrue(shulkersContain(out, Material.REDSTONE, 12), "Redstone shulker's contents preserved");
        assertTrue(shulkersContain(out, Material.EMERALD, 4), "Emerald shulker's contents preserved");
    }

    @Test
    void sortAndMerge_identicalShulkersRemainSeparate() {
        ItemStack a = shulkerOf(new ItemStack(Material.REDSTONE, 12));
        ItemStack b = shulkerOf(new ItemStack(Material.REDSTONE, 12));

        List<ItemStack> out = SortEngine.sortAndMerge(Arrays.asList(a, b), SortingMethod.NAME);

        long shulkers = out.stream().filter(is -> is.getType() == Material.SHULKER_BOX).count();
        assertEquals(2, shulkers, "Two identical shulker boxes must not collapse into one");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ItemStack bundleOf(ItemStack content) {
        ItemStack bundle = new ItemStack(Material.BUNDLE, 1);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        meta.setItems(List.of(content));
        bundle.setItemMeta(meta);
        return bundle;
    }

    private static ItemStack shulkerOf(ItemStack content) {
        ItemStack box = new ItemStack(Material.SHULKER_BOX, 1);
        BlockStateMeta meta = (BlockStateMeta) box.getItemMeta();
        ShulkerBox state = (ShulkerBox) meta.getBlockState();
        state.getInventory().setItem(0, content);
        meta.setBlockState(state);
        box.setItemMeta(meta);
        return box;
    }

    private static List<ItemStack> bundlesIn(List<ItemStack> items) {
        return items.stream().filter(is -> is.getType() == Material.BUNDLE).toList();
    }

    private static boolean bundlesContain(List<ItemStack> bundles, Material mat, int amount) {
        for (ItemStack bundle : bundles) {
            for (ItemStack content : ((BundleMeta) bundle.getItemMeta()).getItems()) {
                if (content != null && content.getType() == mat && content.getAmount() == amount) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean shulkersContain(List<ItemStack> items, Material mat, int amount) {
        for (ItemStack is : items) {
            if (is.getType() != Material.SHULKER_BOX) continue;
            ShulkerBox state = (ShulkerBox) ((BlockStateMeta) is.getItemMeta()).getBlockState();
            for (ItemStack content : state.getInventory().getContents()) {
                if (content != null && content.getType() == mat && content.getAmount() == amount) {
                    return true;
                }
            }
        }
        return false;
    }
}
