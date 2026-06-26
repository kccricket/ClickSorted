package net.kccricket.clicksorted;

import net.kccricket.clicksorted.model.PlayerSortingPrefs;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the /clicksorted bundle toggle commands:
 * {@code in-inventory}, {@code in-containers}, {@code stack-limit}, and {@code enabled}.
 */
class BundleCommandTest extends AbstractClickSortedTest {

    // -------------------------------------------------------------------------
    // bundle enabled in-inventory <yes|no>
    // -------------------------------------------------------------------------

    @Test
    void bundleInventoryOnOffTogglesPref() {
        PlayerMock player = addOpPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        server.dispatchCommand(player, "clicksorted bundle enabled in-inventory on");
        assertTrue(prefs.getBundlePackInInventory(player), "'on' should enable inventory packing");

        server.dispatchCommand(player, "clicksorted bundle enabled in-inventory off");
        assertFalse(prefs.getBundlePackInInventory(player), "'off' should disable inventory packing");
    }

    @Test
    void bundleInventoryAcceptsSynonyms() {
        PlayerMock player = addOpPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        server.dispatchCommand(player, "clicksorted bundle enabled in-inventory enable");
        assertTrue(prefs.getBundlePackInInventory(player));
        server.dispatchCommand(player, "clicksorted bundle enabled in-inventory disable");
        assertFalse(prefs.getBundlePackInInventory(player));
        server.dispatchCommand(player, "clicksorted bundle enabled in-inventory true");
        assertTrue(prefs.getBundlePackInInventory(player));
        server.dispatchCommand(player, "clicksorted bundle enabled in-inventory false");
        assertFalse(prefs.getBundlePackInInventory(player));
    }

    @Test
    void bundleInventorySendsStatusMessage() {
        PlayerMock player = addOpPlayer("Alice");
        drainMessages(player);
        server.dispatchCommand(player, "clicksorted bundle enabled in-inventory on");
        assertMessageSent(drainMessageList(player), "MSG.setBundlePackInInventoryStatus", "ENABLED");
    }

    // -------------------------------------------------------------------------
    // bundle enabled in-containers <yes|no>
    // -------------------------------------------------------------------------

    @Test
    void bundleOthersOnOffTogglesPref() {
        PlayerMock player = addOpPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        server.dispatchCommand(player, "clicksorted bundle enabled in-containers on");
        assertTrue(prefs.getBundlePackInContainers(player), "'on' should enable containers packing");

        server.dispatchCommand(player, "clicksorted bundle enabled in-containers off");
        assertFalse(prefs.getBundlePackInContainers(player), "'off' should disable containers packing");
    }

    // -------------------------------------------------------------------------
    // bundle stack-limit <n|off>
    // -------------------------------------------------------------------------

    @Test
    void bundleStackLimitSetsNumber() {
        PlayerMock player = addOpPlayer("Alice");
        server.dispatchCommand(player, "clicksorted bundle stack-limit 32");
        assertEquals(32, plugin.getSortingPrefs().getBundleStackLimit(player));
    }

    @Test
    void bundleStackLimitOffMeansZero() {
        PlayerMock player = addOpPlayer("Alice");
        server.dispatchCommand(player, "clicksorted bundle stack-limit 32");
        server.dispatchCommand(player, "clicksorted bundle stack-limit off");
        assertEquals(0, plugin.getSortingPrefs().getBundleStackLimit(player), "'off' means weight-only (0)");
    }

    @Test
    void bundleStackLimitSendsStatusMessage() {
        PlayerMock player = addOpPlayer("Alice");
        drainMessages(player);
        server.dispatchCommand(player, "clicksorted bundle stack-limit 12");
        assertMessageSent(drainMessageList(player), "MSG.setBundleStackLimitStatus", "12");
    }

    // -------------------------------------------------------------------------
    // bundle enabled [yes|no] — combined toggle
    // -------------------------------------------------------------------------

    @Test
    void bundleEnabledNoArgSetsBothOnWhenNeitherOn() {
        PlayerMock player = addOpPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        prefs.setBundlePackInInventory(player, false);
        prefs.setBundlePackInContainers(player, false);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted bundle enabled");

        assertTrue(prefs.getBundlePackInInventory(player), "in-inventory should be on");
        assertTrue(prefs.getBundlePackInContainers(player), "in-containers should be on");
        assertMessageSent(drainMessageList(player), "MSG.setBundlePackEnabledStatus", "ENABLED");
    }

