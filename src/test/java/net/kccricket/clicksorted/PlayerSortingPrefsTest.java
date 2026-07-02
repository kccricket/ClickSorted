package net.kccricket.clicksorted;

import net.kccricket.clicksorted.events.PlayerPreferenceChangeEvent;
import net.kccricket.clicksorted.events.PlayerPreferenceChangeEvent.Change;
import net.kccricket.clicksorted.events.PlayerPreferenceChangeEvent.LockedSlotChange;
import net.kccricket.clicksorted.events.Preference;
import net.kccricket.clicksorted.model.ClickMethod;
import net.kccricket.clicksorted.model.PlayerSortingPrefs;
import net.kccricket.clicksorted.model.SortingMethod;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PlayerSortingPrefs: PDC-backed persistence and default-value fallbacks.
 */
class PlayerSortingPrefsTest extends AbstractClickSortedTest {

    // --- Default values ---

    @Test
    void defaultSortingMethodMatchesConfig() {
        PlayerMock player = server.addPlayer("Alice");
        assertEquals(plugin.getConfigManager().main().getDefaultSortingMethod(),
                plugin.getSortingPrefs().getSortingMethod(player),
                "New player should get the default sort method from config");
    }

    @Test
    void defaultClickMethodMatchesConfig() {
        PlayerMock player = server.addPlayer("Alice");
        assertEquals(plugin.getConfigManager().main().getDefaultClickMethod(),
                plugin.getSortingPrefs().getClickMethod(player),
                "New player should get the default click method from config");
    }

    @Test
    void defaultSortOverItemsMatchesConfig() {
        PlayerMock player = server.addPlayer("Alice");
        assertEquals(plugin.getConfigManager().main().getDefaultSortOverItems(),
                plugin.getSortingPrefs().getSortOverItems(player),
                "New player should get the default sort-over-items setting from config");
    }

    // --- PDC read/write ---

    @Test
    void setSortingMethodPersistsInPDC() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        prefs.setSortingMethod(player, SortingMethod.GROUP);

        assertEquals(SortingMethod.GROUP, prefs.getSortingMethod(player));
        // Verify raw PDC key holds the enum name.
        NamespacedKey key = new NamespacedKey(plugin, "sort_mode");
        assertEquals("GROUP", player.getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }

    @Test
    void setClickMethodPersistsInPDC() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        prefs.setClickMethod(player, ClickMethod.DOUBLE_CLICK);

        assertEquals(ClickMethod.DOUBLE_CLICK, prefs.getClickMethod(player));
        NamespacedKey key = new NamespacedKey(plugin, "click_mode");
        assertEquals("DOUBLE_CLICK", player.getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }

    @Test
    void setSortOverItemsPersistsInPDC() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        boolean initial = prefs.getSortOverItems(player);

        prefs.setSortOverItems(player, !initial);

