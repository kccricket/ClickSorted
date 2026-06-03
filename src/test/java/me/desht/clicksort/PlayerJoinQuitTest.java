package me.desht.clicksort;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ClickSortPlugin.onPlayerJoin and the interaction with PlayerSortingPrefs.
 * The async-join work added alerts when a player's stored click method is unknown or
 * unavailable on the current server version.
 */
class PlayerJoinQuitTest extends AbstractClickSortTest {

    // --- Reflection helpers ---

    private Jdbi jdbi() throws Exception {
        Field f = PlayerSortingPrefs.class.getDeclaredField("jdbi");
        f.setAccessible(true);
        return (Jdbi) f.get(plugin.getSortingPrefs());
    }

    private Map<UUID, ?> cache() throws Exception {
        Field f = PlayerSortingPrefs.class.getDeclaredField("cache");
        f.setAccessible(true);
        //noinspection unchecked
        return (Map<UUID, ?>) f.get(plugin.getSortingPrefs());
    }

    /** Insert a row directly into the DB, bypassing the cache. */
    private void insertRow(UUID uuid, String sort, String click, boolean shiftClick) throws Exception {
        jdbi().useHandle(h ->
                h.execute("INSERT OR REPLACE INTO sorting_prefs VALUES (?, ?, ?, ?)",
                        uuid, sort, click, shiftClick));
    }

    /** Evict the given UUID from the in-memory cache so the next access reads the DB. */
    private void evictFromCache(UUID uuid) throws Exception {
        cache().remove(uuid);
    }

    // --- Tests ---

    @Test
    void freshPlayerJoinProducesNoAlert() {
        // A brand-new player with no DB row should not receive any plugin alert on join.
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        player.assertNoMoreSaid();
    }

    @Test
    void rejoinWithUnknownStoredMethodSendsAlert() throws Exception {
        // Scenario: the player's DB row contains a click-method name that is no longer a
        // valid enum value (e.g. from an old plugin version or manual DB edit).
        // onPlayerJoin should send the "clickMethodUnknown" alert.

        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);  // clear any join-broadcast messages

        // Write an invalid method name directly to the DB and evict the cache entry
        // so the next getPrefs() loads from DB rather than cache.
        insertRow(player.getUniqueId(), "NAME", "COMPLETELY_BOGUS_METHOD", true);
        evictFromCache(player.getUniqueId());

        // Disconnect then reconnect; reconnect fires PlayerJoinEvent again.
        player.disconnect();
        player.reconnect();
        // Wait for the async join handler and its inner sync follow-up to complete.
        waitForJoinHandler();

        // Find the alert in the message queue (there may also be a join broadcast).
        boolean found = false;
        String msg;
        while ((msg = player.nextMessage()) != null) {
            if (msg.contains("COMPLETELY_BOGUS_METHOD") || msg.contains("not recognised")) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Expected 'clickMethodUnknown' alert but none was received");
    }

    @Test
    void rejoinWithNullStoredMethodProducesNoAlert() throws Exception {
        // A player whose prefs were created in-memory only (rawClickMethod = null)
        // should produce no alert on subsequent joins.
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);

        // Ensure they have a cache entry (triggers default SortPrefs creation).
        plugin.getSortingPrefs().getSortingMethod(player);

        // getStoredClickMethodName should be null → no alert.
        assertNull(plugin.getSortingPrefs().getStoredClickMethodName(player));

        drainMessages(player);
        player.disconnect();
        player.reconnect();
        waitForJoinHandler();

        // No plugin messages should be queued.
        player.assertNoMoreSaid();
    }

    @Test
    void rejoinWithValidStoredMethodProducesNoAlert() throws Exception {
        // A player with a valid click method stored in DB should get no alert when rejoining.
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);

        // Write prefs through the API (writes to DB).
        plugin.getSortingPrefs().setClickMethod(player, ClickMethod.DOUBLE);

        // Evict from cache so the next join re-reads from DB.
        evictFromCache(player.getUniqueId());
        drainMessages(player);

        player.disconnect();
        player.reconnect();
        waitForJoinHandler();

        // The stored method "DOUBLE" is a valid, available enum value → no alert.
        player.assertNoMoreSaid();
    }

    @Test
    void getStoredClickMethodNameReturnsNullForNewPlayer() {
        // A player who has never had prefs persisted should have rawClickMethod == null.
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        assertNull(plugin.getSortingPrefs().getStoredClickMethodName(player));
    }

    @Test
    void getStoredClickMethodNameReturnedFromDbAfterEviction() throws Exception {
        // After writing prefs to DB, evicting from cache, and re-reading, rawClickMethod
        // should be the string stored in the DB (the enum name).
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);

        plugin.getSortingPrefs().setClickMethod(player, ClickMethod.DOUBLE);
        evictFromCache(player.getUniqueId());

        // Re-read forces a DB lookup → SortPrefsMapper sets rawClickMethod from the DB.
        String storedName = plugin.getSortingPrefs().getStoredClickMethodName(player);
        assertEquals("DOUBLE", storedName);
    }
}
