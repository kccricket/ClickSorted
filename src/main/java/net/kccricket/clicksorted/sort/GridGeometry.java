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

import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Llama;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryHolder;

/**
 * The grid the corner/axis/treemap placement reasons about for a sortable inventory: the slot index
 * of its top-left cell ({@code base}), its column {@code width}, and its {@code rows}.
 * <p>
 * Most containers are a simple {@code width}-wide block whose origin is the start of the row that
 * holds {@code min} (rounded to a row boundary so the grid stays aligned to the real inventory rows),
 * with the row count derived from the sortable range. Chested mounts are the exception: a donkey/mule chest is a fixed
 * {@code 5×3} grid and a llama chest is {@code strength×3}, both starting after the two leading
 * equipment slots. Verified on Paper 26.1.2: for all chested mounts the storage is the trailing block
 * at slots {@code 2..size-1}, so {@code base = min = 2} and only the column width varies.
 */
public record GridGeometry(int base, int width, int rows) {

    /** Number of grid rows in every chested-mount chest (donkey/mule and llama alike). */
    private static final int MOUNT_CHEST_ROWS = 3;
    /** Column count of a donkey/mule chest. */
    private static final int PACK_HORSE_CHEST_COLUMNS = 5;

    /**
     * @param type   the clicked inventory's type
     * @param holder the inventory's holder (may be {@code null})
     * @param min    the first sortable slot (inclusive) — also the grid's top-left slot
     * @param max    one past the last sortable slot (exclusive)
     */
    public static GridGeometry of(InventoryType type, InventoryHolder holder, int min, int max) {
        if (holder instanceof ChestedHorse) {
            int columns = holder instanceof Llama llama ? llama.getStrength() : PACK_HORSE_CHEST_COLUMNS;
            return new GridGeometry(min, columns, MOUNT_CHEST_ROWS);
        }
        int width = SlotOrder.widthFor(type);
        // Normal inventories are anchored at slot 0, so the grid origin is the start of the row that
        // contains `min` — round `min` down to a row boundary rather than using it directly. For every
        // current config this is a no-op (containers start at 0, player storage at 9, both already
        // aligned), and even when it isn't, the linear layout is unaffected (shifting the origin by
        // whole rows can't change the fill order). It only matters when an admin carves
        // `player_sort_min` mid-row: using `min` as the origin would shear every row/col, scrambling
        // the non-default corners/axes and the treemap; rounding keeps the grid aligned to the real
        // inventory rows, with the leading out-of-range slots simply treated as gaps (like locked ones).
        int rowStart = min / width;
        int rowEnd = (max - 1) / width;
        return new GridGeometry(rowStart * width, width, Math.max(1, rowEnd - rowStart + 1));
    }
}
