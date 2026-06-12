package net.kccricket.clicksorted;

import net.kccricket.clicksorted.model.ClickMethod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClickMethodTest {

    @Test
    void shouldCancelEvent_swapAndDrop() {
        assertTrue(ClickMethod.SWAP.shouldCancelEvent());
        assertTrue(ClickMethod.DROP.shouldCancelEvent());
        assertFalse(ClickMethod.DOUBLE.shouldCancelEvent());
        assertFalse(ClickMethod.SINGLE.shouldCancelEvent());
        assertFalse(ClickMethod.NONE.shouldCancelEvent());
    }

    @Test
    void needsOffhandReset_onlySwap() {
        assertTrue(ClickMethod.SWAP.needsOffhandReset());
        assertFalse(ClickMethod.DROP.needsOffhandReset());
        assertFalse(ClickMethod.DOUBLE.needsOffhandReset());
        assertFalse(ClickMethod.SINGLE.needsOffhandReset());
        assertFalse(ClickMethod.NONE.needsOffhandReset());
    }

    @Test
    void parse_dropRoundTrips() {
        assertEquals(ClickMethod.DROP, ClickMethod.parse("DROP", ClickMethod.SWAP));
    }
}
