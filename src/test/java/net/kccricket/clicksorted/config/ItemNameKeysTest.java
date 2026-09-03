package net.kccricket.clicksorted.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Validates {@code tools/items.generated.yml} — the variant-aware sort-key table produced by
 * {@code ItemNameKeyGenerator} (in the {@code tools} source set, not part of the plugin build).
 * No MockBukkit bootstrap needed: {@code Material.valueOf} is a plain enum lookup and never
 * touches a live server registry (see {@code ItemNameKeyGenerator} for the same reasoning).
 */
class ItemNameKeysTest {

    private static final File GENERATED_FILE = new File("tools/items.generated.yml");

    @Test
    void everyKeyIsARealMaterial() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(GENERATED_FILE);
        Set<String> keys = config.getKeys(false);
        assertTrue(keys.size() > 500, "expected the generated table to have a substantial number of entries, found " + keys.size());

        List<String> unknown = new ArrayList<>();
        for (String key : keys) {
            try {
                Material.valueOf(key);
            } catch (IllegalArgumentException e) {
                unknown.add(key);
            }
        }
        assertTrue(unknown.isEmpty(), "keys with no matching Material constant (table is stale — regenerate via "
                + "./gradlew generateItemNames): " + unknown);
    }

    @Test
    void everyValueIsATokenPermutationOfItsKeyAndDiffersFromIt() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(GENERATED_FILE);
        List<String> mismatched = new ArrayList<>();
        List<String> noOps = new ArrayList<>();

        for (String key : config.getKeys(false)) {
            String value = config.getString(key);
            if (value == null) {
                mismatched.add(key + " -> <null>");
                continue;
            }
            if (value.equals(key)) {
                noOps.add(key);
                continue;
            }
            List<String> keyTokens = sortedTokens(key);
            List<String> valueTokens = sortedTokens(value);
            if (!keyTokens.equals(valueTokens)) {
                mismatched.add(key + ": " + value);
            }
        }

        if (!mismatched.isEmpty()) {
            fail("value is not a token permutation of its key (typo or dropped/duplicated token): " + mismatched);
        }
        assertEquals(Collections.emptyList(), noOps,
                "entries whose value equals the key add no sorting benefit and should be omitted from the generated table");
    }

    private static List<String> sortedTokens(String name) {
        List<String> tokens = new ArrayList<>(Arrays.asList(name.split("_")));
        Collections.sort(tokens);
        return tokens;
    }
}
