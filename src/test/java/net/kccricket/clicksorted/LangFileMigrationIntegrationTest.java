package net.kccricket.clicksorted;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end coverage of the legacy {@code lang.yml} -&gt; sparse {@code lang/en_us.yml} migration,
 * run through the real {@link net.kccricket.clicksorted.migration.Migrations#migrateFiles()} entry
 * point against a live plugin instance (unlike {@link FileMigrationTest}, which drives the
 * {@code FileMigration} factories directly against a fake context).
 */
class LangFileMigrationIntegrationTest extends AbstractClickSortedTest {

    @Test
    void migrateFiles_extractsOnlyEditedKeysAndArchivesLegacyFile() throws Exception {
        File dataFolder = plugin.getDataFolder();
        File target = new File(new File(dataFolder, "lang"), "en_us.yml");
        // Clear the override AbstractClickSortedTest already installed so this migration isn't a no-op.
        Files.deleteIfExists(target.toPath());

        YamlConfiguration bundled;
        try (var stream = plugin.getResource("lang/en_us.yml")) {
            bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }

        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("prefix", "MY CUSTOM PREFIX");
        legacy.set("notFromConsole", bundled.getString("notFromConsole")); // left at the bundled default
        legacy.save(new File(dataFolder, "lang.yml"));

        plugin.getMigrations().migrateFiles();

        assertTrue(new File(dataFolder, "lang.yml.bak").exists(), "Legacy lang.yml must be archived");
        assertFalse(new File(dataFolder, "lang.yml").exists());

        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(target);
        assertEquals("MY CUSTOM PREFIX", migrated.getString("prefix"), "The edited key must be carried forward");
        assertNull(migrated.getString("notFromConsole"),
                "A key left at the bundled default must not be carried forward");
    }
}
