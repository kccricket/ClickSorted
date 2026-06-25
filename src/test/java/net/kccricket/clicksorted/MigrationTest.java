package net.kccricket.clicksorted;

import net.kccricket.clicksorted.migration.Migration;
import static net.kccricket.clicksorted.migration.Migration.*;
import net.kccricket.clicksorted.migration.Migrations;
import net.kccricket.clicksorted.migration.Store;
import net.kccricket.clicksorted.migration.ValueMigration;
import net.kccricket.clicksorted.model.ClickMethod;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;


import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the generic {@link ValueMigration} / {@link Migrations} framework and the two lifecycle
 * points it is wired into: config (eager rewrite on load) and player PDC (rewrite on join).
 */
class MigrationTest extends AbstractClickSortedTest {

    // --- ValueMigration (pure) ---

    @Test
    void valueMigration_remapsCaseInsensitivelyAndPassesUnknownThrough() {
        ValueMigration m = ValueMigration.builder().rename("OLD").to("NEW").build();
        assertEquals("NEW", m.migrate("OLD"));
        assertEquals("NEW", m.migrate("old"));
        assertEquals("KEEP", m.migrate("KEEP"), "Unmapped tokens are returned unchanged");
        assertNull(m.migrate(null), "null is returned unchanged");
    }

    @Test
    void builder_chainedLineageMapsEveryHopDirectlyToCanonical() {
        ValueMigration m = ValueMigration.builder()
                .rename("A").to("B").to("C")
                .build();

        assertEquals("C", m.migrate("A"));
        assertEquals("C", m.migrate("B"));
        assertEquals("C", m.migrate("C"));
    }

    @Test
    void builder_multiplePathsResolveIndependently() {
        ValueMigration m = ValueMigration.builder()
                .rename("DOUBLE").to("DOUBLE_CLICK")
                .rename("SINGLE").to("SINGLE_CLICK")
                .build();

        assertEquals("DOUBLE_CLICK", m.migrate("DOUBLE"));
        assertEquals("SINGLE_CLICK", m.migrate("SINGLE"));
    }

    @Test
    void builder_convergesInOnePassAcrossMultipleHops() {
        // SINGLE → SINGLE_CLICK → SINGLE_PUNCH: a value stored as the oldest alias must reach the
        // current canonical in a single migrate() call, not one hop per pass.
        ValueMigration m = ValueMigration.builder()
                .rename("SINGLE").to("SINGLE_CLICK").to("SINGLE_PUNCH")
                .build();

        assertEquals("SINGLE_PUNCH", m.migrate("SINGLE"));
        assertEquals("SINGLE_PUNCH", m.migrate("SINGLE_CLICK"));
        assertEquals("SINGLE_PUNCH", m.migrate("SINGLE_PUNCH"));
    }

    @Test
    void builder_toBeforeRenameThrows() {
        assertThrows(IllegalStateException.class, () -> ValueMigration.builder().to("X"));
    }

    // --- PDC migration on join ---

    @Test
    void legacyClickMethodMigratedByMigrateEntryPoint() {
        PlayerMock player = server.addPlayer("Alice");
        NamespacedKey oldKey = new NamespacedKey(plugin, "click");
        NamespacedKey newKey = new NamespacedKey(plugin, "click_mode");
        player.getPersistentDataContainer().set(oldKey, PersistentDataType.STRING, "DOUBLE");

        plugin.getMigrations().migrate(player);

        // Old key is renamed to click_mode; value is further remapped to canonical form.
        assertNull(player.getPersistentDataContainer().get(oldKey, PersistentDataType.STRING),
                "Legacy 'click' key must be removed after rename");
        assertEquals("DOUBLE_CLICK",
                player.getPersistentDataContainer().get(newKey, PersistentDataType.STRING));
        assertEquals(ClickMethod.DOUBLE_CLICK, plugin.getSortingPrefs().getClickMethod(player));
    }

