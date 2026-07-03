package net.kccricket.clicksorted;

import net.kccricket.clicksorted.events.PlayerPreferenceChangeEvent;
import net.kccricket.clicksorted.model.ClickMethod;
import net.kccricket.clicksorted.model.SortingMethod;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * Integration tests for the ClickSorted command tree.  Each subcommand is exercised by calling
 * server.dispatchCommand (which routes through ClickSortedPlugin.onCommand → CommandManager →
 * the specific command class).
 *
 * Command permissions from paper-plugin.yml:
 *   sort method / click method / click allow-on-hover  — default true  (any player)
 *   admin reload / admin config / admin debug           — default op
 */
class CommandsTest extends AbstractClickSortedTest {

    // --- sort method ---

    @Test
    void sortCommandChangesSortMethod() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        // NAME is the default; change to GROUP (always available since groups.yml is populated).
        SortingMethod initial = plugin.getSortingPrefs().getSortingMethod(player);
        // Pick a different available method.
        SortingMethod target = initial == SortingMethod.NAME ? SortingMethod.GROUP : SortingMethod.NAME;

        server.dispatchCommand(player, "clicksorted sort method " + target.name());

        assertEquals(target, plugin.getSortingPrefs().getSortingMethod(player));
    }

    @Test
    void sortCommandSendsStatusMessage() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted sort method NAME");

        assertMessageSent(drainMessageList(player), "MSG.setSortingMethodTo", "NAME");
    }

    @Test
    void sortCommandWithInvalidModeShowsUsage() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        SortingMethod before = plugin.getSortingPrefs().getSortingMethod(player);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted sort method TOTALLY_INVALID");

        // Sort method should be unchanged and player receives an error message.
        assertEquals(before, plugin.getSortingPrefs().getSortingMethod(player));
        assertMessageSent(drainMessageList(player), "MSG.invalidValue", "TOTALLY_INVALID");
    }

    @Test
    void clickCommandWithInvalidMethodShowsError() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        ClickMethod before = plugin.getSortingPrefs().getClickMethod(player);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted click method BOGUS_METHOD");

        assertEquals(before, plugin.getSortingPrefs().getClickMethod(player),
                "Click method should be unchanged on invalid input");
        assertMessageSent(drainMessageList(player), "MSG.invalidValue", "BOGUS_METHOD");
    }

    @Test
    void startCornerWithInvalidValueShowsError() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        var before = plugin.getSortingPrefs().getStartCorner(player);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted sort start-corner BOGUS_CORNER");

        assertEquals(before, plugin.getSortingPrefs().getStartCorner(player),
                "Start corner should be unchanged on invalid input");
        assertMessageSent(drainMessageList(player), "MSG.invalidValue", "BOGUS_CORNER");
    }

    @Test
    void fillAxisWithInvalidValueShowsError() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        var before = plugin.getSortingPrefs().getFillAxis(player);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted sort fill-axis BOGUS_AXIS");

        assertEquals(before, plugin.getSortingPrefs().getFillAxis(player),
                "Fill axis should be unchanged on invalid input");
        assertMessageSent(drainMessageList(player), "MSG.invalidValue", "BOGUS_AXIS");
    }

    @Test
    void hoverWithInvalidValueShowsError() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        boolean before = plugin.getSortingPrefs().getSortOverItems(player);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted click allow-on-hover GARBAGE");

        assertEquals(before, plugin.getSortingPrefs().getSortOverItems(player),
                "Hover flag should be unchanged on invalid input");
        assertMessageSent(drainMessageList(player), "MSG.invalidValue", "GARBAGE");
    }

    @Test
    void sortCommandIsPlayerOnly() {
        // Sending the command from the console should fail with "not from console" text.
        // Should not throw; just fail gracefully.
        assertDoesNotThrow(() -> server.dispatchCommand(server.getConsoleSender(), "clicksorted sort method NAME"));
    }

    // --- click method ---

    @Test
    void clickCommandChangesClickMethod() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        // DOUBLE_CLICK is always available.
        server.dispatchCommand(player, "clicksorted click method DOUBLE_CLICK");
        assertEquals(ClickMethod.DOUBLE_CLICK, plugin.getSortingPrefs().getClickMethod(player));
    }

    @Test
    void clickCommandSendsStatusMessage() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted click method DOUBLE_CLICK");

        assertMessageSent(drainMessageList(player), "MSG.setClickMethodTo", "DOUBLE_CLICK");
    }

    @Test
    void clickCommandSingleClickForcesHoverOff() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        plugin.getSortingPrefs().setSortOverItems(player, true);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted click method single_click");

        assertFalse(plugin.getSortingPrefs().getSortOverItems(player),
                "SINGLE_CLICK must force hover off");
        assertMessageSent(drainMessageList(player), "MSG.hoverForcedByClickMethod", "DISABLED");
    }

    @Test
    void clickCommandControlDropForcesHoverOn() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        plugin.getSortingPrefs().setSortOverItems(player, false);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted click method control_drop");

        assertTrue(plugin.getSortingPrefs().getSortOverItems(player),
                "CONTROL_DROP must force hover on");
        assertMessageSent(drainMessageList(player), "MSG.hoverForcedByClickMethod", "ENABLED");
    }

    @Test
    void hoverCommandRejectedForGovernedClickMethod() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        plugin.getSortingPrefs().setClickMethod(player, ClickMethod.CONTROL_DROP);
        plugin.getSortingPrefs().setSortOverItems(player, true); // CONTROL_DROP requires on
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted click allow-on-hover off");

        assertTrue(plugin.getSortingPrefs().getSortOverItems(player),
                "hover must be unchanged — allow-on-hover is hidden via .requires when click method governs it");
    }

    // --- click allow-on-hover ---

    @Test
    void hoverCommandTogglesFlag() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        boolean initial = plugin.getSortingPrefs().getSortOverItems(player);

        server.dispatchCommand(player, "clicksorted click allow-on-hover");
        assertEquals(!initial, plugin.getSortingPrefs().getSortOverItems(player),
                "allow-on-hover command should toggle the flag");

        server.dispatchCommand(player, "clicksorted click allow-on-hover");
        assertEquals(initial, plugin.getSortingPrefs().getSortOverItems(player),
                "Second toggle should restore original value");
    }

    @Test
    void hoverCommandSetsExplicitValue() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);

        server.dispatchCommand(player, "clicksorted click allow-on-hover true");
        assertTrue(plugin.getSortingPrefs().getSortOverItems(player), "allow-on-hover true should enable");

        server.dispatchCommand(player, "clicksorted click allow-on-hover false");
        assertFalse(plugin.getSortingPrefs().getSortOverItems(player), "allow-on-hover false should disable");
    }

    @Test
    void hoverCommandSendsStatusMessage() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted click allow-on-hover");

        assertMessageSent(drainMessageList(player), "MSG.setSortOverItemsStatus");
    }

    // --- cancelled preference changes ---

    private static final class CancellingListener implements Listener {
        boolean cancel;
        Component reason;

        @EventHandler
        public void onChange(PlayerPreferenceChangeEvent event) {
            if (cancel) {
                event.setCancelled(true);
                if (reason != null) {
                    event.setCancelReason(reason);
                }
            }
        }
    }

    @Test
    void cancelledSortMethodDoesNotChangeValueOrSendSuccessMessage() {
        CancellingListener listener = new CancellingListener();
        listener.cancel = true;
        server.getPluginManager().registerEvents(listener, plugin);

        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        SortingMethod before = plugin.getSortingPrefs().getSortingMethod(player);
        SortingMethod target = before == SortingMethod.NAME ? SortingMethod.GROUP : SortingMethod.NAME;
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted sort method " + target.name());

        assertEquals(before, plugin.getSortingPrefs().getSortingMethod(player),
                "A cancelled setter must not change the stored value");
        assertFalse(anyMessageContains(player, "MSG.setSortingMethodTo"),
                "A cancelled change must not report success");
    }

    @Test
    void cancelledSortMethodWithReasonShowsReason() {
        CancellingListener listener = new CancellingListener();
        listener.cancel = true;
        listener.reason = Component.text("locked by admin");
        server.getPluginManager().registerEvents(listener, plugin);

        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted sort method GROUP");

        assertTrue(anyMessageContains(player, "locked by admin"),
                "A listener-supplied cancel reason should be shown to the player");
    }

    @Test
    void cancelledSortMethodWithNoReasonShowsGenericBlockedMessage() {
        CancellingListener listener = new CancellingListener();
        listener.cancel = true;
        server.getPluginManager().registerEvents(listener, plugin);

        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted sort method GROUP");

        assertMessageSent(drainMessageList(player), "MSG.preferenceChangeBlocked");
    }

    @Test
    void cancelledEnabledToggleDoesNotSendSuccessMessage() {
        CancellingListener listener = new CancellingListener();
        listener.cancel = true;
        server.getPluginManager().registerEvents(listener, plugin);

        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        boolean before = plugin.getSortingPrefs().getEnabled(player);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted sort enabled " + (before ? "no" : "yes"));

        assertEquals(before, plugin.getSortingPrefs().getEnabled(player),
                "A cancelled setter must not change the stored value");
        var messages = drainMessageList(player);
        assertFalse(messages.stream().anyMatch(m -> m.contains("MSG.setEnabledStatus")),
                "A cancelled change must not report success");
        assertMessageSent(messages, "MSG.preferenceChangeBlocked");
    }

    // --- admin reload (op-only) ---

    @Test
    void reloadCommandSucceedsForOp() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        assertDoesNotThrow(() -> server.dispatchCommand(player, "clicksorted admin reload"));

        assertMessageSent(drainMessageList(player), "MSG.configReloaded");
    }

    @Test
    void reloadCommandFailsWithoutPermission() {
        PlayerMock player = server.addPlayer("Bob");
        // Not op, no explicit reload permission.
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted admin reload");

        // Should receive an error/denied message, not a reload-success message.
        assertFalse(anyMessageContains(player, "MSG.configReloaded"),
                "Non-op player should not be able to reload");
    }

    // --- admin debug (op-only) ---

    @Test
    void debugCommandChangesLevel() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        // Set debug level to TRACE.
        server.dispatchCommand(player, "clicksorted admin debug TRACE");

        assertMessageSent(drainMessageList(player), "MSG.setDebugLevelTo", "TRACE");
    }

    // --- admin config (op-only) ---

    @Test
    void getcfgCommandShowsConfigEntries() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted admin config");

        // Should receive at least one line containing a config key.
        assertTrue(anyMessageContains(player, "=", "defaults", "sort", "click"),
                "admin config should output config key/value pairs");
    }

    // --- prefix ---

    @Test
    void statusMessageIsPrefixedWithPluginTag() {
        String prefix = plugin.getConfigManager().lang().getMessage("prefix", "");
        assumeFalse(prefix.isEmpty(), "prefix key is empty — nothing to assert");

        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted sort method NAME");

        assertMessageSent(drainMessageList(player), "MSG.prefix");
    }

    @Test
    void rawMessageIsNotPrefixed() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted admin config");

        // admin config uses rawMessage — none of those lines should carry the plugin prefix
        String msg;
        while ((msg = player.nextMessage()) != null) {
            assertFalse(msg.contains("MSG.prefix"),
                    "admin config (raw) lines must not be prefixed: " + msg);
        }
    }

    // --- sort / click group permission gates ---

    @Test
    void sortGroupPermissionDeniedBlocksSortCommands() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        player.addAttachment(plugin, "clicksorted.commands.sort", false);
        player.recalculatePermissions();
        SortingMethod before = plugin.getSortingPrefs().getSortingMethod(player);

        server.dispatchCommand(player, "clicksorted sort method NAME");

        assertEquals(before, plugin.getSortingPrefs().getSortingMethod(player),
                "sort method should not change when clicksorted.commands.sort is denied");
    }

    @Test
    void clickGroupPermissionDeniedBlocksClickCommands() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        player.addAttachment(plugin, "clicksorted.commands.click", false);
        player.recalculatePermissions();
        ClickMethod before = plugin.getSortingPrefs().getClickMethod(player);

        server.dispatchCommand(player, "clicksorted click method SWAP");

        assertEquals(before, plugin.getSortingPrefs().getClickMethod(player),
                "click method should not change when clicksorted.commands.click is denied");
    }
}
