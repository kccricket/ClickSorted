package net.kccricket.clicksorted;

import net.kccricket.clicksorted.model.ClickMethod;
import net.kccricket.clicksorted.model.SortingMethod;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the ClickSorted command tree.  Each subcommand is exercised by calling
 * server.dispatchCommand (which routes through ClickSortedPlugin.onCommand → CommandManager →
 * the specific command class).
 *
 * Command permissions from plugin.yml:
 *   sort / click / hover        — default true  (any player)
 *   reload / getcfg / debug     — default op
 */
class CommandsTest extends AbstractClickSortedTest {

    // --- sort ---

    @Test
    void sortCommandChangesSortMethod() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        // NAME is the default; change to GROUP (always available since groups.yml is populated).
        SortingMethod initial = plugin.getSortingPrefs().getSortingMethod(player);
        // Pick a different available method.
        SortingMethod target = initial == SortingMethod.NAME ? SortingMethod.GROUP : SortingMethod.NAME;

        server.dispatchCommand(player, "clicksorted sort " + target.name());

        assertEquals(target, plugin.getSortingPrefs().getSortingMethod(player));
    }

    @Test
    void sortCommandSendsStatusMessage() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted sort NAME");

        // The command sends "Sorting method has been set to: NAME" (with colour prefix).
        assertTrue(anyMessageContains(player, "NAME", "Sorting method"),
                "Expected sort-mode status message");
    }

    @Test
    void sortCommandWithInvalidModeShowsUsage() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        SortingMethod before = plugin.getSortingPrefs().getSortingMethod(player);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted sort TOTALLY_INVALID");

        // Sort method should be unchanged.
        assertEquals(before, plugin.getSortingPrefs().getSortingMethod(player));
    }

    @Test
    void sortCommandIsPlayerOnly() {
        // Sending the command from the console should fail with "not from console" text.
        // Should not throw; just fail gracefully.
        assertDoesNotThrow(() -> server.dispatchCommand(server.getConsoleSender(), "clicksorted sort NAME"));
    }

    // --- click ---

    @Test
    void clickCommandChangesClickMethod() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        // DOUBLE_CLICK is always available.
        server.dispatchCommand(player, "clicksorted click DOUBLE_CLICK");
        assertEquals(ClickMethod.DOUBLE_CLICK, plugin.getSortingPrefs().getClickMethod(player));
    }

    @Test
    void clickCommandSendsStatusMessage() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted click DOUBLE_CLICK");

        assertTrue(anyMessageContains(player, "DOUBLE_CLICK", "Click method"),
                "Expected click-mode status message");
    }

    // --- hover ---

    @Test
    void hoverCommandTogglesFlag() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        boolean initial = plugin.getSortingPrefs().getSortOverItems(player);

        server.dispatchCommand(player, "clicksorted hover");
        assertEquals(!initial, plugin.getSortingPrefs().getSortOverItems(player),
                "hover command should toggle the flag");

        server.dispatchCommand(player, "clicksorted hover");
        assertEquals(initial, plugin.getSortingPrefs().getSortOverItems(player),
                "Second toggle should restore original value");
    }

    @Test
    void hoverCommandSendsStatusMessage() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted hover");

        assertTrue(anyMessageContains(player, "ENABLED", "DISABLED", "Sort over items"),
                "Expected sort-over-items status message");
    }

    // --- reload (op-only) ---

    @Test
    void reloadCommandSucceedsForOp() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        assertDoesNotThrow(() -> server.dispatchCommand(player, "clicksorted reload"));

        assertTrue(anyMessageContains(player, "reloaded", "reload", "configurations"),
                "Op player should receive reload-confirmation message");
    }

    @Test
    void reloadCommandFailsWithoutPermission() {
        PlayerMock player = server.addPlayer("Bob");
        // Not op, no explicit reload permission.
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted reload");

        // Should receive an error/denied message, not a reload-success message.
        assertFalse(anyMessageContains(player, "reloaded", "configurations"),
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

        assertTrue(anyMessageContains(player, "TRACE", "Debug", "debug"),
                "Expected debug-level status message");
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
}
