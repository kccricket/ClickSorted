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
        assertFalse(ClickMethod.NONE.shouldCancelEvent());
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
}
