package net.kccricket.clicksorted;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the bug where {@code /clicksort reload} only recreated {@code items.yml}
 * after all four config files were deleted mid-session.
 *
 * <p>All four files must be recreated from bundled defaults when they are absent at reload time.
 */
class ConfigReloadTest extends AbstractClickSortedTest {

    @Test
    void reloadAll_recreatesAllFourFilesWhenDeleted() {
        File dataFolder = plugin.getDataFolder();

        // Delete all four config files mid-session
        for (String name : new String[]{"config.yml", "groups.yml", "lang.yml", "items.yml"}) {
            File f = new File(dataFolder, name);
            if (f.exists()) {
                assertTrue(f.delete(), "Setup: failed to delete " + name);
            }
        }

        // Simulate /clicksort reload
        plugin.getConfigManager().reloadAll();

        // All four must now exist again
        for (String name : new String[]{"config.yml", "groups.yml", "lang.yml", "items.yml"}) {
            File f = new File(dataFolder, name);
            assertTrue(f.exists(), name + " was not recreated by reloadAll()");
        }
    }
}
