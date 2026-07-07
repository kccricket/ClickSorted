package net.kccricket.clicksorted;

import net.kccricket.clicksorted.events.PlayerPreferenceChangeEvent;
import net.kccricket.clicksorted.gui.LockGuiHolder;
import net.kccricket.clicksorted.gui.PreferencesDialog;
import net.kccricket.clicksorted.gui.PreferencesDialog.ButtonSpec;
import net.kccricket.clicksorted.gui.PreferencesDialog.DialogElement;
import net.kccricket.clicksorted.gui.PreferencesDialog.InputSpec;
import net.kccricket.clicksorted.gui.PreferencesDialog.OptionSpec;
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

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link PreferencesDialog#applyResponse} — the testable half of the preferences dialog —
 * and the stash/restore bookkeeping in {@link PreferencesDialogService}, plus the permission-gating
 * and value-selection logic in {@link PreferencesDialog#planInputs}/{@link PreferencesDialog#planButtons}.
 * Deliberately never calls {@link PreferencesDialog#open}: MockBukkit has no dialog client and no way
 * to construct a real {@code DialogResponseView} or any Paper dialog builder object, so the
 * dialog-building/rendering code itself isn't (and can't be) exercised here.
 */
class PreferencesDialogTest extends AbstractClickSortedTest {

    private static final PendingPrefs NO_SEED = new PendingPrefs(null, null, null, null, null, null, null, null, null);

    private static PendingPrefs withStackLimit(int limit) {
        return new PendingPrefs(null, null, null, null, null, null, null, null, limit);
    }

    private static Set<DialogElement> elementsOf(List<InputSpec> specs) {
        return specs.stream().map(InputSpec::element).collect(Collectors.toCollection(() -> EnumSet.noneOf(DialogElement.class)));
    }

    private static Set<DialogElement> buttonElementsOf(List<ButtonSpec> specs) {
        return specs.stream().map(ButtonSpec::element).collect(Collectors.toCollection(() -> EnumSet.noneOf(DialogElement.class)));
    }

    private static InputSpec find(List<InputSpec> specs, DialogElement element) {
        return specs.stream().filter(s -> s.element() == element).findFirst()
                .orElseThrow(() -> new AssertionError(element + " not present in " + specs));
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

    // --- planInputs: permission gating ---

    @Test
    void planInputsIncludesEveryInputForOpPlayer() {
        PlayerMock player = addOpPlayer("Alice");

        Set<DialogElement> present = elementsOf(PreferencesDialog.planInputs(plugin, player, NO_SEED));

        assertEquals(EnumSet.of(DialogElement.ENABLED, DialogElement.CLICK_METHOD, DialogElement.SORT_METHOD,
                DialogElement.START_CORNER, DialogElement.FILL_AXIS, DialogElement.HOVER,
                DialogElement.BUNDLE_IN_INVENTORY, DialogElement.BUNDLE_IN_CONTAINERS,
                DialogElement.BUNDLE_STACK_LIMIT), present);
    }

    @Test
    void revokingEachSoloPermissionDropsOnlyItsInput() {
        record Case(String node, DialogElement element) {
        }
        List<Case> cases = List.of(
                new Case("clicksorted.commands.sort.enabled", DialogElement.ENABLED),
                new Case("clicksorted.commands.click.method", DialogElement.CLICK_METHOD),
                new Case("clicksorted.commands.sort.method", DialogElement.SORT_METHOD),
                new Case("clicksorted.commands.sort.start-corner", DialogElement.START_CORNER),
                new Case("clicksorted.commands.sort.fill-axis", DialogElement.FILL_AXIS));

        for (Case c : cases) {
            PlayerMock player = addOpPlayer("Player_" + c.element());
            player.addAttachment(plugin, c.node(), false);

            Set<DialogElement> present = elementsOf(PreferencesDialog.planInputs(plugin, player, NO_SEED));

            assertFalse(present.contains(c.element()), c.element() + " should be absent when " + c.node() + " is revoked");
            for (DialogElement other : DialogElement.values()) {
                if (other == c.element() || other.key() == null) {
                    continue;
                }
                assertTrue(present.contains(other), other + " should still be present when only " + c.node() + " is revoked");
            }
        }
    }

    @Test
    void revokingBundlePermissionDropsAllThreeBundleInputsTogether() {
        PlayerMock player = addOpPlayer("Alice");
        player.addAttachment(plugin, "clicksorted.commands.bundle", false);

        Set<DialogElement> present = elementsOf(PreferencesDialog.planInputs(plugin, player, NO_SEED));

        assertFalse(present.contains(DialogElement.BUNDLE_IN_INVENTORY));
        assertFalse(present.contains(DialogElement.BUNDLE_IN_CONTAINERS));
        assertFalse(present.contains(DialogElement.BUNDLE_STACK_LIMIT));
        assertTrue(present.contains(DialogElement.ENABLED), "Unrelated inputs should be unaffected");
    }

    @Test
    void nonOpPlayerWithOnlyOneNodeGrantedSeesOnlyThatInput() {
        // Every clicksorted.commands.* node defaults to true (see paper-plugin.yml), so isolating one
        // input requires explicitly revoking every other gate rather than relying on a bare non-op.
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "clicksorted.commands.sort.enabled", false);
        player.addAttachment(plugin, "clicksorted.commands.click.method", false);
        player.addAttachment(plugin, "clicksorted.commands.sort.method", false);
        player.addAttachment(plugin, "clicksorted.commands.sort.fill-axis", false);
        player.addAttachment(plugin, "clicksorted.commands.click.hover", false);
        player.addAttachment(plugin, "clicksorted.commands.bundle", false);
        player.addAttachment(plugin, "clicksorted.commands.sort.start-corner", true);

        Set<DialogElement> present = elementsOf(PreferencesDialog.planInputs(plugin, player, NO_SEED));

        assertEquals(EnumSet.of(DialogElement.START_CORNER), present);
    }

    // --- planInputs: hover dual-gate ---

    @Test
    void hoverAbsentWhenSeededClickMethodGovernsItEvenWithPermission() {
        PlayerMock player = addOpPlayer("Alice");
        PendingPrefs seed = new PendingPrefs(null, ClickMethod.SINGLE_CLICK, null, null, null, null, null, null, null);

        Set<DialogElement> present = elementsOf(PreferencesDialog.planInputs(plugin, player, seed));

        assertFalse(present.contains(DialogElement.HOVER));
    }

    @Test
    void hoverPresentWhenSeededClickMethodDoesNotGovernIt() {
        PlayerMock player = addOpPlayer("Alice");
        PendingPrefs seed = new PendingPrefs(null, ClickMethod.DOUBLE_CLICK, null, null, null, null, null, null, null);

        Set<DialogElement> present = elementsOf(PreferencesDialog.planInputs(plugin, player, seed));

        assertTrue(present.contains(DialogElement.HOVER));
    }

    @Test
    void hoverAbsentWhenStoredClickMethodGovernsItAndNoSeedOverrides() {
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setClickMethod(player, ClickMethod.CONTROL_DROP);

        Set<DialogElement> present = elementsOf(PreferencesDialog.planInputs(plugin, player, NO_SEED));

        assertFalse(present.contains(DialogElement.HOVER));
    }

    @Test
    void hoverAbsentWhenItsOwnPermissionIsRevokedRegardlessOfClickMethod() {
        PlayerMock player = addOpPlayer("Alice");
        player.addAttachment(plugin, "clicksorted.commands.click.hover", false);
        PendingPrefs seed = new PendingPrefs(null, ClickMethod.DOUBLE_CLICK, null, null, null, null, null, null, null);

        Set<DialogElement> present = elementsOf(PreferencesDialog.planInputs(plugin, player, seed));

        assertFalse(present.contains(DialogElement.HOVER));
    }

    // --- planInputs: sort-method option filtering + selection ---

    @Test
    void sortMethodOptionsMatchIsAvailableAndMarkCurrentSelected() {
        PlayerMock player = addOpPlayer("Alice");
        PendingPrefs seed = new PendingPrefs(null, null, SortingMethod.NAME, null, null, null, null, null, null);

        InputSpec spec = find(PreferencesDialog.planInputs(plugin, player, seed), DialogElement.SORT_METHOD);

        Set<String> expectedIds = Arrays.stream(SortingMethod.values())
                .filter(SortingMethod::isAvailable)
                .map(Enum::name)
                .collect(Collectors.toSet());
        Set<String> actualIds = spec.options().stream().map(OptionSpec::id).collect(Collectors.toSet());
        assertEquals(expectedIds, actualIds);

        long selectedCount = spec.options().stream().filter(OptionSpec::selected).count();
        assertEquals(1, selectedCount, "Exactly one option should be marked selected");
        assertTrue(spec.options().stream().anyMatch(o -> o.id().equals("NAME") && o.selected()));
    }

    // --- planInputs: seed-over-stored initial values ---

    @Test
    void seedValueWinsOverStoredPreference() {
        PlayerMock player = addOpPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        prefs.setEnabled(player, false);
        prefs.setStartCorner(player, StartCorner.TOP_LEFT);
        prefs.setFillAxis(player, FillAxis.HORIZONTAL);

        PendingPrefs seed = new PendingPrefs(true, null, null, StartCorner.BOTTOM_RIGHT, FillAxis.VERTICAL,
                null, null, null, null);
        List<InputSpec> inputs = PreferencesDialog.planInputs(plugin, player, seed);

        assertTrue(find(inputs, DialogElement.ENABLED).boolInitial());
        assertTrue(find(inputs, DialogElement.START_CORNER).options().stream()
                .anyMatch(o -> o.id().equals("BOTTOM_RIGHT") && o.selected()));
        assertTrue(find(inputs, DialogElement.FILL_AXIS).options().stream()
                .anyMatch(o -> o.id().equals("VERTICAL") && o.selected()));
    }

    @Test
    void emptySeedFallsBackToStoredPreference() {
        PlayerMock player = addOpPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        prefs.setEnabled(player, false);
        prefs.setStartCorner(player, StartCorner.TOP_RIGHT);

        List<InputSpec> inputs = PreferencesDialog.planInputs(plugin, player, NO_SEED);

        assertFalse(find(inputs, DialogElement.ENABLED).boolInitial());
        assertTrue(find(inputs, DialogElement.START_CORNER).options().stream()
                .anyMatch(o -> o.id().equals("TOP_RIGHT") && o.selected()));
    }

    // --- planInputs: bundle stack-limit clamping ---

    @Test
    void plannedStackLimitIsClampedToZeroToSixtyFour() {
        PlayerMock player = addOpPlayer("Alice");

        assertEquals(64, find(PreferencesDialog.planInputs(plugin, player, withStackLimit(999)),
                DialogElement.BUNDLE_STACK_LIMIT).numberInitial());
        assertEquals(0, find(PreferencesDialog.planInputs(plugin, player, withStackLimit(-5)),
                DialogElement.BUNDLE_STACK_LIMIT).numberInitial());
    }

    @Test
    void unclampedStoredDefaultIsClampedWhenNoSeedOverrides() {
        PlayerMock player = addOpPlayer("Alice");
        plugin.getConfig().set("defaults.bundle_stack_limit", 999);
        plugin.getConfigManager().main().load();

        int initial = find(PreferencesDialog.planInputs(plugin, player, NO_SEED),
                DialogElement.BUNDLE_STACK_LIMIT).numberInitial();

        assertEquals(64, initial, "An unclamped config default above 64 must still be clamped for display");
    }

    // --- planButtons: permission gating ---

    @Test
    void planButtonsIncludesEveryButtonForOpPlayer() {
        PlayerMock player = addOpPlayer("Alice");

        Set<DialogElement> present = buttonElementsOf(PreferencesDialog.planButtons(player));

        assertEquals(EnumSet.of(DialogElement.LOCK_BUTTON, DialogElement.BLACKLIST_BUTTON,
                DialogElement.SAVE_BUTTON, DialogElement.CANCEL_BUTTON), present);
    }

    @Test
    void revokingLockPermissionDropsOnlyLockButton() {
        PlayerMock player = addOpPlayer("Alice");
        player.addAttachment(plugin, "clicksorted.commands.lock", false);

        Set<DialogElement> present = buttonElementsOf(PreferencesDialog.planButtons(player));

        assertFalse(present.contains(DialogElement.LOCK_BUTTON));
        assertTrue(present.contains(DialogElement.BLACKLIST_BUTTON));
        assertTrue(present.contains(DialogElement.SAVE_BUTTON));
        assertTrue(present.contains(DialogElement.CANCEL_BUTTON));
    }

    @Test
    void revokingBundlePermissionDropsOnlyBlacklistButton() {
        PlayerMock player = addOpPlayer("Alice");
        player.addAttachment(plugin, "clicksorted.commands.bundle", false);

        Set<DialogElement> present = buttonElementsOf(PreferencesDialog.planButtons(player));

        assertTrue(present.contains(DialogElement.LOCK_BUTTON));
        assertFalse(present.contains(DialogElement.BLACKLIST_BUTTON));
        assertTrue(present.contains(DialogElement.SAVE_BUTTON));
        assertTrue(present.contains(DialogElement.CANCEL_BUTTON));
    }

    @Test
    void saveAndCancelAlwaysPresentEvenWithEveryOtherButtonRevoked() {
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "clicksorted.commands.lock", false);
        player.addAttachment(plugin, "clicksorted.commands.bundle", false);

        Set<DialogElement> present = buttonElementsOf(PreferencesDialog.planButtons(player));

        assertTrue(present.contains(DialogElement.SAVE_BUTTON));
        assertTrue(present.contains(DialogElement.CANCEL_BUTTON));
        assertFalse(present.contains(DialogElement.LOCK_BUTTON));
        assertFalse(present.contains(DialogElement.BLACKLIST_BUTTON));
    }

    // --- /clicksorted menu: permission-denied dispatch path ---

    @Test
    void menuCommandDeniedWithoutPermissionDoesNotThrow() {
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "clicksorted.commands.menu", false);

        // Brigadier's .requires() blocks dispatch before PreferencesDialog#open runs, so this must
        // never reach the Paper dialog builders (which throw under MockBukkit).
        assertDoesNotThrow(() -> server.dispatchCommand(player, "clicksorted menu"));
    }
}
