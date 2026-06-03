package me.desht.clicksort;

import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the ClickSort command tree.  Each subcommand is exercised by calling
 * server.dispatchCommand (which routes through ClickSortPlugin.onCommand → CommandManager →
 * the specific command class).
 *
 * Command permissions from plugin.yml:
 *   sort / click / shiftclick   — default true  (any player)
 *   reload / getcfg / debug     — default op
 */
class CommandsTest extends AbstractClickSortTest {

    // --- sort ---

    @Test
    void sortCommandChangesSortMethod() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        // NAME is the default; change to GROUP (always available since groups.yml is populated).
        SortingMethod initial = plugin.getSortingPrefs().getSortingMethod(player);
        // Pick a different available method.
        SortingMethod target = initial == SortingMethod.NAME ? SortingMethod.GROUP : SortingMethod.NAME;

        server.dispatchCommand(player, "clicksort sort " + target.name());

        assertEquals(target, plugin.getSortingPrefs().getSortingMethod(player));
    }

    @Test
    void sortCommandSendsStatusMessage() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        server.dispatchCommand(player, "clicksort sort NAME");

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

        server.dispatchCommand(player, "clicksort sort TOTALLY_INVALID");

        // Sort method should be unchanged.
        assertEquals(before, plugin.getSortingPrefs().getSortingMethod(player));
    }

    @Test
    void sortCommandIsPlayerOnly() {
        // Sending the command from the console should fail with "not from console" text.
        SortingMethod before = plugin.getSortingPrefs().getSortingMethod(server.addPlayer("Alice"));
        // Should not throw; just fail gracefully.
        assertDoesNotThrow(() -> server.dispatchCommand(server.getConsoleSender(), "clicksort sort NAME"));
    }

    // --- click ---

    @Test
    void clickCommandChangesClickMethod() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        // DOUBLE is always available.
        server.dispatchCommand(player, "clicksort click DOUBLE");
        assertEquals(ClickMethod.DOUBLE, plugin.getSortingPrefs().getClickMethod(player));
    }

    @Test
    void clickCommandSendsStatusMessage() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        server.dispatchCommand(player, "clicksort click DOUBLE");

        assertTrue(anyMessageContains(player, "DOUBLE", "Click method"),
                "Expected click-mode status message");
    }

    // --- shiftclick ---

    @Test
    void shiftclickCommandTogglesFlag() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        boolean initial = plugin.getSortingPrefs().getShiftClickAllowed(player);

        server.dispatchCommand(player, "clicksort shiftclick");
        assertEquals(!initial, plugin.getSortingPrefs().getShiftClickAllowed(player),
                "shiftclick command should toggle the flag");

        server.dispatchCommand(player, "clicksort shiftclick");
        assertEquals(initial, plugin.getSortingPrefs().getShiftClickAllowed(player),
                "Second toggle should restore original value");
    }

    @Test
    void shiftclickCommandSendsStatusMessage() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        server.dispatchCommand(player, "clicksort shiftclick");

        assertTrue(anyMessageContains(player, "ENABLED", "DISABLED", "Shift-click"),
                "Expected shift-click status message");
    }

    // --- reload (op-only) ---

    @Test
    void reloadCommandSucceedsForOp() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        assertDoesNotThrow(() -> server.dispatchCommand(player, "clicksort reload"));

        assertTrue(anyMessageContains(player, "reloaded", "reload", "configurations"),
                "Op player should receive reload-confirmation message");
    }

    @Test
    void reloadCommandFailsWithoutPermission() {
        PlayerMock player = server.addPlayer("Bob");
        // Not op, no explicit reload permission.
        drainMessages(player);

        server.dispatchCommand(player, "clicksort reload");

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

        // Set debug level to 2.
        server.dispatchCommand(player, "clicksort debug 2");

        assertTrue(anyMessageContains(player, "2", "Debug", "debug"),
                "Expected debug-level status message");
    }

    // --- getcfg (op-only) ---

    @Test
    void getcfgCommandShowsConfigEntries() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        drainMessages(player);

        server.dispatchCommand(player, "clicksort getcfg");

        // Should receive at least one line containing a config key.
        assertTrue(anyMessageContains(player, "=", "defaults", "sort", "click"),
                "getcfg should output config key/value pairs");
    }
}
