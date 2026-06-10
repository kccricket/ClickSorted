package net.kccricket.clicksorted;

import net.kccricket.clicksorted.gui.LockGuiHolder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the static slot-mapping helpers in {@link LockGuiHolder}.
 * These are pure logic tests — no MockBukkit required.
 */
class LockGuiHolderTest {

    // --- chestSlotToInvSlot ---

    @Test
    void mainStorageRowsMapsChestSlotToInvSlot() {
        // Chest slots 0–26 map to player inventory slots 9–35.
        assertEquals(9, LockGuiHolder.chestSlotToInvSlot(0));
        assertEquals(17, LockGuiHolder.chestSlotToInvSlot(8));
        assertEquals(18, LockGuiHolder.chestSlotToInvSlot(9));
        assertEquals(35, LockGuiHolder.chestSlotToInvSlot(26));
    }

    @Test
    void dividerRowMapsToNegativeOne() {
        // Chest slots 27–35 are the divider — no inventory slot.
        for (int chestSlot = 27; chestSlot < 36; chestSlot++) {
            assertEquals(-1, LockGuiHolder.chestSlotToInvSlot(chestSlot),
                    "Divider slot " + chestSlot + " should return -1");
        }
    }

    @Test
    void hotbarRowMapsChestSlotToInvSlot() {
        // Chest slots 36–44 map to hotbar slots 0–8.
        assertEquals(0, LockGuiHolder.chestSlotToInvSlot(36));
        assertEquals(8, LockGuiHolder.chestSlotToInvSlot(44));
    }

    // --- isDividerSlot ---

    @Test
    void isDividerSlotReturnsTrueForDividerRange() {
        for (int chestSlot = 27; chestSlot < 36; chestSlot++) {
            assertTrue(LockGuiHolder.isDividerSlot(chestSlot),
                    "Slot " + chestSlot + " should be a divider slot");
        }
    }

    @Test
    void isDividerSlotReturnsFalseOutsideDividerRange() {
        assertFalse(LockGuiHolder.isDividerSlot(26));
        assertFalse(LockGuiHolder.isDividerSlot(36));
        assertFalse(LockGuiHolder.isDividerSlot(0));
        assertFalse(LockGuiHolder.isDividerSlot(44));
    }

    // --- isInvSlotSortable ---

    @Test
    void hotbarSlotsAreAlwaysSortable() {
        for (int slot = 0; slot < 9; slot++) {
            assertTrue(LockGuiHolder.isInvSlotSortable(slot, 9, 36),
                    "Hotbar slot " + slot + " should always be sortable");
        }
    }

    @Test
    void mainStorageSlotInRangeIsSortable() {
        assertTrue(LockGuiHolder.isInvSlotSortable(9, 9, 36));
        assertTrue(LockGuiHolder.isInvSlotSortable(35, 9, 36));
    }

    @Test
    void mainStorageSlotOutOfRangeIsNotSortable() {
        assertFalse(LockGuiHolder.isInvSlotSortable(27, 9, 27),
                "Inv slot 27 should not be sortable when sortMax is 27");
        assertFalse(LockGuiHolder.isInvSlotSortable(35, 9, 27),
                "Inv slot 35 should not be sortable when sortMax is 27");
    }

    @Test
    void dividerSentinelIsNotSortable() {
        assertFalse(LockGuiHolder.isInvSlotSortable(-1, 9, 36),
                "Divider sentinel (-1) should never be sortable");
    }

    // --- mapping is bijective for interactive slots ---

    @Test
    void mainStorageMappingCoversAllExpectedInvSlots() {
        // Chest slots 0–26 should cover player inventory slots 9–35 exactly.
        java.util.Set<Integer> covered = new java.util.HashSet<>();
        for (int chestSlot = 0; chestSlot < 27; chestSlot++) {
            covered.add(LockGuiHolder.chestSlotToInvSlot(chestSlot));
        }
        for (int invSlot = 9; invSlot <= 35; invSlot++) {
            assertTrue(covered.contains(invSlot),
                    "Inv slot " + invSlot + " should be reachable from chest row 1-3");
        }
        assertEquals(27, covered.size(), "Should be exactly 27 distinct mappings");
    }

    @Test
    void hotbarMappingCoversAllExpectedInvSlots() {
        // Chest slots 36–44 should cover hotbar slots 0–8 exactly.
        java.util.Set<Integer> covered = new java.util.HashSet<>();
        for (int chestSlot = 36; chestSlot < 45; chestSlot++) {
            covered.add(LockGuiHolder.chestSlotToInvSlot(chestSlot));
        }
        for (int invSlot = 0; invSlot <= 8; invSlot++) {
            assertTrue(covered.contains(invSlot),
                    "Hotbar slot " + invSlot + " should be reachable from chest row 5");
        }
        assertEquals(9, covered.size(), "Should be exactly 9 distinct hotbar mappings");
    }
}
