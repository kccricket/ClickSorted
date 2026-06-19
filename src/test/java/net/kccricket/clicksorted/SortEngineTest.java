package net.kccricket.clicksorted;

import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.sort.SortEngine;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
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

    @Test
    void sortAndMerge_fungibleSameMaterialDifferentMeta_doNotMerge() {
        // Feathers are fungible (maxStackSize 64), so merging is governed entirely by SortKey equality.
        // A plain feather and two feathers with distinct custom metadata must stay as three separate
        // stacks — merging any of them would silently destroy a custom/plugin item's metadata.
        ItemStack plain = new ItemStack(Material.FEATHER, 1);
        ItemStack metaX = namedItem(Material.FEATHER, 1, "X");
        ItemStack metaY = namedItem(Material.FEATHER, 1, "Y");

        List<ItemStack> out = SortEngine.sortAndMerge(Arrays.asList(plain, metaX, metaY), SortingMethod.NAME);

        List<ItemStack> feathers = out.stream().filter(is -> is.getType() == Material.FEATHER).toList();
        assertEquals(3, feathers.size(), "plain + two differently-named feathers must remain distinct");
        int total = feathers.stream().mapToInt(ItemStack::getAmount).sum();
        assertEquals(3, total, "no feathers lost");
    }

    @Test
    void sortAndMerge_fungibleSameMaterialSameMeta_merge() {
        // The flip side: identical material AND identical metadata are still quantity-merged, and the
        // merged stack keeps the custom metadata.
        ItemStack a = namedItem(Material.FEATHER, 10, "X");
        ItemStack b = namedItem(Material.FEATHER, 5, "X");

        List<ItemStack> out = SortEngine.sortAndMerge(Arrays.asList(a, b), SortingMethod.NAME);

        List<ItemStack> feathers = out.stream().filter(is -> is.getType() == Material.FEATHER).toList();
        assertEquals(1, feathers.size(), "same material and same meta merge into one stack");
        assertEquals(15, feathers.get(0).getAmount());
        assertTrue(feathers.get(0).getItemMeta().hasDisplayName(), "merged stack retains the custom name");
    }

    @Test
    void sortAndMerge_fungibleSameMaterialDifferentEnchants_doNotMerge() {
        // Enchantments are part of the meta, so two otherwise-identical fungible stacks that carry
        // different enchantments must remain distinct rather than collapse and lose one's enchantment.
        // (Enchanted on a feather via unsafe addEnchant so the item stays fungible — enchanted tools are
        // non-stackable and would take the discrete path instead.)
        ItemStack sharp = enchantedFeather(Enchantment.SHARPNESS, 1);
        ItemStack unbreaking = enchantedFeather(Enchantment.UNBREAKING, 1);

        List<ItemStack> out = SortEngine.sortAndMerge(Arrays.asList(sharp, unbreaking), SortingMethod.NAME);

        List<ItemStack> feathers = out.stream().filter(is -> is.getType() == Material.FEATHER).toList();
        assertEquals(2, feathers.size(), "feathers with different enchantments must stay distinct");
        assertEquals(2, feathers.stream().mapToInt(ItemStack::getAmount).sum(), "no feathers lost");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ItemStack enchantedFeather(Enchantment enchant, int level) {
        ItemStack is = new ItemStack(Material.FEATHER, 1);
        ItemMeta meta = is.getItemMeta();
        meta.addEnchant(enchant, level, true);
        is.setItemMeta(meta);
        return is;
    }

    private static ItemStack namedItem(Material mat, int amount, String name) {
        ItemStack is = new ItemStack(mat, amount);
        ItemMeta meta = is.getItemMeta();
        meta.displayName(Component.text(name));
        is.setItemMeta(meta);
        return is;
    }

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
