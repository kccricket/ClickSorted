package net.kccricket.clicksorted;

import net.kccricket.clicksorted.model.FillAxis;
import net.kccricket.clicksorted.model.StartCorner;
import net.kccricket.clicksorted.sort.SlotOrder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link SlotOrder}. The helper is pure (no Bukkit state), so these run without
 * MockBukkit. Geometry under test is a 9×3 single chest (27 slots) unless noted otherwise.
 */
class SlotOrderTest {

    private static List<Integer> chestSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            slots.add(i);
        }
        return slots;
    }

    private static List<Integer> order(List<Integer> slots, int width,
                                       StartCorner corner, FillAxis axis) {
        return SlotOrder.order(slots, 0, width, corner, axis);
    }

    @Test
    void topLeftHorizontal_isPlainAscending() {
        List<Integer> expected = IntStream.range(0, 27).boxed().toList();
        assertEquals(expected, order(chestSlots(), 9, StartCorner.TOP_LEFT, FillAxis.HORIZONTAL));
    }

    @Test
    void topRightVertical_fillsColumnsRightToLeft() {
        List<Integer> expected = List.of(
                8, 17, 26, 7, 16, 25, 6, 15, 24, 5, 14, 23, 4, 13, 22,
                3, 12, 21, 2, 11, 20, 1, 10, 19, 0, 9, 18);
        assertEquals(expected, order(chestSlots(), 9, StartCorner.TOP_RIGHT, FillAxis.VERTICAL));
    }

    @Test
    void bottomLeftHorizontal_fillsRowsBottomToTop() {
        List<Integer> expected = List.of(
                18, 19, 20, 21, 22, 23, 24, 25, 26,
                9, 10, 11, 12, 13, 14, 15, 16, 17,
                0, 1, 2, 3, 4, 5, 6, 7, 8);
        assertEquals(expected, order(chestSlots(), 9, StartCorner.BOTTOM_LEFT, FillAxis.HORIZONTAL));
    }

    @Test
    void bottomRightVertical_fillsColumnsRightToLeftBottomUp() {
        List<Integer> expected = List.of(
                26, 17, 8, 25, 16, 7, 24, 15, 6, 23, 14, 5, 22, 13, 4,
                21, 12, 3, 20, 11, 2, 19, 10, 1, 18, 9, 0);
        assertEquals(expected, order(chestSlots(), 9, StartCorner.BOTTOM_RIGHT, FillAxis.VERTICAL));
    }

    @Test
    void rectilinear_skipsGapsButKeepsRelativeOrder() {
        // Locked slots 0 and 26 removed; default direction must still cover exactly the present slots.
        List<Integer> slots = new ArrayList<>(chestSlots());
        slots.remove(Integer.valueOf(0));
        slots.remove(Integer.valueOf(26));
        List<Integer> expected = IntStream.rangeClosed(1, 25).boxed().toList();
        assertEquals(expected, order(slots, 9, StartCorner.TOP_LEFT, FillAxis.HORIZONTAL));
    }
}
