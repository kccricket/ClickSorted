package net.kccricket.clicksorted;

import net.kccricket.clicksorted.model.FillAxis;
import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.model.StartCorner;
import net.kccricket.clicksorted.sort.SortEngine;
import net.kccricket.clicksorted.sort.TreemapPacker;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link TreemapPacker} on a 9×6 double chest (54 slots, base 0) — the geometry where the
 * treemap's proportional blocks are actually visible. Builds inputs through the real
 * {@link SortEngine} pipeline (so stacks are merged/name-sorted exactly as in production), then
 * inspects the slot→stack placement. Extends {@link AbstractClickSortedTest} because ItemStack/meta
 * and item-name lookup are server-side.
 */
class TreemapPackerTest extends AbstractClickSortedTest {

    private static final int WIDTH = 9;
    private static final int ROWS = 6;
    private static final int CELLS = WIDTH * ROWS;

    /** A 54-slot double chest's slots, optionally with some removed (e.g. locked). */
    private static Set<Integer> chestSlots(int... removed) {
        Set<Integer> slots = new TreeSet<>(IntStream.range(0, CELLS).boxed().collect(Collectors.toList()));
        for (int r : removed) {
            slots.remove(r);
        }
        return slots;
    }

    /** {@code n} full (64) stacks of {@code mat}. */
    private List<ItemStack> fullStacks(Material mat, int n) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(stack(mat, 64));
        }
        return out;
    }

    private List<ItemStack> sorted(List<ItemStack> raw) {
        return SortEngine.sortAndMerge(raw, SortingMethod.TREEMAP);
    }

    private Material typeAt(Map<Integer, ItemStack> placement, int slot) {
        ItemStack item = placement.get(slot);
        assertNotNull(item, "expected an item at slot " + slot);
        return item.getType();
    }

    /** The slots a given material occupies in the placement. */
    private Set<Integer> slotsOf(Map<Integer, ItemStack> placement, Material mat) {
        Set<Integer> slots = new TreeSet<>();
        placement.forEach((slot, item) -> {
            if (item.getType() == mat) {
                slots.add(slot);
            }
        });
        return slots;
    }

    /** True if the slots form a single 4-connected region on the 9-wide grid (no split, no wrap). */
    private boolean isContiguous(Set<Integer> slots) {
        if (slots.isEmpty()) {
            return true;
        }
        Deque<Integer> queue = new java.util.ArrayDeque<>();
        Set<Integer> seen = new java.util.HashSet<>();
        int first = slots.iterator().next();
        queue.add(first);
        seen.add(first);
        while (!queue.isEmpty()) {
            int s = queue.poll();
            int row = s / WIDTH, col = s % WIDTH;
            int[][] neighbours = {{row - 1, col}, {row + 1, col}, {row, col - 1}, {row, col + 1}};
            for (int[] n : neighbours) {
                if (n[0] < 0 || n[0] >= ROWS || n[1] < 0 || n[1] >= WIDTH) {
                    continue;
                }
                int ns = n[0] * WIDTH + n[1];
                if (slots.contains(ns) && seen.add(ns)) {
                    queue.add(ns);
                }
            }
        }
        return seen.size() == slots.size();
    }

    /** True if the material fills its entire bounding box — i.e. it is a solid rectangle. */
    private boolean isSolidRectangle(Map<Integer, ItemStack> placement, Material mat) {
        Set<Integer> slots = slotsOf(placement, mat);
        if (slots.isEmpty()) {
            return false;
        }
        int minR = ROWS, maxR = 0, minC = WIDTH, maxC = 0;
        for (int s : slots) {
            minR = Math.min(minR, s / WIDTH);
            maxR = Math.max(maxR, s / WIDTH);
            minC = Math.min(minC, s % WIDTH);
            maxC = Math.max(maxC, s % WIDTH);
        }
        return slots.size() == (maxR - minR + 1) * (maxC - minC + 1);
    }

    /** How many placed cells each material occupies. */
    private Map<Material, Integer> cellCounts(Map<Integer, ItemStack> placement) {
        Map<Material, Integer> counts = new HashMap<>();
        for (ItemStack item : placement.values()) {
            counts.merge(item.getType(), 1, (a, b) -> Integer.sum(a, b));
        }
        return counts;
    }

    private int minRow(Map<Integer, ItemStack> placement, Material mat) {
        return slotsOf(placement, mat).stream().mapToInt(s -> s / WIDTH).min().orElseThrow();
    }

    private int maxRow(Map<Integer, ItemStack> placement, Material mat) {
        return slotsOf(placement, mat).stream().mapToInt(s -> s / WIDTH).max().orElseThrow();
    }

    private int minCol(Map<Integer, ItemStack> placement, Material mat) {
        return slotsOf(placement, mat).stream().mapToInt(s -> s % WIDTH).min().orElseThrow();
    }

    private int maxCol(Map<Integer, ItemStack> placement, Material mat) {
        return slotsOf(placement, mat).stream().mapToInt(s -> s % WIDTH).max().orElseThrow();
    }

    @Test
    void verticalAxisStacksBlocksDownwardInsteadOfRightward() {
        // Same input, two layouts. DIRT(12) is the dominant block; STONE(6) is the runner-up. A
        // HORIZONTAL axis flows the runner-up to the RIGHT of the dominant block (shelves are rows); a
        // VERTICAL axis flows it BELOW (shelves are columns). Both anchor the dominant block top-left.
        List<ItemStack> raw = new ArrayList<>();
        raw.addAll(fullStacks(Material.DIRT, 12));
        raw.addAll(fullStacks(Material.STONE, 6));

        Map<Integer, ItemStack> horizontal =
                TreemapPacker.pack(sorted(raw), chestSlots(), 0, WIDTH, ROWS, StartCorner.TOP_LEFT, FillAxis.HORIZONTAL);
        assertEquals(Material.DIRT, typeAt(horizontal, 0), "the dominant block anchors the top-left corner");
        assertTrue(minCol(horizontal, Material.STONE) > maxCol(horizontal, Material.DIRT),
                "HORIZONTAL places the runner-up to the right of the dominant block");

        Map<Integer, ItemStack> vertical =
                TreemapPacker.pack(sorted(raw), chestSlots(), 0, WIDTH, ROWS, StartCorner.TOP_LEFT, FillAxis.VERTICAL);
        assertEquals(Material.DIRT, typeAt(vertical, 0), "the dominant block still anchors the top-left corner");
        assertTrue(minRow(vertical, Material.STONE) > maxRow(vertical, Material.DIRT),
                "VERTICAL places the runner-up below the dominant block");

        // The vertical layout is still complete and every type stays one contiguous block.
        assertEquals(sorted(raw).size(), vertical.size(), "every stack is placed under the vertical axis");
        for (Material mat : new Material[]{Material.DIRT, Material.STONE}) {
            assertTrue(isContiguous(slotsOf(vertical, mat)), mat + " must be one contiguous block");
        }
    }

    @Test
    void largestTypeAnchorsTopLeftAndEveryStackIsPlaced() {
        // Distinct counts → unambiguous ranks: DIRT(20) STONE(8) SAND(4).
        List<ItemStack> raw = new ArrayList<>();
        raw.addAll(fullStacks(Material.DIRT, 20));
        raw.addAll(fullStacks(Material.STONE, 8));
        raw.addAll(fullStacks(Material.SAND, 4));
        List<ItemStack> sortedStacks = sorted(raw);

        Map<Integer, ItemStack> placement =
                TreemapPacker.pack(sortedStacks, chestSlots(), 0, WIDTH, ROWS, StartCorner.TOP_LEFT, FillAxis.HORIZONTAL);

        // The most-numerous type owns the anchor corner.
        assertEquals(Material.DIRT, typeAt(placement, 0), "the dominant type anchors the top-left corner");

        // Each type occupies exactly its stack count in cells (remainders are ragged edges, not holes),
        // and every stack is placed.
        Map<Material, Integer> counts = cellCounts(placement);
        assertEquals(20, counts.get(Material.DIRT), "DIRT occupies one cell per stack");
        assertEquals(8, counts.get(Material.STONE), "STONE occupies one cell per stack");
        assertEquals(4, counts.get(Material.SAND), "SAND occupies one cell per stack");
        assertEquals(sortedStacks.size(), placement.size(), "every stack is placed, nothing dropped");
    }

    @Test
    void primeCountStillPlacesEveryStackWithNoGap() {
        // A single type whose stack count (a prime) cannot tile cleanly must still place all its stacks
        // with no hole inside the layout.
        List<ItemStack> sortedStacks = sorted(fullStacks(Material.DIRT, 7));

        Map<Integer, ItemStack> placement =
                TreemapPacker.pack(sortedStacks, chestSlots(), 0, WIDTH, ROWS, StartCorner.TOP_LEFT, FillAxis.HORIZONTAL);

        assertEquals(Material.DIRT, typeAt(placement, 0), "the lone type anchors the start corner");
        assertEquals(7, placement.size(), "all 7 stacks placed despite the prime count");
        assertEquals(7, cellCounts(placement).get(Material.DIRT), "no stack is lost to a gap");
    }

    @Test
    void startCornerReflectsTheAnchor() {
        List<ItemStack> raw = new ArrayList<>();
        raw.addAll(fullStacks(Material.DIRT, 20));
        raw.addAll(fullStacks(Material.STONE, 8));
        List<ItemStack> sortedStacks = sorted(raw);

        // TOP_LEFT anchors the largest block at slot 0; BOTTOM_RIGHT reflects it to the last slot.
        Map<Integer, ItemStack> topLeft =
                TreemapPacker.pack(sortedStacks, chestSlots(), 0, WIDTH, ROWS, StartCorner.TOP_LEFT, FillAxis.HORIZONTAL);
        assertEquals(Material.DIRT, typeAt(topLeft, 0), "DIRT anchors the top-left corner");

        Map<Integer, ItemStack> bottomRight =
                TreemapPacker.pack(sorted(raw), chestSlots(), 0, WIDTH, ROWS, StartCorner.BOTTOM_RIGHT, FillAxis.HORIZONTAL);
        assertEquals(Material.DIRT, typeAt(bottomRight, CELLS - 1),
                "DIRT anchors the bottom-right corner when start-corner is reflected");
    }

    @Test
    void lockedCellInsideABlockIsSkippedAndItsStackStillPlaced() {
        // Lock slot 1, which sits inside the dominant DIRT block. That cell must be left empty, and the
        // displaced DIRT stack must still be placed elsewhere — nothing is dropped.
        List<ItemStack> raw = new ArrayList<>();
        raw.addAll(fullStacks(Material.DIRT, 20));
        raw.addAll(fullStacks(Material.STONE, 8));
        List<ItemStack> sortedStacks = sorted(raw);

        Map<Integer, ItemStack> placement =
                TreemapPacker.pack(sortedStacks, chestSlots(1), 0, WIDTH, ROWS, StartCorner.TOP_LEFT, FillAxis.HORIZONTAL);

        assertFalse(placement.containsKey(1), "the locked slot is never written");
        assertEquals(sortedStacks.size(), placement.size(), "all stacks placed despite the locked cell");
        assertEquals(20, cellCounts(placement).get(Material.DIRT), "the displaced DIRT stack lands in a free cell");
    }

    @Test
    void everyTypeIsAContiguousBlockWhenRoomy() {
        // The reported case: 11/7/7 stacks in a 54-cell chest. Each type must be a single connected
        // region — no type tucks into, wraps around, or splits across another (the original complaint).
        List<ItemStack> raw = new ArrayList<>();
        raw.addAll(fullStacks(Material.DIRT, 11));
        raw.addAll(fullStacks(Material.STONE, 7));
        raw.addAll(fullStacks(Material.SAND, 7));
        List<ItemStack> sortedStacks = sorted(raw);

        Map<Integer, ItemStack> placement =
                TreemapPacker.pack(sortedStacks, chestSlots(), 0, WIDTH, ROWS, StartCorner.TOP_LEFT, FillAxis.HORIZONTAL);

        assertEquals(sortedStacks.size(), placement.size(), "every stack is placed");
        for (Material mat : new Material[]{Material.DIRT, Material.STONE, Material.SAND}) {
            assertTrue(isContiguous(slotsOf(placement, mat)), mat + " must be one contiguous block");
        }
    }

    @Test
    void smallRemainderFlowsIntoItsOwnRow() {
        // 19/19/5: the two big types form 4×5 blocks side by side; the small remainder must not tuck into
        // their margin but flow into the open space below as its own contiguous strip on a single row.
        List<ItemStack> raw = new ArrayList<>();
        raw.addAll(fullStacks(Material.DIRT, 19));
        raw.addAll(fullStacks(Material.SAND, 19));
        raw.addAll(fullStacks(Material.STONE, 5));
        List<ItemStack> sortedStacks = sorted(raw);

        Map<Integer, ItemStack> placement =
                TreemapPacker.pack(sortedStacks, chestSlots(), 0, WIDTH, ROWS, StartCorner.TOP_LEFT, FillAxis.HORIZONTAL);

        assertEquals(sortedStacks.size(), placement.size(), "every stack is placed");
        for (Material mat : new Material[]{Material.DIRT, Material.SAND, Material.STONE}) {
            assertTrue(isContiguous(slotsOf(placement, mat)), mat + " must be one contiguous block");
        }
        // The remainder sits on a single row, distinct from the big blocks above it.
        Set<Integer> stoneRows = new TreeSet<>();
        slotsOf(placement, Material.STONE).forEach(s -> stoneRows.add(s / WIDTH));
        assertEquals(1, stoneRows.size(), "the 5-stack remainder occupies a single flat row");
    }

    @Test
    void fullChestProtectsTheLargestTypesAndDropsNothing() {
        // A completely full chest with counts that cannot tile cleanly (25/16/13 = 54). With no slack, the
        // smallest type degrades to fill the leftover, but the two largest keep solid rectangles and every
        // stack is still placed.
        List<ItemStack> raw = new ArrayList<>();
        raw.addAll(fullStacks(Material.DIRT, 25));
        raw.addAll(fullStacks(Material.STONE, 16));
        raw.addAll(fullStacks(Material.SAND, 13));
        List<ItemStack> sortedStacks = sorted(raw);

        Map<Integer, ItemStack> placement =
                TreemapPacker.pack(sortedStacks, chestSlots(), 0, WIDTH, ROWS, StartCorner.TOP_LEFT, FillAxis.HORIZONTAL);

        assertEquals(CELLS, placement.size(), "a full chest fills every cell");
        assertEquals(sortedStacks.size(), placement.size(), "no stack is dropped from a full chest");
        Map<Material, Integer> counts = cellCounts(placement);
        assertEquals(25, counts.get(Material.DIRT));
        assertEquals(16, counts.get(Material.STONE));
        assertEquals(13, counts.get(Material.SAND));

        // The two largest types are protected as solid rectangles; only the smallest fills the remainder.
        assertTrue(isSolidRectangle(placement, Material.DIRT), "the largest type is a solid rectangle");
        assertTrue(isSolidRectangle(placement, Material.STONE), "the second-largest type is a solid rectangle");
    }

    @Test
    void emptySpaceTilesTheTrailingRegion() {
        // With only a few stacks the layout must place them all and leave the rest of the chest empty;
        // total placed equals total stacks and no slot outside the sortable set is touched.
        List<ItemStack> sortedStacks = sorted(fullStacks(Material.DIRT, 5));

        Map<Integer, ItemStack> placement =
                TreemapPacker.pack(sortedStacks, chestSlots(), 0, WIDTH, ROWS, StartCorner.TOP_LEFT, FillAxis.HORIZONTAL);

        assertEquals(5, placement.size(), "only the real stacks are placed");
        assertTrue(placement.keySet().stream().allMatch(s -> s >= 0 && s < CELLS),
                "no placement escapes the grid");
    }

    // --- material-grouping tests ---

    /** Slots whose item passes the predicate. */
    private Set<Integer> slotsMatching(Map<Integer, ItemStack> placement, Predicate<ItemStack> filter) {
        Set<Integer> slots = new TreeSet<>();
        placement.forEach((slot, item) -> { if (filter.test(item)) slots.add(slot); });
        return slots;
    }

    /** Creates a max-size stack with lore so multiple copies remain distinct cells after SortEngine merge. */
    private ItemStack withLore(Material mat, String lore) {
        ItemStack stack = new ItemStack(mat, 64);
        ItemMeta meta = stack.getItemMeta();
        meta.lore(List.of(Component.text(lore)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack withDamage(Material mat, int damage) {
        ItemStack stack = new ItemStack(mat);
        Damageable meta = (Damageable) stack.getItemMeta();
        meta.setDamage(damage);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack withPDC(Material mat, String key) {
        ItemStack stack = new ItemStack(mat, 64);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey("test", key), PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack enchantedBook(Enchantment ench, int level) {
        ItemStack stack = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) stack.getItemMeta();
        meta.addStoredEnchant(ench, level, true);
        stack.setItemMeta(meta);
        return stack;
    }

    @Test
    void bundlesMergeIntoOneBlock() {
        // Empty bundles and a filled bundle must share one contiguous rectangle (not split by meta).
        ItemStack filled = new ItemStack(Material.BUNDLE);
        BundleMeta bundleMeta = (BundleMeta) filled.getItemMeta();
        bundleMeta.addItem(new ItemStack(Material.STONE, 16));
        filled.setItemMeta(bundleMeta);

        List<ItemStack> raw = new ArrayList<>();
        for (int i = 0; i < 6; i++) raw.add(new ItemStack(Material.BUNDLE));
        raw.add(filled);
        // Add other material so the grouping is exercised against a distinct block.
        raw.addAll(fullStacks(Material.DIRT, 8));

        List<ItemStack> sortedStacks = sorted(raw);
        Map<Integer, ItemStack> placement =
                TreemapPacker.pack(sortedStacks, chestSlots(), 0, WIDTH, ROWS, StartCorner.TOP_LEFT, FillAxis.HORIZONTAL);

        assertEquals(sortedStacks.size(), placement.size(), "every stack placed");
        Set<Integer> bundleSlots = slotsOf(placement, Material.BUNDLE);
        assertFalse(bundleSlots.isEmpty(), "bundles are placed");
        assertTrue(isContiguous(bundleSlots), "all bundle stacks occupy one contiguous block");
    }

    @Test
    void durableVariantsMergeIntoOneBlock() {
        // Diamond pickaxes differing by damage, enchantment, and anvil rename are all durable and
        // must share one contiguous rectangle regardless of meta differences.
        ItemStack plain    = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemStack damaged  = withDamage(Material.DIAMOND_PICKAXE, 200);
        ItemStack enchanted = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta enchMeta = enchanted.getItemMeta();
        enchMeta.addEnchant(Enchantment.EFFICIENCY, 3, true);
        enchanted.setItemMeta(enchMeta);
        ItemStack renamed = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta renamedMeta = renamed.getItemMeta();
        renamedMeta.displayName(Component.text("My Pickaxe"));
        renamed.setItemMeta(renamedMeta);

        List<ItemStack> raw = new ArrayList<>();
        for (int i = 0; i < 5; i++) raw.add(plain.clone());
        for (int i = 0; i < 5; i++) raw.add(damaged.clone());
        for (int i = 0; i < 5; i++) raw.add(enchanted.clone());
        for (int i = 0; i < 5; i++) raw.add(renamed.clone());
        raw.addAll(fullStacks(Material.DIRT, 8));

        List<ItemStack> sortedStacks = sorted(raw);
        Map<Integer, ItemStack> placement =
                TreemapPacker.pack(sortedStacks, chestSlots(), 0, WIDTH, ROWS, StartCorner.TOP_LEFT, FillAxis.HORIZONTAL);

        assertEquals(sortedStacks.size(), placement.size(), "every stack placed");
        Set<Integer> pickSlots = slotsOf(placement, Material.DIAMOND_PICKAXE);
        assertEquals(20, pickSlots.size(), "all 20 pickaxe stacks placed");
        assertTrue(isContiguous(pickSlots), "all diamond pickaxe variants share one contiguous block");
    }

    @Test
    void customSwordJoinsMaterialBlockBecauseSwordsAreDurable() {
        // A DIAMOND_SWORD with lore (which would make it "custom" if non-durable) must still join the
        // plain diamond sword block because swords are durable — the durable check wins.
        ItemStack plain  = new ItemStack(Material.DIAMOND_SWORD);
        ItemStack custom = withLore(Material.DIAMOND_SWORD, "Legendary");

        List<ItemStack> raw = new ArrayList<>();
        for (int i = 0; i < 6; i++) raw.add(plain.clone());
        for (int i = 0; i < 6; i++) raw.add(custom.clone());
        raw.addAll(fullStacks(Material.DIRT, 8));

        List<ItemStack> sortedStacks = sorted(raw);
        Map<Integer, ItemStack> placement =
                TreemapPacker.pack(sortedStacks, chestSlots(), 0, WIDTH, ROWS, StartCorner.TOP_LEFT, FillAxis.HORIZONTAL);

        Set<Integer> swordSlots = slotsOf(placement, Material.DIAMOND_SWORD);
        assertEquals(12, swordSlots.size(), "all 12 sword stacks placed");
        assertTrue(isContiguous(swordSlots), "plain and custom swords share one block (durable rule)");
    }

    @Test
    void nonDurableCustomItemsGetSeparateBlocksGroupedByIdentity() {
        // Two distinct custom feathers (different lore) each form their own block; plain feathers
        // form a third block. Multiple copies of the same custom variant share one block.
        ItemStack customA = withLore(Material.FEATHER, "Lore A");  // amount=64 (from withLore)
        ItemStack customB = withLore(Material.FEATHER, "Lore B");
        ItemStack plain   = new ItemStack(Material.FEATHER, 64);   // amount=64 so merge keeps 5 stacks

        List<ItemStack> raw = new ArrayList<>();
        for (int i = 0; i < 5; i++) raw.add(customA.clone());
        for (int i = 0; i < 5; i++) raw.add(customB.clone());
        for (int i = 0; i < 5; i++) raw.add(plain.clone());
        raw.addAll(fullStacks(Material.DIRT, 8));

        List<ItemStack> sortedStacks = sorted(raw);
        Map<Integer, ItemStack> placement =
                TreemapPacker.pack(sortedStacks, chestSlots(), 0, WIDTH, ROWS, StartCorner.TOP_LEFT, FillAxis.HORIZONTAL);

        assertEquals(sortedStacks.size(), placement.size(), "every stack placed");

        // Each variant must occupy its own contiguous region.
        Set<Integer> slotsA = slotsMatching(placement, item -> item.isSimilar(customA));
        Set<Integer> slotsB = slotsMatching(placement, item -> item.isSimilar(customB));
        Set<Integer> slotsPlain = slotsMatching(placement, item ->
                item.getType() == Material.FEATHER && !item.isSimilar(customA) && !item.isSimilar(customB));

        assertEquals(5, slotsA.size(), "5 stacks of custom-A placed");
        assertEquals(5, slotsB.size(), "5 stacks of custom-B placed");
        assertEquals(5, slotsPlain.size(), "5 plain feather stacks placed");
        assertTrue(isContiguous(slotsA),     "custom-A feathers form one contiguous block");
        assertTrue(isContiguous(slotsB),     "custom-B feathers form one contiguous block");
        assertTrue(isContiguous(slotsPlain), "plain feathers form one contiguous block");

        // The three blocks must be distinct (no slot shared).
        Set<Integer> all = new TreeSet<>(slotsA);
        all.retainAll(slotsB);
        assertTrue(all.isEmpty(), "custom-A and custom-B blocks do not overlap");
    }

    @Test
    void anvilRenameAloneDoesNotMakeItemCustom() {
        // A STICK with only a display name (no lore/enchant/model-data/PDC) is not a custom item
        // and must group with plain sticks in one block. Use amount=64 so each "copy" produces a
        // distinct cell after SortEngine merges same-SortKey stacks.
        ItemStack plain   = new ItemStack(Material.STICK, 64);
        ItemStack renamed = new ItemStack(Material.STICK, 64);
        ItemMeta meta = renamed.getItemMeta();
        meta.displayName(Component.text("My Stick"));
        renamed.setItemMeta(meta);

        List<ItemStack> raw = new ArrayList<>();
        for (int i = 0; i < 6; i++) raw.add(plain.clone());
        for (int i = 0; i < 6; i++) raw.add(renamed.clone());
        raw.addAll(fullStacks(Material.DIRT, 8));

        List<ItemStack> sortedStacks = sorted(raw);
        Map<Integer, ItemStack> placement =
                TreemapPacker.pack(sortedStacks, chestSlots(), 0, WIDTH, ROWS, StartCorner.TOP_LEFT, FillAxis.HORIZONTAL);

        Set<Integer> stickSlots = slotsOf(placement, Material.STICK);
        assertEquals(12, stickSlots.size(), "all 12 stick stacks placed");
        assertTrue(isContiguous(stickSlots), "plain and renamed sticks share one contiguous block");
    }

    @Test
    void nonDurableItemWithPdcIsCustomAndGetsOwnBlock() {
        // A non-durable item carrying a PDC entry (the WuufusWaygates feather-key pattern) must be
        // treated as a custom item and land in a block separate from plain items of the same material.
        ItemStack pdcFeather = withPDC(Material.FEATHER, "door_key");
        ItemStack plain      = new ItemStack(Material.FEATHER, 64);

        List<ItemStack> raw = new ArrayList<>();
        for (int i = 0; i < 5; i++) raw.add(pdcFeather.clone());
        for (int i = 0; i < 5; i++) raw.add(plain.clone());
        raw.addAll(fullStacks(Material.DIRT, 8));

        List<ItemStack> sortedStacks = sorted(raw);
        Map<Integer, ItemStack> placement =
                TreemapPacker.pack(sortedStacks, chestSlots(), 0, WIDTH, ROWS, StartCorner.TOP_LEFT, FillAxis.HORIZONTAL);

        assertEquals(sortedStacks.size(), placement.size(), "every stack placed");
        Set<Integer> pdcSlots   = slotsMatching(placement, item -> item.isSimilar(pdcFeather));
        Set<Integer> plainSlots = slotsMatching(placement, item ->
                item.getType() == Material.FEATHER && !item.isSimilar(pdcFeather));

        assertFalse(pdcSlots.isEmpty(),   "PDC feathers are placed");
        assertFalse(plainSlots.isEmpty(), "plain feathers are placed");
        assertTrue(isContiguous(pdcSlots),   "PDC feathers form one contiguous block");
        assertTrue(isContiguous(plainSlots), "plain feathers form one contiguous block");
        Set<Integer> overlap = new TreeSet<>(pdcSlots);
        overlap.retainAll(plainSlots);
        assertTrue(overlap.isEmpty(), "PDC and plain feather blocks do not overlap");
    }

    @Test
    void enchantedBooksLumpIntoOneBlock() {
        // ENCHANTED_BOOKs with different stored enchantments are not custom (hasEnchants() is false
        // for stored enchantments) and must all group into one contiguous block.
        List<ItemStack> raw = new ArrayList<>();
        for (int i = 0; i < 4; i++) raw.add(enchantedBook(Enchantment.SHARPNESS, 1));
        for (int i = 0; i < 4; i++) raw.add(enchantedBook(Enchantment.EFFICIENCY, 3));
        for (int i = 0; i < 4; i++) raw.add(enchantedBook(Enchantment.PROTECTION, 4));
        raw.addAll(fullStacks(Material.DIRT, 8));

        List<ItemStack> sortedStacks = sorted(raw);
        Map<Integer, ItemStack> placement =
                TreemapPacker.pack(sortedStacks, chestSlots(), 0, WIDTH, ROWS, StartCorner.TOP_LEFT, FillAxis.HORIZONTAL);

        assertEquals(sortedStacks.size(), placement.size(), "every stack placed");
        Set<Integer> bookSlots = slotsOf(placement, Material.ENCHANTED_BOOK);
        assertEquals(12, bookSlots.size(), "all 12 enchanted book stacks placed");
        assertTrue(isContiguous(bookSlots), "all enchanted books occupy one contiguous block");
    }
}
