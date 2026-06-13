package net.kccricket.clicksorted;

import net.kccricket.clicksorted.model.PlayerSortingPrefs;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the repurposed {@code /clicksorted bundle} toggle command:
 * {@code inventory}, {@code others}, and {@code stacklimit}.
 */
class BundleCommandTest extends AbstractClickSortedTest {

    // -------------------------------------------------------------------------
    // bundle inventory <on|off>
    // -------------------------------------------------------------------------

    @Test
    void bundleInventoryOnOffTogglesPref() {
        PlayerMock player = addOpPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        server.dispatchCommand(player, "clicksorted set bundle inventory on");
        assertTrue(prefs.getBundlePackInventory(player), "'on' should enable inventory packing");

        server.dispatchCommand(player, "clicksorted set bundle inventory off");
        assertFalse(prefs.getBundlePackInventory(player), "'off' should disable inventory packing");
    }

    @Test
    void bundleInventoryAcceptsSynonyms() {
        PlayerMock player = addOpPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        server.dispatchCommand(player, "clicksorted set bundle inventory enable");
        assertTrue(prefs.getBundlePackInventory(player));
        server.dispatchCommand(player, "clicksorted set bundle inventory disable");
        assertFalse(prefs.getBundlePackInventory(player));
        server.dispatchCommand(player, "clicksorted set bundle inventory true");
        assertTrue(prefs.getBundlePackInventory(player));
        server.dispatchCommand(player, "clicksorted set bundle inventory false");
        assertFalse(prefs.getBundlePackInventory(player));
    }

    @Test
    void bundleInventorySendsStatusMessage() {
        PlayerMock player = addOpPlayer("Alice");
        drainMessages(player);
        server.dispatchCommand(player, "clicksorted set bundle inventory on");
        assertTrue(anyMessageContains(player, "ENABLED", "inventory", "Bundle"),
                "Expected an inventory-packing status message");
    }

    // -------------------------------------------------------------------------
    // bundle others <on|off>
    // -------------------------------------------------------------------------

    @Test
    void bundleOthersOnOffTogglesPref() {
        PlayerMock player = addOpPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        server.dispatchCommand(player, "clicksorted set bundle others on");
        assertTrue(prefs.getBundlePackOthers(player), "'on' should enable others packing");

        server.dispatchCommand(player, "clicksorted set bundle others off");
        assertFalse(prefs.getBundlePackOthers(player), "'off' should disable others packing");
    }

    // -------------------------------------------------------------------------
    // bundle stacklimit <n|off>
    // -------------------------------------------------------------------------

    @Test
    void bundleStackLimitSetsNumber() {
        PlayerMock player = addOpPlayer("Alice");
        server.dispatchCommand(player, "clicksorted set bundle stacklimit 32");
        assertEquals(32, plugin.getSortingPrefs().getBundleStackLimit(player));
    }

    @Test
    void bundleStackLimitOffMeansZero() {
        PlayerMock player = addOpPlayer("Alice");
        server.dispatchCommand(player, "clicksorted set bundle stacklimit 32");
        server.dispatchCommand(player, "clicksorted set bundle stacklimit off");
        assertEquals(0, plugin.getSortingPrefs().getBundleStackLimit(player), "'off' means weight-only (0)");
    }

    @Test
    void bundleStackLimitSendsStatusMessage() {
        PlayerMock player = addOpPlayer("Alice");
        drainMessages(player);
        server.dispatchCommand(player, "clicksorted set bundle stacklimit 12");
        assertTrue(anyMessageContains(player, "12", "stack limit", "Bundle"),
                "Expected a stack-limit status message");
    }

    // -------------------------------------------------------------------------
    // Defaults + PDC round-trip
    // -------------------------------------------------------------------------

    @Test
    void defaultsMatchConfig() {
        PlayerMock player = server.addPlayer("Alice");
        var main = plugin.getConfigManager().main();
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        assertEquals(main.getDefaultBundlePackInventory(), prefs.getBundlePackInventory(player));
        assertEquals(main.getDefaultBundlePackOthers(), prefs.getBundlePackOthers(player));
        assertEquals(main.getDefaultBundleStackLimit(), prefs.getBundleStackLimit(player));
    }

    @Test
    void setBundlePackInventoryPersistsInPDC() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        prefs.setBundlePackInventory(player, true);

        assertTrue(prefs.getBundlePackInventory(player));
        NamespacedKey key = new NamespacedKey(plugin, "bundle_inventory");
        assertEquals((byte) 1, player.getPersistentDataContainer().get(key, PersistentDataType.BYTE));
    }

    @Test
    void setBundleStackLimitPersistsInPDC() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        prefs.setBundleStackLimit(player, 24);

        assertEquals(24, prefs.getBundleStackLimit(player));
        NamespacedKey key = new NamespacedKey(plugin, "bundle_stack_limit");
        assertEquals(24, player.getPersistentDataContainer().get(key, PersistentDataType.INTEGER));
    }

    // -------------------------------------------------------------------------
    // Guards
    // -------------------------------------------------------------------------

    @Test
    void bundleCommandIsPlayerOnly() {
        assertDoesNotThrow(() ->
                server.dispatchCommand(server.getConsoleSender(), "clicksorted set bundle inventory on"));
    }

    @Test
    void bareBundleCommandPrintsStatus() {
        PlayerMock player = addOpPlayer("Alice");
        drainMessages(player);
        server.dispatchCommand(player, "clicksorted set bundle");
        assertTrue(anyMessageContains(player, "Bundle", "inventory", "stack limit"),
                "Bare /clicksorted bundle should print the current settings");
    }
}
