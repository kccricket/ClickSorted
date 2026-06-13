package net.kccricket.clicksorted;

import net.kccricket.clicksorted.migration.Migrations;
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
        NamespacedKey key = new NamespacedKey(plugin, "click");
        player.getPersistentDataContainer().set(key, PersistentDataType.STRING, "DOUBLE");

        plugin.getMigrations().migrate(player);

        // The stored token is rewritten in place, so reads see the renamed constant.
        assertEquals("DOUBLE_CLICK",
                player.getPersistentDataContainer().get(key, PersistentDataType.STRING));
        assertEquals(ClickMethod.DOUBLE_CLICK, plugin.getSortingPrefs().getClickMethod(player));
    }

    @Test
    void playerJoinEventTriggersPdcMigration() {
        PlayerMock player = server.addPlayer("Alice");
        NamespacedKey key = new NamespacedKey(plugin, "click");
        player.getPersistentDataContainer().set(key, PersistentDataType.STRING, "SINGLE");

        server.getPluginManager().callEvent(new PlayerJoinEvent(player, Component.empty()));

        assertEquals("SINGLE_CLICK",
                player.getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }

    @Test
    void migratePlayer_isNoOpForCanonicalAndMissingValues() {
        PlayerMock player = server.addPlayer("Alice");
        NamespacedKey key = new NamespacedKey(plugin, "click");

        plugin.getMigrations().migrate(player); // no stored value → nothing to migrate
        assertNull(player.getPersistentDataContainer().get(key, PersistentDataType.STRING));

        player.getPersistentDataContainer().set(key, PersistentDataType.STRING, "SWAP");
        plugin.getMigrations().migrate(player); // canonical value → unchanged
        assertEquals("SWAP", player.getPersistentDataContainer().get(key, PersistentDataType.STRING));
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
}