        assertEquals(!initial, prefs.getSortOverItems(player));
        NamespacedKey key = new NamespacedKey(plugin, "sort_over_items");
        byte expected = (!initial) ? (byte) 1 : (byte) 0;
        assertEquals(expected, player.getPersistentDataContainer().get(key, PersistentDataType.BYTE));
    }

    // --- Locked slots ---

    @Test
    void defaultLockedSlotsIsEmpty() {
        PlayerMock player = server.addPlayer("Alice");
        assertTrue(plugin.getSortingPrefs().getLockedSlots(player).isEmpty(),
                "New player should have no locked slots");
    }

    @Test
    void setLockedSlotsPersistsInPDC() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        prefs.setLockedSlots(player, Set.of(9, 15, 0));

        Set<Integer> stored = prefs.getLockedSlots(player);
        assertEquals(Set.of(9, 15, 0), stored);

        // Verify the raw PDC key holds an INTEGER_ARRAY with the same elements.
        NamespacedKey key = new NamespacedKey(plugin, "locked_slots");
        int[] raw = player.getPersistentDataContainer().get(key, PersistentDataType.INTEGER_ARRAY);
        assertNotNull(raw);
        Set<Integer> rawSet = Arrays.stream(raw).boxed().collect(Collectors.toSet());
        assertEquals(Set.of(9, 15, 0), rawSet);
    }

    @Test
    void setLockedSlotsEmptyRemovesKey() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        prefs.setLockedSlots(player, Set.of(5));
        prefs.setLockedSlots(player, Set.of());

        assertTrue(prefs.getLockedSlots(player).isEmpty());
        NamespacedKey key = new NamespacedKey(plugin, "locked_slots");
        assertNull(player.getPersistentDataContainer().get(key, PersistentDataType.INTEGER_ARRAY),
                "Empty lock set should remove the PDC key");
    }

    @Test
    void getLockedSlotsEmptyReturnsUnmodifiableSet() {
        PlayerMock player = server.addPlayer("Alice");
        Set<Integer> slots = plugin.getSortingPrefs().getLockedSlots(player);
        assertThrows(UnsupportedOperationException.class, () -> slots.add(1),
                "Empty locked-slots set must be unmodifiable");
    }

    @Test
    void getLockedSlotsNonEmptyReturnsUnmodifiableSet() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        prefs.setLockedSlots(player, Set.of(9));
        Set<Integer> slots = prefs.getLockedSlots(player);
        assertThrows(UnsupportedOperationException.class, () -> slots.add(10),
                "Non-empty locked-slots set must be unmodifiable");
    }

    @Test
    void toggleSlotLockedReturnsTrueWhenLocking() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        assertTrue(prefs.toggleSlotLocked(player, 5),
                "toggleSlotLocked should return true when the slot was not previously locked");
        assertTrue(prefs.getLockedSlots(player).contains(5));
    }

    @Test
    void toggleSlotLockedReturnsFalseWhenUnlocking() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        prefs.toggleSlotLocked(player, 5);
        assertFalse(prefs.toggleSlotLocked(player, 5),
                "toggleSlotLocked should return false when the slot was already locked");
        assertFalse(prefs.getLockedSlots(player).contains(5));
    }

    @Test
    void toggleSlotLockedRoundTripsThroughPDC() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        prefs.toggleSlotLocked(player, 12);
        assertTrue(prefs.getLockedSlots(player).contains(12), "Slot should be locked after one toggle");
        prefs.toggleSlotLocked(player, 12);
        assertFalse(prefs.getLockedSlots(player).contains(12), "Slot should be unlocked after second toggle");
    }

    // --- PlayerPreferenceChangeEvent ---

    private static final class PreferenceListener implements Listener {
        boolean fired;
        PlayerPreferenceChangeEvent captured;
        boolean cancel;

        @EventHandler
        public void onChange(PlayerPreferenceChangeEvent event) {
            fired = true;
            captured = event;
            if (cancel) event.setCancelled(true);
        }
    }

    @Test
    void settingClickMethodFiresTypedChangeEvent() {
        PreferenceListener listener = new PreferenceListener();
        server.getPluginManager().registerEvents(listener, plugin);

        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        ClickMethod before = prefs.getClickMethod(player);

        prefs.setClickMethod(player, ClickMethod.DOUBLE_CLICK);

        assertTrue(listener.fired, "Changing click method should fire PlayerPreferenceChangeEvent");
        Change<ClickMethod> change = listener.captured.getChange(Preference.CLICK_MODE);
        assertNotNull(change, "Event should be typed for Preference.CLICK_MODE");
        assertEquals(before, change.oldValue());
        assertEquals(ClickMethod.DOUBLE_CLICK, change.newValue());
        assertNull(listener.captured.getChange(Preference.SORT_MODE),
                "Typed accessor for an unrelated preference should return null");
    }

    @Test
    void cancellingPreferenceChangeEventBlocksPersistence() {
        PreferenceListener listener = new PreferenceListener();
        listener.cancel = true;
        server.getPluginManager().registerEvents(listener, plugin);

        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        ClickMethod before = prefs.getClickMethod(player);

        prefs.setClickMethod(player, ClickMethod.DOUBLE_CLICK);

        assertEquals(before, prefs.getClickMethod(player), "Cancelled change must not persist");
    }

    @Test
    void toggleSlotLockedFiresLockedSlotChange() {
        PreferenceListener listener = new PreferenceListener();
        server.getPluginManager().registerEvents(listener, plugin);

        PlayerMock player = server.addPlayer("Alice");
        plugin.getSortingPrefs().toggleSlotLocked(player, 7);

        assertTrue(listener.fired);
        assertTrue(listener.captured.getChange() instanceof LockedSlotChange,
                "Locked-slot toggle should carry a LockedSlotChange payload");
        LockedSlotChange lockChange = (LockedSlotChange) listener.captured.getChange();
        assertEquals(7, lockChange.slot());
        assertEquals(false, lockChange.oldValue());
        assertEquals(true, lockChange.newValue());
    }

}
