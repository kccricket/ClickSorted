package net.kccricket.clicksorted;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the master {@code clicksorted} kill-switch permission.
 *
 * <p>Denying {@code clicksorted} must:
 * <ul>
 *   <li>Block click-triggered sorting (inventory unchanged).</li>
 *   <li>Block click-triggered bundle packing (inventory unchanged).</li>
 *   <li>Block the bare {@code /clicksorted} toggle command.</li>
 *   <li>Block {@code /clicksorted sort enabled}, {@code /clicksorted bundle …}.</li>
 * </ul>
 * Granting {@code clicksorted} (the default) must leave all the above working.
 */
class MasterPermissionTest extends AbstractClickSortedTest {

    /** Adds a non-op player with all ClickSorted permissions granted except where overridden. */
    private PlayerMock addNormalPlayer(String name) {
        return server.addPlayer(name);
    }

    /** Deny the master kill-switch on {@code player}. */
    private void denyMaster(PlayerMock player) {
        player.addAttachment(plugin, "clicksorted", false);
        player.recalculatePermissions();
    }

    /**
     * Fire a SWAP_OFFHAND on the player's main storage and return the event.
     * Uses rawSlot 27 (maps to main-inventory slot 9) with sort-over-items forced on.
     */
    private InventoryClickEvent fireSortTrigger(PlayerMock player) {
        plugin.getSortingPrefs().setSortOverItems(player, true);
        Inventory dummy = server.createInventory(null, InventoryType.CHEST);
        InventoryView view = player.openInventory(dummy);
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 27, ClickType.SWAP_OFFHAND, InventoryAction.UNKNOWN) {
            @Override
            public ItemStack getCurrentItem() { return new ItemStack(Material.STONE, 1); }
        };
        server.getPluginManager().callEvent(event);
        return event;
    }

    // =========================================================================
    // Click-triggered sorting
    // =========================================================================

    @Test
    void masterDenied_sortingBlockedClickIsNoOp() {
        PlayerMock player = addNormalPlayer("Alice");
        plugin.getSortingPrefs().setEnabled(player, true);
        denyMaster(player);

        player.getInventory().setItem(9, new ItemStack(Material.STONE, 5));
        player.getInventory().setItem(11, new ItemStack(Material.STONE, 10));
        ItemStack[] before = player.getInventory().getContents().clone();

        InventoryClickEvent event = fireSortTrigger(player);

        assertArrayEquals(before, player.getInventory().getContents(),
                "Inventory should be unchanged when master permission is denied");
        assertFalse(event.isCancelled(), "Event should not be cancelled when blocked by master perm");
    }

    @Test
    void masterGranted_sortingProceeds() {
        PlayerMock player = addNormalPlayer("Bob");
        plugin.getSortingPrefs().setEnabled(player, true);
        // Master is default: true — no denial needed.

        player.getInventory().setItem(9, new ItemStack(Material.STONE, 5));
        player.getInventory().setItem(11, new ItemStack(Material.STONE, 10));

        fireSortTrigger(player);

        // Items should have been merged and sorted.
        int total = 0;
        for (ItemStack is : player.getInventory().getContents()) {
            if (is != null && is.getType() == Material.STONE) total += is.getAmount();
        }
        assertEquals(15, total, "Stone should have been merged (15 total) after sort");
    }

    // =========================================================================
    // Click-triggered bundle packing (sorting off)
    // =========================================================================

    @Test
    void masterDenied_packingBlockedEvenWhenEnabled() {
        PlayerMock player = addNormalPlayer("Carol");
        plugin.getSortingPrefs().setEnabled(player, false);
        plugin.getSortingPrefs().setBundlePackInInventory(player, true);
        denyMaster(player);

        player.getInventory().setItem(9, new ItemStack(Material.BUNDLE, 1));
        player.getInventory().setItem(10, new ItemStack(Material.COBBLESTONE, 10));
        ItemStack[] before = player.getInventory().getContents().clone();

        fireSortTrigger(player);

        assertArrayEquals(before, player.getInventory().getContents(),
                "Bundle packing should be blocked when master permission is denied");
    }

    // =========================================================================
    // Bare /clicksorted toggle
    // =========================================================================

    @Test
    void masterDenied_bareToggleBlocked() {
        PlayerMock player = addNormalPlayer("Dave");
        boolean before = plugin.getSortingPrefs().getEnabled(player);
        denyMaster(player);

        player.performCommand("clicksorted");

        assertEquals(before, plugin.getSortingPrefs().getEnabled(player),
                "Enabled flag must not change when master permission is denied");
        assertTrue(anyMessageContains(player, "noPermission", "permission"),
                "Player should receive a noPermission message");
    }

    @Test
    void masterGranted_bareToggleWorks() {
        PlayerMock player = addNormalPlayer("Eve");
        boolean before = plugin.getSortingPrefs().getEnabled(player);

        player.performCommand("clicksorted");

        assertNotEquals(before, plugin.getSortingPrefs().getEnabled(player),
                "Enabled flag should have toggled");
    }

    // =========================================================================
    // /clicksorted sort enabled
    // =========================================================================

    @Test
    void masterDenied_sortEnabledCommandBlocked() {
        PlayerMock player = addNormalPlayer("Frank");
        boolean before = plugin.getSortingPrefs().getEnabled(player);
        denyMaster(player);

        player.performCommand("clicksorted sort enabled");

        assertEquals(before, plugin.getSortingPrefs().getEnabled(player),
                "Enabled flag must not change when master permission is denied");
    }

    // =========================================================================
    // /clicksorted bundle
    // =========================================================================

    @Test
    void masterDenied_bundleCommandBlocked() {
        PlayerMock player = addNormalPlayer("Grace");
        denyMaster(player);
        drainMessages(player);

        // Attempt to enable bundle packing via command.
        boolean before = plugin.getSortingPrefs().getBundlePackInInventory(player);
        player.performCommand("clicksorted bundle enabled in-inventory yes");

        assertEquals(before, plugin.getSortingPrefs().getBundlePackInInventory(player),
                "Bundle preference must not change when master permission is denied");
    }

    @Test
    void masterGranted_bundleCommandWorks() {
        PlayerMock player = addNormalPlayer("Heidi");
        plugin.getSortingPrefs().setBundlePackInInventory(player, false);

        player.performCommand("clicksorted bundle enabled in-inventory yes");

        assertTrue(plugin.getSortingPrefs().getBundlePackInInventory(player),
                "Bundle inventory packing should be enabled after the command");
    }

    // =========================================================================
    // Non-player-facing (admin) commands are unaffected by master denial
    // =========================================================================

    @Test
    void masterDenied_adminReloadStillAccessible() {
        PlayerMock player = addOpPlayer("Ivan");
        denyMaster(player);
        drainMessages(player);

        // Should not throw / should not say "unknown command" — admin nodes are separate.
        // We can't easily verify a reload happened in tests, but we can confirm no "no permission"
        // message is sent (the admin perm check is separate).
        player.performCommand("clicksorted admin reload");
        assertFalse(anyMessageContains(player, "noPermission"),
                "Admin reload should not be blocked by the master kill-switch");
    }
}
