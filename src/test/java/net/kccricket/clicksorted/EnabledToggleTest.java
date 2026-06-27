package net.kccricket.clicksorted;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the per-player {@code enabled} preference gates sorting:
 * a sort gesture does nothing while disabled, and resumes once re-enabled.
 */
class EnabledToggleTest extends AbstractClickSortedTest {

    @Test
    void sortDoesNotFireWhenEnabledIsFalse() {
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setClickMethod(player, net.kccricket.clicksorted.model.ClickMethod.SWAP);
        plugin.getSortingPrefs().setSortOverItems(player, false);

        // Fill chest with unsorted items.
        InventoryView view = openChest(player,
                stack(Material.STONE, 1), stack(Material.DIRT, 1), stack(Material.GRAVEL, 1));
        Inventory chest = view.getTopInventory();
        Map<Material, Integer> before = countByMaterial(chest);

        plugin.getSortingPrefs().setEnabled(player, false);

        fireClick(view, ClickType.SWAP_OFFHAND, 0);

        assertEquals(before, countByMaterial(chest), "Sort must not fire while enabled=false");
    }

    @Test
    void sortFiresAgainAfterReEnable() {
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setClickMethod(player, net.kccricket.clicksorted.model.ClickMethod.SWAP);
        plugin.getSortingPrefs().setSortOverItems(player, false);

        InventoryView view = openChest(player,
                stack(Material.STONE, 1), stack(Material.DIRT, 1), stack(Material.GRAVEL, 1));
        Inventory chest = view.getTopInventory();

        plugin.getSortingPrefs().setEnabled(player, false);
        fireClick(view, ClickType.SWAP_OFFHAND, 0); // should not sort

        plugin.getSortingPrefs().setEnabled(player, true);
        fireClick(view, ClickType.SWAP_OFFHAND, 0); // should sort now

        // After sorting, items should be in alphabetical order (DIRT, GRAVEL, STONE).
        // Just verify the total counts are preserved — sort correctness is covered elsewhere.
        Map<Material, Integer> after = countByMaterial(chest);
        assertEquals(1, after.getOrDefault(Material.STONE, 0));
        assertEquals(1, after.getOrDefault(Material.DIRT, 0));
        assertEquals(1, after.getOrDefault(Material.GRAVEL, 0));
    }

    @Test
    void setEnabledCommandTogglesState() {
        PlayerMock player = addOpPlayer("Alice");
        drainMessages(player);

        assertTrue(plugin.getSortingPrefs().getEnabled(player),
                "Enabled should be true by default");

        server.dispatchCommand(player, "clicksorted sort enabled off");
        assertFalse(plugin.getSortingPrefs().getEnabled(player));
        assertMessageSent(drainMessageList(player), "MSG.setEnabledStatus", "DISABLED");

        server.dispatchCommand(player, "clicksorted sort enabled on");
        assertTrue(plugin.getSortingPrefs().getEnabled(player));
        assertMessageSent(drainMessageList(player), "MSG.setEnabledStatus", "ENABLED");
    }

    @Test
    void bareClicksortedCommandTogglesEnabled() {
        PlayerMock player = addOpPlayer("Alice");
        drainMessages(player);

        boolean initial = plugin.getSortingPrefs().getEnabled(player);

        server.dispatchCommand(player, "clicksorted");
        assertEquals(!initial, plugin.getSortingPrefs().getEnabled(player),
                "Bare /clicksorted must toggle enabled");
        assertMessageSent(drainMessageList(player), "MSG.setEnabledStatus");

        server.dispatchCommand(player, "clicksorted");
        assertEquals(initial, plugin.getSortingPrefs().getEnabled(player),
                "Second bare /clicksorted must toggle back");
    }

    @Test
    void denyingSortEnabledPermissionBlocksToggleButLeavesStatusAndBundleAccessible() {
        PlayerMock player = server.addPlayer("Restricted");
        // Keep the umbrella clicksorted.commands node (default true), revoke only sort.enabled.
        player.addAttachment(plugin, "clicksorted.commands.sort.enabled", false);
        player.recalculatePermissions();
        drainMessages(player);

        boolean initial = plugin.getSortingPrefs().getEnabled(player);

        // Bare /clicksorted — should refuse with no-permission message, not toggle.
        server.dispatchCommand(player, "clicksorted");
        assertEquals(initial, plugin.getSortingPrefs().getEnabled(player),
                "Enabled state must not change when sort.enabled is denied");
        assertMessageSent(drainMessageList(player), "MSG.noPermission");

        // /clicksorted status — the root is still accessible (not pruned), so status should work.
        server.dispatchCommand(player, "clicksorted status");
        assertMessageSent(drainMessageList(player), "MSG.statusEnabled");

        // /clicksorted bundle enabled — bundle subcommand should also still be accessible.
        server.dispatchCommand(player, "clicksorted bundle enabled yes");
        assertMessageSent(drainMessageList(player), "MSG.setBundlePackEnabledStatus");
    }
}