    @Test
    void bundleEnabledNoArgSetsBothOffWhenBothOn() {
        PlayerMock player = addOpPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        prefs.setBundlePackInInventory(player, true);
        prefs.setBundlePackInContainers(player, true);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted bundle enabled");

        assertFalse(prefs.getBundlePackInInventory(player), "in-inventory should be off");
        assertFalse(prefs.getBundlePackInContainers(player), "in-containers should be off");
        assertMessageSent(drainMessageList(player), "MSG.setBundlePackEnabledStatus", "DISABLED");
    }

    @Test
    void bundleEnabledNoArgSetsBothOnWhenMixed() {
        PlayerMock player = addOpPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        prefs.setBundlePackInInventory(player, true);
        prefs.setBundlePackInContainers(player, false);

        server.dispatchCommand(player, "clicksorted bundle enabled");

        assertTrue(prefs.getBundlePackInInventory(player));
        assertTrue(prefs.getBundlePackInContainers(player));
    }

    @Test
    void bundleEnabledWithExplicitYesSetsBoth() {
        PlayerMock player = addOpPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        server.dispatchCommand(player, "clicksorted bundle enabled yes");
        assertTrue(prefs.getBundlePackInInventory(player));
        assertTrue(prefs.getBundlePackInContainers(player));

        server.dispatchCommand(player, "clicksorted bundle enabled no");
        assertFalse(prefs.getBundlePackInInventory(player));
        assertFalse(prefs.getBundlePackInContainers(player));
    }

    // -------------------------------------------------------------------------
    // Defaults + PDC round-trip
    // -------------------------------------------------------------------------

    @Test
    void defaultsMatchConfig() {
        PlayerMock player = server.addPlayer("Alice");
        var main = plugin.getConfigManager().main();
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        assertEquals(main.getDefaultBundlePackInInventory(), prefs.getBundlePackInInventory(player));
        assertEquals(main.getDefaultBundlePackInContainers(), prefs.getBundlePackInContainers(player));
        assertEquals(main.getDefaultBundleStackLimit(), prefs.getBundleStackLimit(player));
    }

    @Test
    void setBundlePackInInventoryPersistsInPDC() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        prefs.setBundlePackInInventory(player, true);

        assertTrue(prefs.getBundlePackInInventory(player));
        NamespacedKey key = new NamespacedKey(plugin, "bundle_in_inventory");
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
    // Invalid argument feedback
    // -------------------------------------------------------------------------

    @Test
    void bundleInventoryWithInvalidValueShowsError() {
        PlayerMock player = addOpPlayer("Alice");
        boolean before = plugin.getSortingPrefs().getBundlePackInInventory(player);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted bundle enabled in-inventory GARBAGE");

        assertEquals(before, plugin.getSortingPrefs().getBundlePackInInventory(player),
                "Pref should be unchanged on invalid input");
        assertMessageSent(drainMessageList(player), "MSG.invalidValue", "GARBAGE");
    }

    @Test
    void bundleStackLimitWithInvalidValueShowsError() {
        PlayerMock player = addOpPlayer("Alice");
        int before = plugin.getSortingPrefs().getBundleStackLimit(player);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted bundle stack-limit NOTANUMBER");

        assertEquals(before, plugin.getSortingPrefs().getBundleStackLimit(player),
                "Stack limit should be unchanged on invalid input");
        assertMessageSent(drainMessageList(player), "MSG.invalidValue", "NOTANUMBER");
    }

    // -------------------------------------------------------------------------
    // Guards
    // -------------------------------------------------------------------------

    @Test
    void bundleCommandIsPlayerOnly() {
        assertDoesNotThrow(() ->
                server.dispatchCommand(server.getConsoleSender(), "clicksorted bundle enabled in-inventory on"));
    }

    @Test
    void statusIncludesBundleSettings() {
        PlayerMock player = addOpPlayer("Alice");
        drainMessages(player);
        server.dispatchCommand(player, "clicksorted status");
        List<String> msgs = drainMessageList(player);
        assertMessageSent(msgs, "MSG.statusBundleInInventory");
        assertMessageSent(msgs, "MSG.statusBundleInContainers");
        assertMessageSent(msgs, "MSG.statusBundleStackLimit");
    }
}
