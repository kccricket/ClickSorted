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
import net.kccricket.clicksorted.model.StartCorner;
import org.bukkit.event.inventory.InventoryType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Pure, stateless mapping of a sorted item sequence onto inventory slots. Given the set of sortable
 * slots and the inventory's grid geometry, produces the order in which slots should be filled so that
 * the first sorted item lands at the player's chosen {@link StartCorner} and the layout flows along
 * the chosen {@link FillAxis}.
 * <p>
 * The default {@code TOP_LEFT} + {@code HORIZONTAL} reproduces plain ascending slot order — i.e. the
 * historical "left-to-right, top-to-bottom" behavior.
 */
public final class SlotOrder {

    private SlotOrder() {
    }

    /**
     * @param slots  the slots to order (gaps from locked/excluded slots are fine — only the slots
     *               actually present are returned)
     * @param base   the slot index of the grid's top-left cell (row 0, col 0)
     * @param width  the grid width in columns
     * @param corner the corner the layout starts from
     * @param axis   the direction the layout flows
     * @return the slots in fill order; the caller writes the i-th sorted item to the i-th slot
     */
    public static List<Integer> order(Collection<Integer> slots, int base, int width,
                                      StartCorner corner, FillAxis axis) {
        int rowSign = corner.topRow() ? 1 : -1;   // top edge → rows ascend; bottom edge → rows descend
        int colSign = corner.leftCol() ? 1 : -1;  // left edge → cols ascend; right edge → cols descend

        Comparator<Integer> cmp = Comparator
                .comparingInt((Integer slot) -> {
                    int r = slot - base;
                    int row = r / width, col = r % width;
                    return axis == FillAxis.VERTICAL ? colSign * col : rowSign * row;
                })
                .thenComparingInt(slot -> {
                    int r = slot - base;
                    int row = r / width, col = r % width;
                    return axis == FillAxis.VERTICAL ? rowSign * row : colSign * col;
                });

        List<Integer> ordered = new ArrayList<>(slots);
        ordered.sort(cmp);
        return ordered;
    }

    /** The grid width (columns) for an inventory type. Most containers and the player grid are 9 wide. */
    public static int widthFor(InventoryType type) {
        return switch (type) {
            case HOPPER -> 5;
            case DROPPER, DISPENSER -> 3;
            default -> 9;
        };
    }
}
