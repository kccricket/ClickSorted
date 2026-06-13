package net.kccricket.clicksorted;

import net.kccricket.clicksorted.model.ClickMethod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClickMethodTest {

    @Test
    void shouldCancelEvent_swapAndControlDrop() {
        assertTrue(ClickMethod.SWAP.shouldCancelEvent());
        assertTrue(ClickMethod.CONTROL_DROP.shouldCancelEvent());
        assertTrue(ClickMethod.SHIFT_LEFT_CLICK.shouldCancelEvent());
        assertTrue(ClickMethod.SHIFT_RIGHT_CLICK.shouldCancelEvent());
        assertFalse(ClickMethod.DOUBLE_CLICK.shouldCancelEvent());
        assertFalse(ClickMethod.SINGLE_CLICK.shouldCancelEvent());
        assertFalse(ClickMethod.NONE.shouldCancelEvent());
    }

    @Test
    void needsOffhandReset_onlySwap() {
        assertTrue(ClickMethod.SWAP.needsOffhandReset());
        assertFalse(ClickMethod.CONTROL_DROP.needsOffhandReset());
        assertFalse(ClickMethod.DOUBLE_CLICK.needsOffhandReset());
        assertFalse(ClickMethod.SINGLE_CLICK.needsOffhandReset());
        assertFalse(ClickMethod.NONE.needsOffhandReset());
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
