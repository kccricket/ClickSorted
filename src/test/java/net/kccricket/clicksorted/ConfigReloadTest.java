package net.kccricket.clicksorted;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the bug where {@code /clicksorted reload} only recreated {@code items.yml}
 * after all four config files were deleted mid-session.
 *
 * <p>All four config files (and the {@code lang/en_us.yml} template LangConfig owns) must be
 * recreated from bundled defaults when absent at reload time.
 */
class ConfigReloadTest extends AbstractClickSortedTest {

    @Test
    void reloadAll_recreatesAllFourFilesWhenDeleted() {
        File dataFolder = plugin.getDataFolder();

        // Delete all four config files (lang's is a directory) mid-session
        for (String name : new String[]{"config.yml", "groups.yml", "lang", "items.yml"}) {
            File f = new File(dataFolder, name);
            if (f.exists()) {
                deleteRecursively(f);
                assertTrue(!f.exists(), "Setup: failed to delete " + name);
            }
        }

        // Simulate /clicksorted reload
        plugin.getConfigManager().reloadAll();

        // config.yml, groups.yml, and items.yml must now exist again; lang/en_us.yml is LangConfig's
        // recreated template.
        for (String name : new String[]{"config.yml", "groups.yml", "items.yml"}) {
            File f = new File(dataFolder, name);
            assertTrue(f.exists(), name + " was not recreated by reloadAll()");
        }
        File langDefault = new File(new File(dataFolder, "lang"), "en_us.yml");
        assertTrue(langDefault.exists(), "lang/en_us.yml was not recreated by reloadAll()");
    }

    private static void deleteRecursively(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        f.delete();
    }
}
