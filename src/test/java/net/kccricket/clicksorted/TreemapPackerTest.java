package net.kccricket.clicksorted;

import net.kccricket.clicksorted.model.FillAxis;
import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.model.StartCorner;
import net.kccricket.clicksorted.sort.SortEngine;
import net.kccricket.clicksorted.sort.TreemapPacker;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
}
