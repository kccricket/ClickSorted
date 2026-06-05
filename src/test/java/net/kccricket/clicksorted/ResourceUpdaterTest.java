package net.kccricket.clicksorted;

import net.kccricket.clicksorted.config.ResourceUpdater;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ResourceUpdaterTest extends AbstractClickSortedTest {

    @Test
    void missingKeyIsAddedAndExistingKeyIsPreserved() throws IOException {
        // Pre-write a lang.yml that has tipToReEnable with a custom value but is missing sortBy.
        File dataFolder = plugin.getDataFolder();
        File langFile = new File(dataFolder, "lang.yml");

        YamlConfiguration partial = new YamlConfiguration();
        partial.set("tipToReEnable", "CUSTOM_VALUE");
        partial.save(langFile);

        YamlConfiguration result = ResourceUpdater.update(plugin, "lang.yml");

        // Existing key must be preserved
        assertEquals("CUSTOM_VALUE", result.getString("tipToReEnable"),
                "Existing key must not be overwritten by update()");

        // Missing key must be added from the bundled default
        assertNotNull(result.getString("sortBy"),
                "Missing key from bundled default must be added by update()");

        // The file on disk must reflect the same state
        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(langFile);
        assertEquals("CUSTOM_VALUE", onDisk.getString("tipToReEnable"),
                "File on disk must still have the preserved custom value");
        assertNotNull(onDisk.getString("sortBy"),
                "File on disk must contain the newly added default key");
    }
}
