package net.kccricket.clicksorted.sort;

/*
 * This file is part of ClickSorted
 *
 * ClickSorted is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * ClickSorted is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with ClickSorted. If not, see <http://www.gnu.org/licenses/>.
 */

import net.kccricket.clicksorted.model.FillAxis;
import net.kccricket.clicksorted.model.SortKey;
import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.model.StartCorner;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure, stateless placement for the {@code TREEMAP} sort method — each item type becomes its own
 * roughly-square, contiguous rectangle, sized to the number of stacks it occupies.
 * <p>
 * Item types are grouped before placement: durable items (tools/weapons/armour) are grouped by
 * material only, fully meta-agnostic — a damaged, enchanted, or anvil-renamed diamond sword shares
 * one rectangle with plain diamond swords. Non-durable items that carry enchantments, lore,
 * custom-model-data, or a non-empty PDC are treated as custom/plugin items and each distinct
 * custom variant gets its own rectangle (multiple copies of the same custom item still share one
 * rectangle). All other non-durable items (including bundles and written books) group by material.
 * <p>
 * Groups are ranked largest-first and packed shelf-style: the biggest group opens a shelf (a
 * horizontal band) whose height is chosen to keep the block near-square; smaller groups fill in to
 * its right at the same height while they stay reasonably square and fit, otherwise a new shelf
 * opens below. A group that does not tile its rectangle exactly leaves its own trailing cells empty
 * (its ragged tail) — a neighbouring group never tucks into it, so no group wraps or splits.
 * <p>
 * Because clean rectangles need spare cells to pad their tails, this works while the container has
 * room. Once a remaining group can no longer fit as a rectangle in the space left (a nearly-full
 * container), the packer falls back to filling the remaining free cells tightly in reading order —
 * so the largest, most visible groups keep clean rectangles and only the tail degrades, reducing to
 * a gap-free fill when the container is full. {@code TOP_LEFT} anchors the largest block top-left;
 * other {@link StartCorner}s reflect the grid so the largest block sits at the chosen corner.
 */
public final class TreemapPacker {

    /**
     * Minimum width:height a block may have when placed onto an <em>existing</em> shelf. A type whose
     * stacks would form a sliver thinner than this (e.g. a 1-wide column down a 5-tall shelf) is sent
     * to a fresh shelf instead, so small remainders flow into the open space below rather than
     * lining the right margin.
     */
    private static final double MIN_SHELF_ASPECT = 0.5;

    /**
     * Within-block stack ordering. Enchanted books sort by their primary stored enchantment key
     * (alphabetically) then level; everything else sorts by SortKey (name → durability → meta).
     */
    private static final Comparator<ItemStack> WITHIN_BLOCK_ORDER = (a, b) -> {
        if (a.getType() == Material.ENCHANTED_BOOK && b.getType() == Material.ENCHANTED_BOOK) {
            String aKey = primaryStoredEnchantKey(a);
            String bKey = primaryStoredEnchantKey(b);
            int c = aKey.compareTo(bKey);
            if (c != 0) return c;
            c = Integer.compare(storedEnchantLevel(a, aKey), storedEnchantLevel(b, bKey));
            if (c != 0) return c;
        }
        return new SortKey(a, SortingMethod.TREEMAP).compareTo(new SortKey(b, SortingMethod.TREEMAP));
    };

    private TreemapPacker() {
    }