    @Test
    void playerJoinEventTriggersPdcMigration() {
        PlayerMock player = server.addPlayer("Alice");
        NamespacedKey oldKey = new NamespacedKey(plugin, "click");
        NamespacedKey newKey = new NamespacedKey(plugin, "click_mode");
        player.getPersistentDataContainer().set(oldKey, PersistentDataType.STRING, "SINGLE");

        server.getPluginManager().callEvent(new PlayerJoinEvent(player, Component.empty()));

        assertNull(player.getPersistentDataContainer().get(oldKey, PersistentDataType.STRING),
                "Legacy 'click' key must be removed after rename");
        assertEquals("SINGLE_CLICK",
                player.getPersistentDataContainer().get(newKey, PersistentDataType.STRING));
    }

    @Test
    void migratePlayer_renamesClickKeyAndPreservesCanonicalValue() {
        PlayerMock player = server.addPlayer("Alice");
        NamespacedKey oldKey = new NamespacedKey(plugin, "click");
        NamespacedKey newKey = new NamespacedKey(plugin, "click_mode");

        plugin.getMigrations().migrate(player); // nothing stored → neither key created
        assertNull(player.getPersistentDataContainer().get(oldKey, PersistentDataType.STRING));
        assertNull(player.getPersistentDataContainer().get(newKey, PersistentDataType.STRING));

        player.getPersistentDataContainer().set(oldKey, PersistentDataType.STRING, "SWAP");
        plugin.getMigrations().migrate(player); // canonical value moved to new key
        assertNull(player.getPersistentDataContainer().get(oldKey, PersistentDataType.STRING),
                "Old 'click' key must be removed after rename");
        assertEquals("SWAP", player.getPersistentDataContainer().get(newKey, PersistentDataType.STRING),
                "Canonical value must be preserved under the new key");
    }

    @Test
    void migratePlayer_noneClickMethodBecomesEnabledFalseAndSwap() {
        PlayerMock player = server.addPlayer("Alice");
        NamespacedKey clickKey = new NamespacedKey(plugin, "click");
        NamespacedKey clickModeKey = new NamespacedKey(plugin, "click_mode");
        NamespacedKey enabledKey = new NamespacedKey(plugin, "enabled");
        player.getPersistentDataContainer().set(clickKey, PersistentDataType.STRING, "NONE");

        plugin.getMigrations().migrate(player);

        assertNull(player.getPersistentDataContainer().get(clickKey, PersistentDataType.STRING),
                "Old 'click' key must be removed");
        assertEquals("SWAP",
                player.getPersistentDataContainer().get(clickModeKey, PersistentDataType.STRING),
                "click_mode must be SWAP after NONE migration");
        assertEquals((byte) 0,
                player.getPersistentDataContainer().get(enabledKey, PersistentDataType.BYTE),
                "enabled must be false (0) after NONE migration");
        assertFalse(plugin.getSortingPrefs().getEnabled(player));
    }

    @Test
    void migrateConfig_noneClickModeBecomesEnabledFalseAndSwap() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("defaults.click_mode", "NONE");

