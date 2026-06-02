package me.desht.clicksort;

import me.desht.dhutils.CompatUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class CompatUtilTest {

    // --- parseSubVersion (pure function, no mocking needed) ---

    @Test
    void subVersion_1_7() {
        assertEquals(7, CompatUtil.parseSubVersion("1.7-R0.4"));
    }

    @Test
    void subVersion_1_12_2() {
        assertEquals(12, CompatUtil.parseSubVersion("1.12.2-R0.1-SNAPSHOT"));
    }

    @Test
    void subVersion_1_17() {
        assertEquals(17, CompatUtil.parseSubVersion("1.17-R0.1-SNAPSHOT"));
    }

    @Test
    void subVersion_1_20_6() {
        assertEquals(20, CompatUtil.parseSubVersion("1.20.6-R0.1-SNAPSHOT"));
    }

    @Test
    void subVersion_newFormat_26() {
        // Regression: version numbers >= 26 previously parsed as sub-version 0
        assertEquals(26, CompatUtil.parseSubVersion("26.0.0-R0.1-SNAPSHOT"));
    }

    @Test
    void subVersion_newFormat_27() {
        assertEquals(27, CompatUtil.parseSubVersion("27.1.2-R0.1-SNAPSHOT"));
    }

    @Test
    void subVersion_malformed_singleComponent() {
        // Bug fix: "1" with no minor component (e.g. "1-R0.1-SNAPSHOT") must not throw
        // ArrayIndexOutOfBoundsException; instead it returns the safe default 0.
        assertEquals(0, CompatUtil.parseSubVersion("1-R0.1-SNAPSHOT"));
    }

    // Helper: stub GetMinecraftSubVersion() only, delegate everything else to real impl
    private void withSubVersion(int version, Runnable test) {
        try (MockedStatic<CompatUtil> compat = Mockito.mockStatic(CompatUtil.class, Mockito.CALLS_REAL_METHODS)) {
            compat.when(CompatUtil::GetMinecraftSubVersion).thenReturn(version);
            test.run();
        }
    }

    // --- isMaterialIdAllowed (numeric IDs removed in 1.13) ---

    @Test
    void materialId_v7_allowed() {
        withSubVersion(7, () -> assertTrue(CompatUtil.isMaterialIdAllowed()));
    }

    @Test
    void materialId_v12_allowed() {
        withSubVersion(12, () -> assertTrue(CompatUtil.isMaterialIdAllowed()));
    }

    @Test
    void materialId_v13_notAllowed() {
        withSubVersion(13, () -> assertFalse(CompatUtil.isMaterialIdAllowed()));
    }

    // --- isMiddleClickAllowed ---
    // NOTE: The cutoff of <= 17 is not documented in the code. Confirm the reason
    // middle-click stopped working at 1.18 (e.g. creative mode inventory change).

    @Test
    void middleClick_v7_allowed() {
        withSubVersion(7, () -> assertTrue(CompatUtil.isMiddleClickAllowed()));
    }

    @Test
    void middleClick_v17_allowed() {
        withSubVersion(17, () -> assertTrue(CompatUtil.isMiddleClickAllowed()));
    }

    @Test
    void middleClick_v18_notAllowed() {
        withSubVersion(18, () -> assertFalse(CompatUtil.isMiddleClickAllowed()));
    }

    @Test
    void middleClick_v26_notAllowed() {
        // Regression: version 26 was previously parsed as sub-version 0 (< 17), so middle-click
        // was incorrectly reported as allowed on newer servers.
        withSubVersion(26, () -> assertFalse(CompatUtil.isMiddleClickAllowed()));
    }

    // --- isSwapKeyAvailable ---

    @Test
    void swapKey_v7_notAvailable() {
        withSubVersion(7, () -> assertFalse(CompatUtil.isSwapKeyAvailable()));
    }

    @Test
    void swapKey_v15_notAvailable() {
        withSubVersion(15, () -> assertFalse(CompatUtil.isSwapKeyAvailable()));
    }

    @Test
    void swapKey_v16_available() {
        withSubVersion(16, () -> assertTrue(CompatUtil.isSwapKeyAvailable()));
    }

    @Test
    void swapKey_v26_available() {
        // Regression: version 26 was previously parsed as sub-version 0 (< 16), so swap key
        // was incorrectly reported as unavailable on newer servers.
        withSubVersion(26, () -> assertTrue(CompatUtil.isSwapKeyAvailable()));
    }
}
