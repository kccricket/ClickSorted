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
    void playerStorageRoundsTheOriginToARowBoundary() {
        // Default range is already row-aligned: base stays 9, nothing moves.
        GridGeometry aligned = GridGeometry.of(InventoryType.PLAYER, null, 9, 36);
        assertEquals(9, aligned.base());
        assertEquals(9, aligned.width());
        assertEquals(3, aligned.rows());

        // A skip-a-whole-row range is also aligned (18 is a boundary).
        GridGeometry twoRows = GridGeometry.of(InventoryType.PLAYER, null, 18, 36);
        assertEquals(18, twoRows.base());
        assertEquals(2, twoRows.rows());

        // A mid-row min (11) rounds DOWN to its row boundary (9) so the grid stays aligned to the real
        // inventory rows; the row span still reaches the last row. Slots 9..10 (excluded via admin slot
        // locks or any other exclusion mechanism) are handled downstream as gaps, exactly like player-
        // locked slots.
        GridGeometry midRow = GridGeometry.of(InventoryType.PLAYER, null, 11, 36);
        assertEquals(9, midRow.base());
        assertEquals(9, midRow.width());
        assertEquals(3, midRow.rows());
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
    void storageOffsetIsTheSingleSourceForTheRangeStartAndGridBase() {
        // Non-mount holders (and null) start at slot 0; every horse-like mount skips its 2 leading
        // equipment slots. The grid's base for a chested mount is derived from this same offset, so the
        // sortable-range lower bound the service computes can never drift from the grid origin.
        assertEquals(0, GridGeometry.storageOffset(null));

        DonkeyMock donkey = new DonkeyMock(server, UUID.randomUUID());
        assertEquals(2, GridGeometry.storageOffset(donkey));
        assertEquals(GridGeometry.storageOffset(donkey),
                GridGeometry.of(InventoryType.CHEST, donkey, GridGeometry.storageOffset(donkey), 17).base());

        HorseMock horse = new HorseMock(server, UUID.randomUUID());
        assertEquals(2, GridGeometry.storageOffset(horse), "a plain horse still has 2 leading equipment slots");
    }

    @Test
    void chestedMountGridOriginFollowsTheGivenMinNotAReDerivedOffset() {
        // The grid base must track the sortable range the caller built from min, so of() honors the min
        // it is given rather than recomputing storageOffset internally. Passing an off-nominal min (5,
        // not the donkey's real offset of 2) proves base follows min — guarding against a regression
        // where the chested branch re-derives the offset and shears the grid against the sorted slots.
        GridGeometry g = GridGeometry.of(InventoryType.CHEST, new DonkeyMock(server, UUID.randomUUID()), 5, 17);
        assertEquals(5, g.base(), "chested-mount base must equal the supplied min");
        assertEquals(5, g.width());
        assertEquals(3, g.rows());
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
