package net.kccricket.clicksorted.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Unit tests for the pure version-comparison logic (no Bukkit required). */
class UpdateCheckerTest {

    @Test
    void newerPatchIsGreater() {
        assertTrue(UpdateChecker.compareVersions("1.2.0", "1.1.1") > 0);
        assertTrue(UpdateChecker.compareVersions("1.1.1", "1.2.0") < 0);
    }

    @Test
    void equalVersionsCompareZero() {
        assertEquals(0, UpdateChecker.compareVersions("1.2.3", "1.2.3"));
    }

    @Test
    void releaseIsNewerThanPreRelease() {
        assertTrue(UpdateChecker.compareVersions("1.2.0", "1.2.0-beta.1") > 0);
        assertTrue(UpdateChecker.compareVersions("1.2.0-beta.1", "1.2.0") < 0);
    }

    @Test
    void leadingVIsTolerated() {
        assertEquals(0, UpdateChecker.compareVersions("v1.2.0", "1.2.0"));
        assertTrue(UpdateChecker.compareVersions("v1.3.0", "1.2.0") > 0);
    }

    @Test
    void shorterVersionTreatedAsZeroPadded() {
        assertEquals(0, UpdateChecker.compareVersions("1.2", "1.2.0"));
        assertTrue(UpdateChecker.compareVersions("1.2.1", "1.2") > 0);
    }
}
