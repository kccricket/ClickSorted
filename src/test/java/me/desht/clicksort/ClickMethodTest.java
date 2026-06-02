package me.desht.clicksort;

import me.desht.dhutils.CompatUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class ClickMethodTest {

    @Test
    void next_wrapsAround() {
        assertEquals(ClickMethod.MIDDLE, ClickMethod.NONE.next());
    }

    @Test
    void next_sequential() {
        assertEquals(ClickMethod.DOUBLE, ClickMethod.MIDDLE.next());
        assertEquals(ClickMethod.SINGLE, ClickMethod.DOUBLE.next());
        assertEquals(ClickMethod.SWAP,   ClickMethod.SINGLE.next());
        assertEquals(ClickMethod.NONE,   ClickMethod.SWAP.next());
        assertEquals(ClickMethod.MIDDLE, ClickMethod.NONE.next());
    }

    @Test
    void shouldCancelEvent_onlySwap() {
        assertTrue(ClickMethod.SWAP.shouldCancelEvent());
        assertFalse(ClickMethod.MIDDLE.shouldCancelEvent());
        assertFalse(ClickMethod.DOUBLE.shouldCancelEvent());
        assertFalse(ClickMethod.SINGLE.shouldCancelEvent());
        assertFalse(ClickMethod.NONE.shouldCancelEvent());
    }

    @Test
    void isAvailable_doubleAndSingleAndNone_alwaysTrue() {
        assertTrue(ClickMethod.DOUBLE.isAvailable());
        assertTrue(ClickMethod.SINGLE.isAvailable());
        assertTrue(ClickMethod.NONE.isAvailable());
    }

    @Test
    void isAvailable_middle_delegatesToCompat() {
        try (MockedStatic<CompatUtil> compat = Mockito.mockStatic(CompatUtil.class)) {
            compat.when(CompatUtil::isMiddleClickAllowed).thenReturn(true);
            assertTrue(ClickMethod.MIDDLE.isAvailable());
        }
        try (MockedStatic<CompatUtil> compat = Mockito.mockStatic(CompatUtil.class)) {
            compat.when(CompatUtil::isMiddleClickAllowed).thenReturn(false);
            assertFalse(ClickMethod.MIDDLE.isAvailable());
        }
    }

    @Test
    void isAvailable_swap_delegatesToCompat() {
        try (MockedStatic<CompatUtil> compat = Mockito.mockStatic(CompatUtil.class)) {
            compat.when(CompatUtil::isSwapKeyAvailable).thenReturn(true);
            assertTrue(ClickMethod.SWAP.isAvailable());
        }
        try (MockedStatic<CompatUtil> compat = Mockito.mockStatic(CompatUtil.class)) {
            compat.when(CompatUtil::isSwapKeyAvailable).thenReturn(false);
            assertFalse(ClickMethod.SWAP.isAvailable());
        }
    }

    @Test
    void nextAvailable_skipsUnavailableMiddle() {
        try (MockedStatic<CompatUtil> compat = Mockito.mockStatic(CompatUtil.class)) {
            compat.when(CompatUtil::isMiddleClickAllowed).thenReturn(false);
            compat.when(CompatUtil::isSwapKeyAvailable).thenReturn(true);
            // NONE -> MIDDLE (unavailable) -> DOUBLE (available)
            assertEquals(ClickMethod.DOUBLE, ClickMethod.NONE.nextAvailable());
        }
    }

    @Test
    void nextAvailable_skipsBothMiddleAndSwap() {
        try (MockedStatic<CompatUtil> compat = Mockito.mockStatic(CompatUtil.class)) {
            compat.when(CompatUtil::isMiddleClickAllowed).thenReturn(false);
            compat.when(CompatUtil::isSwapKeyAvailable).thenReturn(false);
            // MIDDLE -> DOUBLE (always available)
            assertEquals(ClickMethod.DOUBLE, ClickMethod.MIDDLE.nextAvailable());
        }
    }

    // NOTE: nextAvailable() exits when it finds an available method OR laps back to `this`.
    // If `this` itself is unavailable and is somehow the only method, it returns itself
    // despite being unavailable. This is safe in practice since DOUBLE/SINGLE/NONE are
    // always available, but the guard is implicit rather than enforced.

    @Test
    void unavailableFor_unknownName_null() {
        assertNull(ClickMethod.unavailableFor("BOGUS"));
    }

    @Test
    void unavailableFor_alwaysAvailableMethod_null() {
        assertNull(ClickMethod.unavailableFor("DOUBLE"));
        assertNull(ClickMethod.unavailableFor("SINGLE"));
        assertNull(ClickMethod.unavailableFor("NONE"));
    }

    @Test
    void unavailableFor_availableMiddle_null() {
        try (MockedStatic<CompatUtil> compat = Mockito.mockStatic(CompatUtil.class)) {
            compat.when(CompatUtil::isMiddleClickAllowed).thenReturn(true);
            assertNull(ClickMethod.unavailableFor("MIDDLE"));
        }
    }

    @Test
    void unavailableFor_unavailableMiddle_returnsMethod() {
        try (MockedStatic<CompatUtil> compat = Mockito.mockStatic(CompatUtil.class)) {
            compat.when(CompatUtil::isMiddleClickAllowed).thenReturn(false);
            assertEquals(ClickMethod.MIDDLE, ClickMethod.unavailableFor("MIDDLE"));
        }
    }
}
