package net.kccricket.clicksorted;

import net.kccricket.clicksorted.config.ResourceUpdater;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResourceUpdaterTest extends AbstractClickSortedTest {

    @Test
    void missingKeyIsAddedAndExistingKeyIsPreserved() throws IOException {
        // Pre-write a groups.yml that has a custom group but is missing a bundled default group.
        File dataFolder = plugin.getDataFolder();
        File groupsFile = new File(dataFolder, "groups.yml");

        YamlConfiguration partial = new YamlConfiguration();
        partial.set("custom-group", List.of("CUSTOM_VALUE"));
        partial.save(groupsFile);

        YamlConfiguration result = ResourceUpdater.update(plugin, "groups.yml");

        // Existing key must be preserved
        assertEquals(List.of("CUSTOM_VALUE"), result.getStringList("custom-group"),
                "Existing key must not be overwritten by update()");

        // Missing key must be added from the bundled default
        assertFalse(result.getStringList("010-building-blocks").isEmpty(),
                "Missing key from bundled default must be added by update()");

        // The file on disk must reflect the same state
        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(groupsFile);
        assertEquals(List.of("CUSTOM_VALUE"), onDisk.getStringList("custom-group"),
                "File on disk must still have the preserved custom value");
        assertFalse(onDisk.getStringList("010-building-blocks").isEmpty(),
                "File on disk must contain the newly added default key");
    }
}
