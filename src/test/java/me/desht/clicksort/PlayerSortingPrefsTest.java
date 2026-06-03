package me.desht.clicksort;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PlayerSortingPrefs: in-memory cache, SQLite persistence, and the periodic
 * purge/eviction of stale offline-player records.
 */
class PlayerSortingPrefsTest extends AbstractClickSortTest {

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

    private void evictFromCache(UUID uuid) throws Exception {
        cache().remove(uuid);
    }

    /** Returns true if the given UUID has a row in the sorting_prefs table. */
    private boolean existsInDb(UUID uuid) throws Exception {
        return jdbi().withHandle(h ->
                h.createQuery("SELECT COUNT(*) FROM sorting_prefs WHERE player = ?")
                        .bind(0, uuid)
                        .mapTo(Integer.class)
                        .one()) > 0;
    }

    // --- Default values ---

    @Test
    void defaultSortingMethodMatchesConfig() {
        PlayerMock player = server.addPlayer("Alice");
        assertEquals(plugin.getDefaultSortingMethod(),
                plugin.getSortingPrefs().getSortingMethod(player),
                "New player should get the default sort method from config");
    }

    @Test
    void defaultClickMethodMatchesConfig() {
        PlayerMock player = server.addPlayer("Alice");
        assertEquals(plugin.getDefaultClickMethod(),
                plugin.getSortingPrefs().getClickMethod(player),
                "New player should get the default click method from config");
    }

    @Test
    void defaultShiftClickAllowedMatchesConfig() {
        PlayerMock player = server.addPlayer("Alice");
        assertEquals(plugin.getDefaultShiftClick(),
                plugin.getSortingPrefs().getShiftClickAllowed(player),
                "New player should get the default shift-click setting from config");
    }

    @Test
    void storedClickMethodNameIsNullForNewPlayer() {
        // rawClickMethod is only set when prefs are loaded from the DB; for a fresh player
        // created in-memory, it should be null.
        PlayerMock player = server.addPlayer("Alice");
        assertNull(plugin.getSortingPrefs().getStoredClickMethodName(player));
    }

    // --- In-memory CRUD ---

    @Test
    void setSortingMethodUpdatesCache() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        SortingMethod target = SortingMethod.GROUP;
        prefs.setSortingMethod(player, target);

        assertEquals(target, prefs.getSortingMethod(player));
    }

    @Test
    void setClickMethodUpdatesCache() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        prefs.setClickMethod(player, ClickMethod.DOUBLE);

        assertEquals(ClickMethod.DOUBLE, prefs.getClickMethod(player));
    }

    @Test
    void setShiftClickAllowedUpdatesCache() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        boolean initial = prefs.getShiftClickAllowed(player);

        prefs.setShiftClickAllowed(player, !initial);

        assertEquals(!initial, prefs.getShiftClickAllowed(player));
    }

    // --- Persistence through DB ---

    @Test
    void sortingMethodPersistedAndReloadedFromDb() throws Exception {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        prefs.setSortingMethod(player, SortingMethod.GROUP);
        assertTrue(existsInDb(player.getUniqueId()), "Row should exist in DB after set");

        // Evict from cache — next access must reload from DB.
        evictFromCache(player.getUniqueId());

        assertEquals(SortingMethod.GROUP, prefs.getSortingMethod(player),
                "Sort method should survive a cache eviction / DB round-trip");
    }

    @Test
    void clickMethodPersistedAndReloadedFromDb() throws Exception {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        prefs.setClickMethod(player, ClickMethod.DOUBLE);
        evictFromCache(player.getUniqueId());

        assertEquals(ClickMethod.DOUBLE, prefs.getClickMethod(player),
                "Click method should survive cache eviction / DB round-trip");
    }

    @Test
    void rawClickMethodSetAfterDbLoad() throws Exception {
        // When prefs are loaded from the DB (cache miss), rawClickMethod should be the
        // string stored in the DB column — this is what onPlayerJoin reads to validate.
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        prefs.setClickMethod(player, ClickMethod.DOUBLE);
        evictFromCache(player.getUniqueId());

        assertEquals("DOUBLE", prefs.getStoredClickMethodName(player));
    }

    @Test
    void shiftClickPersistedAndReloadedFromDb() throws Exception {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        boolean initial = prefs.getShiftClickAllowed(player);

        prefs.setShiftClickAllowed(player, !initial);
        evictFromCache(player.getUniqueId());

        assertEquals(!initial, prefs.getShiftClickAllowed(player),
                "shift-click setting should survive cache eviction / DB round-trip");
    }

    // --- Purge / eviction ---

    /**
     * Insert a DB row for a UUID that was NEVER added as a MockBukkit player.
     * MockBukkit's PlayerListMock has no lastSeen entry for this UUID, so
     * Bukkit.getOfflinePlayer(uuid).getLastPlayed() returns 0.
     * With purge_after=1d, currentTimeMillis - 0 >> 86400000 ms → always purged.
     */
    private UUID insertGhostPlayerRow() throws Exception {
        UUID ghost = UUID.randomUUID();
        jdbi().useHandle(h ->
                h.execute("INSERT OR REPLACE INTO sorting_prefs VALUES (?, ?, ?, ?)",
                        ghost, "NAME", "SWAP", true));
        return ghost;
    }

    @Test
    void purgeRemovesDbRowForStalePlayer() throws Exception {
        // A UUID that was never added to MockBukkit has lastPlayed() == 0.
        // With purge_after=1d, currentTimeMillis - 0 is always > 1 day → purged.
        UUID ghost = insertGhostPlayerRow();
        assertTrue(existsInDb(ghost), "Ghost row should exist before purge");

        plugin.getSortingPrefs().purge();

        assertFalse(existsInDb(ghost), "Stale ghost row should be deleted after purge");
    }

    @Test
    void purgeEvictsCacheEntryForStalePlayer() throws Exception {
        // Confirm that a cache entry for the ghost UUID is also evicted (if it was present).
        UUID ghost = insertGhostPlayerRow();
        // Manually inject a cache entry for this ghost (simulates an edge case where an evicted
        // player's entry was re-inserted before purge ran).
        Field cacheField = PlayerSortingPrefs.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Object> rawCache = (Map<UUID, Object>) cacheField.get(plugin.getSortingPrefs());
        rawCache.put(ghost, new Object()); // inject any non-null value
        assertTrue(rawCache.containsKey(ghost), "Ghost should be in cache before purge");

        plugin.getSortingPrefs().purge();

        assertFalse(rawCache.containsKey(ghost), "Ghost cache entry should be evicted after purge");
    }

    @Test
    void purgeDoesNotRemoveRecentlyDisconnectedPlayer() throws Exception {
        // MockBukkit records lastSeen = System.currentTimeMillis() on disconnect.
        // purge_after = 1 day → currentTimeMillis - recent_lastSeen < 1 day → NOT purged.
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        prefs.setSortingMethod(player, SortingMethod.GROUP);
        assertTrue(existsInDb(player.getUniqueId()), "Row must exist before purge");

        player.disconnect();  // sets lastSeen = System.currentTimeMillis()
        prefs.purge();

        assertTrue(existsInDb(player.getUniqueId()),
                "Recently disconnected player's row should NOT be purged within 1 day");
    }

    @Test
    void purgeIsIdempotentForEmptyTable() {
        // Purging with no rows in the DB should not throw.
        assertDoesNotThrow(() -> plugin.getSortingPrefs().purge());
    }
}
