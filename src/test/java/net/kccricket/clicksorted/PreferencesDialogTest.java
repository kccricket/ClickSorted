package net.kccricket.clicksorted;

import net.kccricket.clicksorted.events.PlayerPreferenceChangeEvent;
import net.kccricket.clicksorted.gui.LockGuiHolder;
import net.kccricket.clicksorted.gui.PreferencesDialog;
import net.kccricket.clicksorted.gui.PreferencesDialogService;
import net.kccricket.clicksorted.model.ClickMethod;
import net.kccricket.clicksorted.model.FillAxis;
import net.kccricket.clicksorted.model.PendingPrefs;
import net.kccricket.clicksorted.model.PlayerSortingPrefs;
import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.model.StartCorner;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link PreferencesDialog#applyResponse} — the testable half of the preferences dialog —
 * and the stash/restore bookkeeping in {@link PreferencesDialogService}. Deliberately never calls
 * {@link PreferencesDialog#open}: MockBukkit has no dialog client and no way to construct a real
 * {@code DialogResponseView}, so the dialog-building/rendering code isn't exercised here.
 */
class PreferencesDialogTest extends AbstractClickSortedTest {

    private static PendingPrefs withStackLimit(int limit) {
        return new PendingPrefs(null, null, null, null, null, null, null, null, limit);
    }

    // --- applyResponse: normal apply ---

    @Test
    void applyResponseAppliesEveryPresentField() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        PendingPrefs values = new PendingPrefs(true, ClickMethod.DOUBLE_CLICK, SortingMethod.NAME,
                StartCorner.BOTTOM_RIGHT, FillAxis.VERTICAL, null, true, false, 12);

        PreferencesDialog.applyResponse(plugin, player, values);

        assertTrue(prefs.getEnabled(player));
        assertEquals(ClickMethod.DOUBLE_CLICK, prefs.getClickMethod(player));
        assertEquals(SortingMethod.NAME, prefs.getSortingMethod(player));
        assertEquals(StartCorner.BOTTOM_RIGHT, prefs.getStartCorner(player));
        assertEquals(FillAxis.VERTICAL, prefs.getFillAxis(player));
        assertTrue(prefs.getBundlePackInInventory(player));
        assertFalse(prefs.getBundlePackInContainers(player));
        assertEquals(12, prefs.getBundleStackLimit(player));
    }

    // --- applyResponse: listener veto ---

    private static final class CancellingListener implements Listener {
        boolean fired;

        @EventHandler
        public void onChange(PlayerPreferenceChangeEvent event) {
            fired = true;
            event.setCancelled(true);
        }
    }

    @Test
    void applyResponseReportsVetoAndDoesNotPersist() {
        CancellingListener listener = new CancellingListener();
        server.getPluginManager().registerEvents(listener, plugin);

        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        ClickMethod before = prefs.getClickMethod(player);

        PendingPrefs values = new PendingPrefs(null, ClickMethod.DOUBLE_CLICK, null, null, null,
                null, null, null, null);
        PreferencesDialog.applyResponse(plugin, player, values);

        assertTrue(listener.fired, "Setter should still fire the preference-change event");
        assertEquals(before, prefs.getClickMethod(player), "Cancelled change must not persist");

        List<String> messages = drainMessageList(player);
        assertMessageSent(messages, "MSG.preferenceChangeBlocked");
    }

    // --- applyResponse: hover governed by click method ---

    @Test
    void hoverIsIgnoredWhenResultingClickMethodGovernsIt() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        // SINGLE_CLICK governs hover to false; submit click method + an explicit hover=true in
        // the same batch and confirm the explicit hover value is refused (method wins).
        PendingPrefs values = new PendingPrefs(null, ClickMethod.SINGLE_CLICK, null, null, null,
                true, null, null, null);
        PreferencesDialog.applyResponse(plugin, player, values);

        assertEquals(ClickMethod.SINGLE_CLICK, prefs.getClickMethod(player));
        assertFalse(prefs.getSortOverItems(player),
                "SINGLE_CLICK requires hover=false regardless of the submitted value");
    }

    // --- applyResponse: bundle stack-limit clamping ---

    @Test
    void bundleStackLimitIsClampedToZeroToSixtyFour() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        PreferencesDialog.applyResponse(plugin, player, withStackLimit(999));
        assertEquals(64, prefs.getBundleStackLimit(player), "Above 64 should clamp down to 64");

        PreferencesDialog.applyResponse(plugin, player, withStackLimit(-5));
        assertEquals(0, prefs.getBundleStackLimit(player), "Negative should clamp up to 0 (off)");
    }

    // --- PreferencesDialogService: stash bookkeeping ---

    @Test
    void stashAndClearStashRoundTrip() {
        PlayerMock player = server.addPlayer("Alice");
        PreferencesDialogService service = plugin.getPreferencesDialogService();

        assertFalse(service.hasStash(player));
        service.stash(player, withStackLimit(32));
        assertTrue(service.hasStash(player));
        service.clearStash(player);
        assertFalse(service.hasStash(player));
    }

    @Test
    void closingLockGuiOpenedFromDialogConsumesStash() {
        PlayerMock player = server.addPlayer("Alice");
        PreferencesDialogService service = plugin.getPreferencesDialogService();

        service.stash(player, withStackLimit(20));
        assertTrue(service.hasStash(player));

        InventoryView view = player.openInventory(new LockGuiHolder(plugin, player).getInventory());
        InventoryCloseEvent closeEvent = new InventoryCloseEvent(view);
        server.getPluginManager().callEvent(closeEvent);

        // The stash is consumed synchronously in the close handler; the dialog re-show itself is
        // scheduled a tick later on the player's region scheduler and is not exercised here.
        assertFalse(service.hasStash(player), "Stash should be consumed once the GUI closes");
    }

    @Test
    void closingAGuiWithNoStashIsANoOp() {
        PlayerMock player = server.addPlayer("Alice");
        PreferencesDialogService service = plugin.getPreferencesDialogService();

        InventoryView view = player.openInventory(new LockGuiHolder(plugin, player).getInventory());
        InventoryCloseEvent closeEvent = new InventoryCloseEvent(view);
        server.getPluginManager().callEvent(closeEvent);

        assertFalse(service.hasStash(player));
    }
}
