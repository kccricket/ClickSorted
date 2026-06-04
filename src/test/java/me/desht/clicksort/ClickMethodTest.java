package me.desht.clicksort;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClickMethodTest {

    @Test
    void shouldCancelEvent_onlySwap() {
        assertTrue(ClickMethod.SWAP.shouldCancelEvent());
        assertFalse(ClickMethod.DOUBLE.shouldCancelEvent());
        assertFalse(ClickMethod.SINGLE.shouldCancelEvent());
        assertFalse(ClickMethod.NONE.shouldCancelEvent());
    }
}
