package net.kccricket.clicksorted;

import net.kccricket.clicksorted.sort.GridGeometry;
import org.bukkit.event.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.DonkeyMock;
import org.mockbukkit.mockbukkit.entity.HorseMock;
import org.mockbukkit.mockbukkit.entity.LlamaMock;
import org.mockbukkit.mockbukkit.entity.MuleMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link GridGeometry}, the per-inventory grid (origin/width/rows) that drives the
 * corner/axis/treemap placement. The chested-mount numbers mirror the real Paper 26.1.2 layout
 * captured by the startup probe: 2 leading equipment slots, then a 3-row chest whose column count is
 * the mount's strength (llamas) or 5 (donkey/mule). Extends {@link AbstractClickSortedTest} only for
 * the {@code ServerMock} needed to construct the entity mocks.
 */
class GridGeometryTest extends AbstractClickSortedTest {

    @Test
    void plainContainersUseTheInventoryTypeWidth() {
        // A double chest: base 0, 9 wide, 6 rows.
        GridGeometry chest = GridGeometry.of(InventoryType.CHEST, null, 0, 54);
        assertEquals(0, chest.base());
        assertEquals(9, chest.width());
        assertEquals(6, chest.rows());

        // A hopper: 5 wide, single row.
        GridGeometry hopper = GridGeometry.of(InventoryType.HOPPER, null, 0, 5);
        assertEquals(5, hopper.width());
        assertEquals(1, hopper.rows());
    }

    @Test
    void donkeyAndMuleChestsAreFiveWideThreeTall() {
        DonkeyMock donkey = new DonkeyMock(server, UUID.randomUUID());
        GridGeometry d = GridGeometry.of(InventoryType.CHEST, donkey, 2, 17);
        assertEquals(2, d.base(), "storage starts after the 2 equipment slots");
        assertEquals(5, d.width());
        assertEquals(3, d.rows());

        MuleMock mule = new MuleMock(server, UUID.randomUUID());
        GridGeometry m = GridGeometry.of(InventoryType.CHEST, mule, 2, 17);
        assertEquals(2, m.base());
        assertEquals(5, m.width());
        assertEquals(3, m.rows());
    }

    @Test
    void llamaChestWidthIsItsStrength() {
        // size = 2 leading + strength*3 storage; width = strength, rows = 3, base = 2.
        for (int strength = 1; strength <= 5; strength++) {
            LlamaMock llama = new LlamaMock(server, UUID.randomUUID());
            llama.setStrength(strength);
            int size = 2 + strength * 3;

            GridGeometry g = GridGeometry.of(InventoryType.CHEST, llama, 2, size);
            assertEquals(2, g.base(), "llama storage starts after the saddle+decor slots");
            assertEquals(strength, g.width(), "llama chest is strength columns wide");
            assertEquals(3, g.rows(), "llama chest is always 3 rows");
        }
    }

    @Test
    void regularHorseIsNotGivenAChestGrid() {
        // A plain horse isn't a ChestedHorse, so it falls through to the generic path rather than the
        // 3-row mount-chest grid (it has no storage anyway).
        HorseMock horse = new HorseMock(server, UUID.randomUUID());
        GridGeometry g = GridGeometry.of(InventoryType.CHEST, horse, 2, 2);
        assertEquals(9, g.width(), "a non-chested horse uses the generic width, not the mount-chest width");
    }
}
