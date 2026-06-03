package me.desht.clicksort;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ClickSortPlugin.onPlayerJoin and the interaction with PlayerSortingPrefs.
 * The join handler alerts when a player's stored click method is unknown or unavailable
 * on the current server version.
 */
class PlayerJoinQuitTest extends AbstractClickSortTest {

    // --- Test-local helpers ---

    /** Seed a click method string directly into the player's PDC, bypassing the prefs API. */
    private void seedClickMethod(PlayerMock player, String clickMethodName) {
        NamespacedKey key = new NamespacedKey(plugin, "click");
        player.getPersistentDataContainer().set(key, PersistentDataType.STRING, clickMethodName);
    }

    // --- Tests ---

    @Test
    void freshPlayerJoinProducesNoAlert() {
        // A brand-new player with no PDC key should not receive any plugin alert on join.
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        player.assertNoMoreSaid();
    }

    @Test
    void rejoinWithUnknownStoredMethodSendsAlert() {
        // Scenario: the player's PDC contains a click-method name that is no longer a
        // valid enum value (e.g. from an old plugin version).
        // onPlayerJoin should send the "clickMethodUnknown" alert.
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        // Seed an invalid method name directly into PDC.
        seedClickMethod(player, "COMPLETELY_BOGUS_METHOD");

        // Disconnect then reconnect; reconnect fires PlayerJoinEvent again.
        // MockBukkit preserves PDC on the same PlayerMock object across reconnect.
        player.disconnect();
        player.reconnect();

        assertTrue(anyMessageContains(player, "COMPLETELY_BOGUS_METHOD", "not recognised"),
                "Expected 'clickMethodUnknown' alert but none was received");
    }

    @Test
    void rejoinWithNullStoredMethodProducesNoAlert() {
        // A player with no PDC click key (rawClickMethod = null) should produce no alert.
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);

        assertNull(plugin.getSortingPrefs().getStoredClickMethodName(player));

        drainMessages(player);
        player.disconnect();
        player.reconnect();

        player.assertNoMoreSaid();
    }

    @Test
    void rejoinWithValidStoredMethodProducesNoAlert() {
        // A player with a valid click method in PDC should get no alert when rejoining.
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);

        plugin.getSortingPrefs().setClickMethod(player, ClickMethod.DOUBLE);
        drainMessages(player);

        player.disconnect();
        player.reconnect();

        player.assertNoMoreSaid();
    }

    @Test
    void getStoredClickMethodNameReturnsNullForNewPlayer() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        assertNull(plugin.getSortingPrefs().getStoredClickMethodName(player));
    }

    @Test
    void getStoredClickMethodNameReturnedFromPDC() {
        // After writing prefs through the API, getStoredClickMethodName returns the enum name.
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);

        plugin.getSortingPrefs().setClickMethod(player, ClickMethod.DOUBLE);

        assertEquals("DOUBLE", plugin.getSortingPrefs().getStoredClickMethodName(player));
    }
}