        assertTrue(plugin.getMigrations().migrate(cfg));
        assertEquals("SWAP", cfg.getString("defaults.click_mode"),
                "defaults.click_mode must be SWAP after NONE migration");
        assertEquals(false, cfg.get("defaults.enabled"),
                "defaults.enabled must be false after NONE migration");
    }

    // --- Config migration (eager rewrite on load) ---

    @Test
    void configValueMigratedInPlace() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("defaults.click_mode", "SINGLE");

        assertTrue(plugin.getMigrations().migrate(cfg));
        assertEquals("SINGLE_CLICK", cfg.getString("defaults.click_mode"));

        assertFalse(plugin.getMigrations().migrate(cfg),
                "Second pass is a no-op once the value is canonical");
    }

    // --- Deprecated removal ---

    @Test
    void migratePlayer_dropsDeprecatedShiftClickKey() {
        PlayerMock player = server.addPlayer("Alice");
        NamespacedKey shiftClick = new NamespacedKey(plugin, "shift_click");
        player.getPersistentDataContainer().set(shiftClick, PersistentDataType.BYTE, (byte) 1);

        plugin.getMigrations().migrate(player);

        assertFalse(player.getPersistentDataContainer().getKeys().contains(shiftClick),
                "Deprecated shift_click PDC key is removed");
    }

    @Test
    void playerJoinEvent_dropsDeprecatedShiftClickKey() {
        PlayerMock player = server.addPlayer("Alice");
        NamespacedKey shiftClick = new NamespacedKey(plugin, "shift_click");
        player.getPersistentDataContainer().set(shiftClick, PersistentDataType.BYTE, (byte) 1);

        server.getPluginManager().callEvent(new PlayerJoinEvent(player, Component.empty()));

        assertFalse(player.getPersistentDataContainer().getKeys().contains(shiftClick));
    }

    @Test
    void migrateConfig_dropsDeprecatedShiftClickPath() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("defaults.shift_click", "SHIFT_LEFT_CLICK");

        assertTrue(plugin.getMigrations().migrate(cfg));
        assertFalse(cfg.contains("defaults.shift_click"),
                "Deprecated defaults.shift_click config path is removed");
    }

    @Test
    void deprecatedRemoval_isNoOpWhenAbsent() {
        PlayerMock player = server.addPlayer("Alice");
        plugin.getMigrations().migrate(player); // no shift_click present → nothing to do

        YamlConfiguration cfg = new YamlConfiguration();
        assertFalse(plugin.getMigrations().migrate(cfg),
                "No legacy values and no deprecated paths → no change");
    }

    // --- Structural transform: player_sort_min / player_sort_max → locked_slots.player ---

    @Test
    void migrateSortBounds_defaultMinMaxAddsNoLockedSlots() {
        // min=9, max=36 are the defaults — no slots are excluded, so no locks should be added.
        // The deprecated keys are still removed (migrate returns true because of path removal),
        // but no locked_slots.player entries should appear.
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("player_sort_min", 9);
        cfg.set("player_sort_max", 36);

        plugin.getMigrations().migrate(cfg); // return value not asserted — key removal makes it true
        assertTrue(cfg.getIntegerList("locked_slots.player").isEmpty(),
                "No slots should be added when min/max are at defaults");
        assertFalse(cfg.contains("player_sort_min"), "player_sort_min must be dropped");
        assertFalse(cfg.contains("player_sort_max"), "player_sort_max must be dropped");
    }

    @Test
    void migrateSortBounds_customMinAddsLockedSlots() {
        // min=18 means slots 9..17 were formerly excluded from sorting.
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("player_sort_min", 18);
        cfg.set("player_sort_max", 36);

        assertTrue(plugin.getMigrations().migrate(cfg));
        java.util.List<Integer> locked = cfg.getIntegerList("locked_slots.player");
        for (int i = 9; i < 18; i++) {
            assertTrue(locked.contains(i), "Slot " + i + " should be in locked_slots.player");
        }
        assertEquals(9, locked.size(), "Exactly 9 slots should be added (9..17)");
        assertFalse(cfg.contains("player_sort_min"), "player_sort_min must be dropped");
        assertFalse(cfg.contains("player_sort_max"), "player_sort_max must be dropped");
    }

    @Test
    void migrateSortBounds_customMaxAddsLockedSlots() {
        // max=27 means slots 27..35 were formerly excluded from sorting.
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("player_sort_min", 9);
        cfg.set("player_sort_max", 27);

        assertTrue(plugin.getMigrations().migrate(cfg));
        java.util.List<Integer> locked = cfg.getIntegerList("locked_slots.player");
        for (int i = 27; i < 36; i++) {
            assertTrue(locked.contains(i), "Slot " + i + " should be in locked_slots.player");
        }
        assertEquals(9, locked.size(), "Exactly 9 slots should be added (27..35)");
    }

    @Test
    void migrateSortBounds_bothCustomMergesCorrectly() {
        // min=18, max=27 → slots 9..17 and 27..35 are excluded.
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("player_sort_min", 18);
        cfg.set("player_sort_max", 27);

        plugin.getMigrations().migrate(cfg);
        java.util.List<Integer> locked = cfg.getIntegerList("locked_slots.player");
        for (int i = 9; i < 18; i++) assertTrue(locked.contains(i), "Slot " + i + " expected");
        for (int i = 27; i < 36; i++) assertTrue(locked.contains(i), "Slot " + i + " expected");
        assertEquals(18, locked.size(), "18 slots total should be locked (9 low + 9 high)");
    }

    @Test
    void migrateSortBounds_unionsWithExistingLockedSlots() {
        // Pre-existing locked_slots.player entry [12] must be preserved and merged (no duplicate).
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("locked_slots.player", java.util.List.of(12));
        cfg.set("player_sort_min", 18); // would add 9..17, which includes 12
        cfg.set("player_sort_max", 36);

        plugin.getMigrations().migrate(cfg);
        java.util.List<Integer> locked = cfg.getIntegerList("locked_slots.player");
        // 9..17 including 12 (no duplicate) — exactly 9 unique entries.
        assertEquals(9, locked.size(), "Should have exactly 9 unique entries (no duplicate for 12)");
        for (int i = 9; i < 18; i++) {
            assertTrue(locked.contains(i), "Slot " + i + " expected in merged result");
        }
    }

    @Test
    void migrateSortBounds_isIdempotent() {
        // First pass should report a change; second pass (no more min/max keys) must be a no-op.
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("player_sort_min", 18);
        cfg.set("player_sort_max", 36);

        assertTrue(plugin.getMigrations().migrate(cfg), "First migration pass must report a change");
        assertFalse(cfg.contains("player_sort_min"), "player_sort_min must be gone after first pass");

        assertFalse(plugin.getMigrations().migrate(cfg),
                "Second migration pass must be a no-op (keys already removed)");
    }

    @Test
    void migrateSortBounds_absentKeysIsNoOp() {
        // If neither key is present, the transform must not change anything.
        YamlConfiguration cfg = new YamlConfiguration();
        assertFalse(plugin.getMigrations().migrate(cfg),
                "Absent player_sort_min/max → no change");
        assertFalse(cfg.contains("locked_slots.player"),
                "No locked_slots.player entry should be created when no migration was needed");
    }

    // --- when…then primitive in isolation ---

    @Test
    void whenThenPrimitive_firesOnMatchAndIsNoOpOnMismatch() {
        // Use an in-memory store fake to verify the when…then mechanism independently.
        java.util.Map<String, String> data = new java.util.HashMap<>();
        Store fake = new Store() {
            @Override public String getString(String key) { return data.get(key); }
            @Override public void setString(String key, String value) { data.put(key, value); }
            @Override public void setBoolean(String key, boolean value) { data.put(key, String.valueOf(value)); }
            @Override public void clear(String key) { data.remove(key); }
            @Override public boolean contains(String key) { return data.containsKey(key); }
        };

        Migration rule = when("mode").is("LEGACY").then(set("mode", "MODERN"), set("flag", "true"));

        // Absent key → no-op.
        assertFalse(rule.apply(fake), "Should be a no-op when key is absent");

        // Wrong value → no-op.
        data.put("mode", "OTHER");
        assertFalse(rule.apply(fake), "Should be a no-op when value doesn't match");
        assertEquals("OTHER", data.get("mode"));

        // Matching value → effects applied.
        data.put("mode", "LEGACY");
        assertTrue(rule.apply(fake));
        assertEquals("MODERN", data.get("mode"), "mode must be rewritten to MODERN");
        assertEquals("true", data.get("flag"), "flag must be set to true");
    }
}
