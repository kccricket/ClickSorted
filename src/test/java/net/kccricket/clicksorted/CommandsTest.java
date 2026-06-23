package net.kccricket.clicksorted;

import net.kccricket.clicksorted.model.ClickMethod;
import net.kccricket.clicksorted.model.SortingMethod;
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
 *   set sort-method / set click-method / set hover  — default true  (any player)
 *   reload / getcfg / debug                         — default op
 */
class CommandsTest extends AbstractClickSortedTest {

    // --- set sort-method ---

    @Test
    void sortCommandChangesSortMethod() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        // NAME is the default; change to GROUP (always available since groups.yml is populated).
        SortingMethod initial = plugin.getSortingPrefs().getSortingMethod(player);
        // Pick a different available method.
        SortingMethod target = initial == SortingMethod.NAME ? SortingMethod.GROUP : SortingMethod.NAME;

        server.dispatchCommand(player, "clicksorted set sort-method " + target.name());

        assertEquals(target, plugin.getSortingPrefs().getSortingMethod(player));
    }

    @Test
    void sortCommandSendsStatusMessage() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted set sort-method NAME");

        assertMessageSent(drainMessageList(player), "MSG.setSortingMethodTo", "NAME");
    }

    @Test
    void sortCommandWithInvalidModeShowsUsage() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        SortingMethod before = plugin.getSortingPrefs().getSortingMethod(player);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted set sort-method TOTALLY_INVALID");

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

        server.dispatchCommand(player, "clicksorted set click-method BOGUS_METHOD");

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

        server.dispatchCommand(player, "clicksorted set start-corner BOGUS_CORNER");

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

        server.dispatchCommand(player, "clicksorted set fill-axis BOGUS_AXIS");

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

        server.dispatchCommand(player, "clicksorted set hover GARBAGE");

        assertEquals(before, plugin.getSortingPrefs().getSortOverItems(player),
                "Hover flag should be unchanged on invalid input");
        assertMessageSent(drainMessageList(player), "MSG.invalidValue", "GARBAGE");
    }

    @Test
    void sortCommandIsPlayerOnly() {
        // Sending the command from the console should fail with "not from console" text.
        // Should not throw; just fail gracefully.
        assertDoesNotThrow(() -> server.dispatchCommand(server.getConsoleSender(), "clicksorted set sort-method NAME"));
    }

    // --- set click-method ---

    @Test
    void clickCommandChangesClickMethod() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        // DOUBLE_CLICK is always available.
        server.dispatchCommand(player, "clicksorted set click-method DOUBLE_CLICK");
        assertEquals(ClickMethod.DOUBLE_CLICK, plugin.getSortingPrefs().getClickMethod(player));
    }

    @Test
    void clickCommandSendsStatusMessage() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted set click-method DOUBLE_CLICK");

        assertMessageSent(drainMessageList(player), "MSG.setClickMethodTo", "DOUBLE_CLICK");
    }

    @Test
    void clickCommandSingleClickForcesHoverOff() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        plugin.getSortingPrefs().setSortOverItems(player, true);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted set click-method single_click");

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

        server.dispatchCommand(player, "clicksorted set click-method control_drop");

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

        server.dispatchCommand(player, "clicksorted set hover off");

        assertTrue(plugin.getSortingPrefs().getSortOverItems(player),
                "hover must be unchanged when the click method governs it");
        assertMessageSent(drainMessageList(player), "MSG.hoverGovernedByClickMethod");
    }

    // --- set hover ---

    @Test
    void hoverCommandTogglesFlag() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        boolean initial = plugin.getSortingPrefs().getSortOverItems(player);

        server.dispatchCommand(player, "clicksorted set hover");
        assertEquals(!initial, plugin.getSortingPrefs().getSortOverItems(player),
                "hover command should toggle the flag");

        server.dispatchCommand(player, "clicksorted set hover");
        assertEquals(initial, plugin.getSortingPrefs().getSortOverItems(player),
                "Second toggle should restore original value");
    }

    @Test
    void hoverCommandSetsExplicitValue() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);

        server.dispatchCommand(player, "clicksorted set hover true");
        assertTrue(plugin.getSortingPrefs().getSortOverItems(player), "hover true should enable");

        server.dispatchCommand(player, "clicksorted set hover false");
        assertFalse(plugin.getSortingPrefs().getSortOverItems(player), "hover false should disable");
    }

    @Test
    void hoverCommandSendsStatusMessage() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted set hover");

        assertMessageSent(drainMessageList(player), "MSG.setSortOverItemsStatus");
    }

    // --- reload (op-only) ---

    @Test
    void reloadCommandSucceedsForOp() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        assertDoesNotThrow(() -> server.dispatchCommand(player, "clicksorted reload"));

        assertMessageSent(drainMessageList(player), "MSG.configReloaded");
    }

    @Test
    void reloadCommandFailsWithoutPermission() {
        PlayerMock player = server.addPlayer("Bob");
        // Not op, no explicit reload permission.
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted reload");

        // Should receive an error/denied message, not a reload-success message.
        assertFalse(anyMessageContains(player, "MSG.configReloaded"),
                "Non-op player should not be able to reload");
    }

    // --- debug (op-only) ---

    @Test
    void debugCommandChangesLevel() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        // Set debug level to TRACE.
        server.dispatchCommand(player, "clicksorted debug TRACE");

        assertMessageSent(drainMessageList(player), "MSG.setDebugLevelTo", "TRACE");
    }

    // --- getcfg (op-only) ---

    @Test
    void getcfgCommandShowsConfigEntries() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted getcfg");

        // Should receive at least one line containing a config key.
        assertTrue(anyMessageContains(player, "=", "defaults", "sort", "click"),
                "getcfg should output config key/value pairs");
    }

    // --- prefix ---

    @Test
    void statusMessageIsPrefixedWithPluginTag() {
        String prefix = plugin.getConfigManager().lang().getMessage("prefix", "");
        assumeFalse(prefix.isEmpty(), "prefix key is empty — nothing to assert");

        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted set sort-method NAME");

        assertMessageSent(drainMessageList(player), "MSG.prefix");
    }

    @Test
    void rawMessageIsNotPrefixed() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted getcfg");

        // getcfg uses rawMessage — none of those lines should carry the plugin prefix
        String msg;
        while ((msg = player.nextMessage()) != null) {
            assertFalse(msg.contains("MSG.prefix"),
                    "getcfg (raw) lines must not be prefixed: " + msg);
        }
    }
}
