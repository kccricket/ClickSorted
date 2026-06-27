package net.kccricket.clicksorted;

import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.sort.SortEngine;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
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
    void sortAndMerge_fungibleSameMaterialDifferentLore_doNotMerge() {
        ItemStack a = loredItem(Material.FEATHER, 1, "Line one");
        ItemStack b = loredItem(Material.FEATHER, 1, "Line two");
        ItemStack plain = new ItemStack(Material.FEATHER, 1);

        List<ItemStack> out = SortEngine.sortAndMerge(Arrays.asList(a, b, plain), SortingMethod.NAME);

        List<ItemStack> feathers = out.stream().filter(is -> is.getType() == Material.FEATHER).toList();
        assertEquals(3, feathers.size(), "feathers with different lore must stay distinct");
        assertEquals(3, feathers.stream().mapToInt(ItemStack::getAmount).sum(), "no feathers lost");
    }

    @Test
    void sortAndMerge_fungibleSameMaterialDifferentCustomModelData_doNotMerge() {
        ItemStack a = customModelDataItem(Material.FEATHER, 1, 100);
        ItemStack b = customModelDataItem(Material.FEATHER, 1, 200);

        List<ItemStack> out = SortEngine.sortAndMerge(Arrays.asList(a, b), SortingMethod.NAME);

        List<ItemStack> feathers = out.stream().filter(is -> is.getType() == Material.FEATHER).toList();
        assertEquals(2, feathers.size(), "feathers with different custom model data must stay distinct");
        assertEquals(2, feathers.stream().mapToInt(ItemStack::getAmount).sum(), "no feathers lost");
    }

    @Test
    void sortAndMerge_fungibleSameMaterialDifferentExternalPdc_doNotMerge() {
        // Simulates items tagged by an external plugin. NamespacedKey.fromString("otherplugin:…")
        // produces a key with namespace "otherplugin", identical in every way to what a real plugin
        // would produce via new NamespacedKey(otherPlugin, …) where otherPlugin.getName() = "otherplugin".
        NamespacedKey key = NamespacedKey.fromString("otherplugin:custom_flag");
        ItemStack tagged = pdcItem(Material.FEATHER, 1, key, "special");
        ItemStack plain = new ItemStack(Material.FEATHER, 1);
        ItemStack differentValue = pdcItem(Material.FEATHER, 1, key, "other");

        List<ItemStack> out = SortEngine.sortAndMerge(Arrays.asList(tagged, plain, differentValue), SortingMethod.NAME);

        List<ItemStack> feathers = out.stream().filter(is -> is.getType() == Material.FEATHER).toList();
        assertEquals(3, feathers.size(), "feathers differing by external PDC must stay distinct");
        assertEquals(3, feathers.stream().mapToInt(ItemStack::getAmount).sum(), "no feathers lost");
    }

    @Test
    void sortAndMerge_fungibleSameMaterialSameExternalPdc_merge() {
        // Identical external-plugin PDC: stacks should still be quantity-merged and the PDC preserved.
        NamespacedKey key = NamespacedKey.fromString("otherplugin:custom_flag");
        ItemStack a = pdcItem(Material.FEATHER, 3, key, "special");
        ItemStack b = pdcItem(Material.FEATHER, 7, key, "special");

        List<ItemStack> out = SortEngine.sortAndMerge(Arrays.asList(a, b), SortingMethod.NAME);

        List<ItemStack> feathers = out.stream().filter(is -> is.getType() == Material.FEATHER).toList();
        assertEquals(1, feathers.size(), "identical PDC feathers merge into one stack");
        assertEquals(10, feathers.get(0).getAmount(), "quantities summed correctly");
        assertEquals("special",
                feathers.get(0).getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING),
                "merged stack retains external PDC value");
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

    @Test
    void sortAndMerge_fungibleSameMaterialDifferentItemName_doNotMerge() {
        // item_name is a separate data component from custom_name (anvil name). Two feathers with
        // distinct item_name values (+ a plain one) must stay three discrete stacks — merging would
        // silently destroy the data-pack/plugin base name.
        ItemStack plain = new ItemStack(Material.FEATHER, 1);
        ItemStack nameX = itemNamedItem(Material.FEATHER, 1, "X");
        ItemStack nameY = itemNamedItem(Material.FEATHER, 1, "Y");

        List<ItemStack> out = SortEngine.sortAndMerge(Arrays.asList(plain, nameX, nameY), SortingMethod.NAME);

        List<ItemStack> feathers = out.stream().filter(is -> is.getType() == Material.FEATHER).toList();
        assertEquals(3, feathers.size(), "plain + two differently item-named feathers must remain distinct");
        assertEquals(3, feathers.stream().mapToInt(ItemStack::getAmount).sum(), "no feathers lost");
    }

    @Test
    void sortAndMerge_fungibleSameItemName_merge() {
        // The flip side: identical item_name still quantity-merges, and the merged stack keeps the
        // item_name (guards against a future regression that would over-split).
        ItemStack a = itemNamedItem(Material.FEATHER, 10, "X");
        ItemStack b = itemNamedItem(Material.FEATHER, 5, "X");

        List<ItemStack> out = SortEngine.sortAndMerge(Arrays.asList(a, b), SortingMethod.NAME);

        List<ItemStack> feathers = out.stream().filter(is -> is.getType() == Material.FEATHER).toList();
        assertEquals(1, feathers.size(), "same material and same item_name merge into one stack");
        assertEquals(15, feathers.get(0).getAmount());
        assertTrue(feathers.get(0).getItemMeta().hasItemName(), "merged stack retains the item_name");
    }

    @Test
    void sortAndMerge_itemNameVsCustomNameSameText_doNotMerge() {
        // An item_name feather and a custom_name feather with the SAME text are still distinct: they
        // differ by which data component carries the name, so their ItemMeta differs.
        ItemStack itemNamed = itemNamedItem(Material.FEATHER, 1, "Foo");
        ItemStack customNamed = namedItem(Material.FEATHER, 1, "Foo");

        List<ItemStack> out = SortEngine.sortAndMerge(Arrays.asList(itemNamed, customNamed), SortingMethod.NAME);

        List<ItemStack> feathers = out.stream().filter(is -> is.getType() == Material.FEATHER).toList();
        assertEquals(2, feathers.size(), "item_name vs custom_name with same text must stay distinct");
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

    private static ItemStack loredItem(Material mat, int amount, String loreLine) {
        ItemStack is = new ItemStack(mat, amount);
        ItemMeta meta = is.getItemMeta();
        meta.lore(List.of(Component.text(loreLine)));
        is.setItemMeta(meta);
        return is;
    }

    private static ItemStack customModelDataItem(Material mat, int amount, int modelData) {
        ItemStack is = new ItemStack(mat, amount);
        ItemMeta meta = is.getItemMeta();
        meta.setCustomModelData(modelData);
        is.setItemMeta(meta);
        return is;
    }

    private static ItemStack pdcItem(Material mat, int amount, NamespacedKey key, String value) {
        ItemStack is = new ItemStack(mat, amount);
        ItemMeta meta = is.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
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

    private static ItemStack itemNamedItem(Material mat, int amount, String name) {
        ItemStack is = new ItemStack(mat, amount);
        ItemMeta meta = is.getItemMeta();
        meta.itemName(Component.text(name));
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