    /** Returns the alphabetically-first stored enchantment key (without namespace) for an enchanted book. */
    private static String primaryStoredEnchantKey(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof EnchantmentStorageMeta esm)) return "";
        return esm.getStoredEnchants().keySet().stream()
                .map(e -> e.getKey().getKey())
                .sorted()
                .findFirst()
                .orElse("");
    }

    /** Returns the level of the stored enchantment whose key matches {@code primaryKey}. */
    private static int storedEnchantLevel(ItemStack stack, String primaryKey) {
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof EnchantmentStorageMeta esm)) return 0;
        for (Map.Entry<Enchantment, Integer> e : esm.getStoredEnchants().entrySet()) {
            if (e.getKey().getKey().getKey().equals(primaryKey)) {
                return e.getValue();
            }
        }
        return 0;
    }

    /**
     * @param sortedStacks  items already merged, name-sorted and split into max-size stacks
     * @param sortableSlots the slots available to write (gaps/locked slots already excluded)
     * @param base          slot index of the grid's top-left cell (row 0, col 0)
     * @param width         grid width in columns
     * @param rows          grid height in rows
     * @param start         the corner that receives the most-numerous type
     * @param axis          the direction the layout flows: {@code HORIZONTAL} grows shelves left-to-right
     *                      (bands stacked top-to-bottom); {@code VERTICAL} grows them top-to-bottom
     *                      (columns placed left-to-right)
     * @return a slot → stack map for the slots that should be filled; the caller clears the rest
     */
    public static Map<Integer, ItemStack> pack(List<ItemStack> sortedStacks, Set<Integer> sortableSlots,
                                               int base, int width, int rows, StartCorner start, FillAxis axis) {
        Map<Integer, ItemStack> placement = new LinkedHashMap<>();
        if (width <= 0 || rows <= 0) {
            return placement;
        }

        // 1. Partition the name-sorted list into per-type groups, then rank by size descending.
        List<Block> blocks = new ArrayList<>(groupByType(sortedStacks));
        blocks.sort(Comparator.<Block>comparingInt(b -> -b.size).thenComparing(b -> b.key));
        if (blocks.isEmpty()) {
            return placement;
        }

        // 2. Assign each type a rectangle of grid cells (top-left origin). The shelf-packer always lays
        //    horizontal bands; a VERTICAL fill axis runs the identical algorithm on a transposed grid
        //    (rows/width swapped) so its bands become columns. The resulting cells are then in
        //    transposed coordinates and are mapped back to the real grid when emitting (step 3).
        boolean transpose = axis == FillAxis.VERTICAL;
        shelfPack(blocks, transpose ? width : rows, transpose ? rows : width);

        // 3. Reflect onto the chosen anchor corner and emit the slot → stack map; cells whose slot is
        //    locked/missing are skipped and their stacks spill to a leftover queue.
        boolean flipRow = !start.topRow();
        boolean flipCol = !start.leftCol();
        Deque<ItemStack> leftover = new ArrayDeque<>();
        for (Block b : blocks) {
            b.stacks.sort(WITHIN_BLOCK_ORDER);
            List<Integer> slots = new ArrayList<>(b.cells.size());
            for (int[] cell : b.cells) {
                // Undo the transpose (if any) so cellRow/cellCol address the real rows×width grid.
                int cellRow = transpose ? cell[1] : cell[0];
                int cellCol = transpose ? cell[0] : cell[1];
                int r = flipRow ? rows - 1 - cellRow : cellRow;
                int c = flipCol ? width - 1 - cellCol : cellCol;
                slots.add(base + r * width + c);
            }
            slots.sort(Comparator.naturalOrder());
            Deque<ItemStack> stacks = new ArrayDeque<>(b.stacks);
            for (int slot : slots) {
                if (stacks.isEmpty()) {
                    break;
                }
                if (sortableSlots.contains(slot) && !placement.containsKey(slot)) {
                    placement.put(slot, stacks.poll());
                }
            }
            leftover.addAll(stacks); // displaced by locked/missing cells
        }

        // Displaced stacks fill the remaining free sortable slots, flowing from the chosen corner along
        // the chosen axis; any that still do not fit are left for the caller's overflow/drop path.
        for (int slot : SlotOrder.order(sortableSlots, base, width, start, axis)) {
            if (leftover.isEmpty()) {
                break;
            }
            if (!placement.containsKey(slot)) {
                placement.put(slot, leftover.poll());
            }
        }
        return placement;
    }

    /**
     * Lays the ranked {@code blocks} (largest first) into the {@code rows}×{@code width} grid as
     * shelf-packed rectangles, recording each block's grid cells (as {@code [row, col]}) in
     * {@link Block#cells}. Each block is given exactly its {@code size} cells (row-major within its
     * rectangle), so the rectangle's leftover cells stay empty. When a block can no longer fit as a
     * rectangle in the remaining space, it and every smaller block fall back to filling the still-free
     * cells in reading order.
     */
    private static void shelfPack(List<Block> blocks, int rows, int width) {
        boolean[][] used = new boolean[rows][width];
        int shelfTop = 0;    // row where the current shelf begins
        int shelfHeight = 0; // height of the current shelf (0 = no shelf open yet)
        int col = 0;         // next free column within the current shelf

        int idx = 0;
        for (; idx < blocks.size(); idx++) {
            Block b = blocks.get(idx);

            // Try to place on the current shelf, at its established height, if it stays square-ish and fits.
            if (shelfHeight > 0) {
                int w = ceilDiv(b.size, shelfHeight);
                double aspect = (double) w / shelfHeight;
                if (col + w <= width && aspect >= MIN_SHELF_ASPECT) {
                    b.cells = boxCells(shelfTop, col, shelfHeight, w, b.size, used);
                    col += w;
                    continue;
                }
            }

            // Otherwise open a new shelf directly below the current one.
            int top = shelfTop + shelfHeight;
            int[] wh = chooseRect(b.size, rows - top, width);
            if (wh == null) {
                break; // out of room for clean rectangles — degrade the rest to a tight fill
            }
            shelfTop = top;
            shelfHeight = wh[1];
            b.cells = boxCells(shelfTop, 0, shelfHeight, wh[0], b.size, used);
            col = wh[0];
        }

        // Fallback: any block that could not be placed as a rectangle fills the remaining free cells in
        // reading order. Keeps a nearly-full container gap-free without disturbing the clean rectangles.
        if (idx < blocks.size()) {
            List<int[]> freeCells = new ArrayList<>();
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < width; c++) {
                    if (!used[r][c]) {
                        freeCells.add(new int[]{r, c});
                    }
                }
            }
            int f = 0;
            for (; idx < blocks.size(); idx++) {
                Block b = blocks.get(idx);
                List<int[]> cells = new ArrayList<>();
                for (int k = 0; k < b.size && f < freeCells.size(); k++, f++) {
                    cells.add(freeCells.get(f));
                }
                b.cells = cells;
            }
        }
    }

    /**
     * Picks a rectangle for {@code size} cells within {@code maxHeight} rows and {@code width} columns:
     * the least-wasteful shape (smallest area ≥ size), tie-broken toward square and then toward taller.
     * Returns {@code {width, height}} or {@code null} if no rectangle fits the available rows.
     */
    private static int[] chooseRect(int size, int maxHeight, int width) {
        int[] best = null;
        long bestWaste = Long.MAX_VALUE;
        int bestSquare = Integer.MAX_VALUE;
        int bestHeight = -1;
        int hi = Math.min(maxHeight, size);
        for (int h = 1; h <= hi; h++) {
            int w = ceilDiv(size, h);
            if (w > width) {
                continue;
            }
            long waste = (long) w * h - size;
            int square = Math.abs(w - h);
            if (waste < bestWaste
                    || (waste == bestWaste && square < bestSquare)
                    || (waste == bestWaste && square == bestSquare && h > bestHeight)) {
                best = new int[]{w, h};
                bestWaste = waste;
                bestSquare = square;
                bestHeight = h;
            }
        }
        return best;
    }

    /**
     * Marks the whole {@code height}×{@code w} rectangle anchored at {@code (top, left)} as used and
     * returns its first {@code size} cells in row-major order. The rectangle's remaining cells are
     * reserved (kept empty) so a later block never fills this block's ragged tail.
     */
    private static List<int[]> boxCells(int top, int left, int height, int w, int size, boolean[][] used) {
        List<int[]> cells = new ArrayList<>(size);
        for (int r = top; r < top + height; r++) {
            for (int c = left; c < left + w; c++) {
                used[r][c] = true;
                if (cells.size() < size) {
                    cells.add(new int[]{r, c});
                }
            }
        }
        return cells;
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }

    /**
     * Groups stacks into per-type blocks across the whole list (not just consecutive runs).
     * Durable items group by material (meta-agnostic). Non-durable items that carry enchantments,
     * lore, custom-model-data, or a non-empty PDC are custom plugin items; each distinct custom
     * variant gets its own block. All other non-durable items group by material.
     */
    private static List<Block> groupByType(List<ItemStack> stacks) {
        LinkedHashMap<Object, Block> groups = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            Object key = groupKey(stack);
            Block block = groups.get(key);
            if (block == null) {
                block = new Block(new SortKey(stack, SortingMethod.TREEMAP));
                groups.put(key, block);
            }
            block.stacks.add(stack);
            block.size++;
        }
        return new ArrayList<>(groups.values());
    }

    private static Object groupKey(ItemStack stack) {
        if (stack.getType().getMaxDurability() > 0) {
            return stack.getType(); // durable: all meta variants share one block
        }
        if (isCustomNonDurable(stack)) {
            return new SortKey(stack, SortingMethod.TREEMAP); // custom plugin item: own block by identity
        }
        return stack.getType(); // plain non-durable: by material
    }

    private static boolean isCustomNonDurable(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        return meta != null && (meta.hasEnchants()
                || meta.hasLore()
                || meta.hasCustomModelData()
                || !meta.getPersistentDataContainer().isEmpty());
    }

    /** One rectangle in the treemap: an item type with its stacks. {@link #cells} is filled by {@link #shelfPack}. */
    private static final class Block {
        final SortKey key;
        final List<ItemStack> stacks = new ArrayList<>();
        int size;
        List<int[]> cells = List.of();

        Block(SortKey key) {
            this.key = key;
        }
    }
}
