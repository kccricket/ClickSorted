package net.kccricket.clicksorted;

import net.kccricket.clicksorted.migration.Migrations;
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
 * Covers {@link Migrations} — ClickSorted's migration catalog — and the two lifecycle points it is
 * wired into: config (eager rewrite on load) and player PDC (rewrite on join). The generic
 * {@code Migration}/{@code ValueMigration} framework primitives are covered independently in
 * KcMcLib.
 */
class MigrationTest extends AbstractClickSortedTest {

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

    // --- Bundle key renames (bundle_inventory → bundle_in_inventory, bundle_others → bundle_in_containers) ---

    @Test
    void migratePlayer_renamesBundleInventoryKey() {
        PlayerMock player = server.addPlayer("Alice");
        NamespacedKey oldKey = new NamespacedKey(plugin, "bundle_inventory");
        NamespacedKey newKey = new NamespacedKey(plugin, "bundle_in_inventory");
        player.getPersistentDataContainer().set(oldKey, PersistentDataType.BYTE, (byte) 1);

        plugin.getMigrations().migrate(player);

        assertNull(player.getPersistentDataContainer().get(oldKey, PersistentDataType.BYTE),
                "Old 'bundle_inventory' key must be removed after rename");
        assertEquals((byte) 1, player.getPersistentDataContainer().get(newKey, PersistentDataType.BYTE),
                "Value must be preserved under 'bundle_in_inventory'");
        assertTrue(plugin.getSortingPrefs().getBundlePackInInventory(player));
    }

    @Test
    void migratePlayer_renamesBundleOthersKey() {
        PlayerMock player = server.addPlayer("Alice");
        NamespacedKey oldKey = new NamespacedKey(plugin, "bundle_others");
        NamespacedKey newKey = new NamespacedKey(plugin, "bundle_in_containers");
        player.getPersistentDataContainer().set(oldKey, PersistentDataType.BYTE, (byte) 1);

        plugin.getMigrations().migrate(player);

        assertNull(player.getPersistentDataContainer().get(oldKey, PersistentDataType.BYTE),
                "Old 'bundle_others' key must be removed after rename");
        assertEquals((byte) 1, player.getPersistentDataContainer().get(newKey, PersistentDataType.BYTE),
                "Value must be preserved under 'bundle_in_containers'");
        assertTrue(plugin.getSortingPrefs().getBundlePackInContainers(player));
    }

    @Test
    void migrateConfig_renamesBundleInventoryPath() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("defaults.bundle_inventory", true);

        assertTrue(plugin.getMigrations().migrate(cfg));
        assertFalse(cfg.contains("defaults.bundle_inventory"),
                "Old 'defaults.bundle_inventory' must be removed after rename");
        assertEquals(true, cfg.get("defaults.bundle_in_inventory"),
                "Value must appear under 'defaults.bundle_in_inventory'");
    }

    @Test
    void migrateConfig_renamesBundleOthersPath() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("defaults.bundle_others", true);

        assertTrue(plugin.getMigrations().migrate(cfg));
        assertFalse(cfg.contains("defaults.bundle_others"),
                "Old 'defaults.bundle_others' must be removed after rename");
        assertEquals(true, cfg.get("defaults.bundle_in_containers"),
                "Value must appear under 'defaults.bundle_in_containers'");
    }

}
