package net.kccricket.clicksorted;

import net.kccricket.clicksorted.model.ClickMethod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClickMethodTest {

    @Test
    void shouldCancelEvent_dedicatedGestureMethods() {
        // SWAP/CONTROL_DROP/shift-click/DOUBLE_CLICK all carry a vanilla side-effect that a sort must
        // suppress. SINGLE_CLICK only ever sorts an empty slot (a vanilla no-op), so it does not cancel.
        assertTrue(ClickMethod.SWAP.shouldCancelEvent());
        assertTrue(ClickMethod.CONTROL_DROP.shouldCancelEvent());
        assertTrue(ClickMethod.SHIFT_LEFT_CLICK.shouldCancelEvent());
        assertTrue(ClickMethod.SHIFT_RIGHT_CLICK.shouldCancelEvent());
        assertTrue(ClickMethod.DOUBLE_CLICK.shouldCancelEvent());
        assertFalse(ClickMethod.SINGLE_CLICK.shouldCancelEvent());
    }

    @Test
    void parse_controlDropRoundTrips() {
        assertEquals(ClickMethod.CONTROL_DROP, ClickMethod.parse("CONTROL_DROP", ClickMethod.SWAP));
    }

    @Test
    void parse_unknownFallsBackToDefault() {
        assertEquals(ClickMethod.SWAP, ClickMethod.parse("TOTALLY_INVALID", ClickMethod.SWAP));
        assertEquals(ClickMethod.SWAP, ClickMethod.parse(null, ClickMethod.SWAP));
    }

    @Test
    void requiredSortOverItems_governedMethodsForceAValue() {
        // SINGLE_CLICK with hover on is unusable → forced off; CONTROL_DROP can only fire on an occupied
        // slot → forced on. Every other method leaves hover to the player.
        assertEquals(java.util.Optional.of(false), ClickMethod.SINGLE_CLICK.requiredSortOverItems());
        assertEquals(java.util.Optional.of(true), ClickMethod.CONTROL_DROP.requiredSortOverItems());
        assertTrue(ClickMethod.SWAP.requiredSortOverItems().isEmpty());
        assertTrue(ClickMethod.DOUBLE_CLICK.requiredSortOverItems().isEmpty());
        assertTrue(ClickMethod.SHIFT_LEFT_CLICK.requiredSortOverItems().isEmpty());
        assertTrue(ClickMethod.SHIFT_RIGHT_CLICK.requiredSortOverItems().isEmpty());
    }

    @Test
    void effectiveSortOverItems_governedMethodsIgnorePlayerPref() {
        // SINGLE_CLICK/CONTROL_DROP force their value regardless of what the player has set.
        assertFalse(ClickMethod.SINGLE_CLICK.effectiveSortOverItems(true));
        assertFalse(ClickMethod.SINGLE_CLICK.effectiveSortOverItems(false));
        assertTrue(ClickMethod.CONTROL_DROP.effectiveSortOverItems(true));
        assertTrue(ClickMethod.CONTROL_DROP.effectiveSortOverItems(false));
    }

    @Test
    void effectiveSortOverItems_hoverNeutralMethodsPassThroughPlayerPref() {
        // SWAP/DOUBLE_CLICK/shift-click leave hover to the player's own preference.
        for (ClickMethod method : new ClickMethod[]{
                ClickMethod.SWAP, ClickMethod.DOUBLE_CLICK, ClickMethod.SHIFT_LEFT_CLICK, ClickMethod.SHIFT_RIGHT_CLICK}) {
            assertTrue(method.effectiveSortOverItems(true), method + " should pass through true");
            assertFalse(method.effectiveSortOverItems(false), method + " should pass through false");
        }
    }
}
