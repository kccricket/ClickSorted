package net.kccricket.clicksorted;

import net.kccricket.clicksorted.model.PlayerSortingPrefs;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@code /clicksorted bundle} and {@code /clicksorted bundlecap}.
 */
class BundleCommandTest extends AbstractClickSortedTest {

    // -------------------------------------------------------------------------
    // /clicksorted bundlecap — toggle
    // -------------------------------------------------------------------------

    @Test
    void bundlecapCommandTogglesBundleCapEnabled() {
        PlayerMock player = addOpPlayer("Alice");
        boolean initial = plugin.getSortingPrefs().getBundleCapEnabled(player);

        server.dispatchCommand(player, "clicksorted bundlecap");
        assertEquals(!initial, plugin.getSortingPrefs().getBundleCapEnabled(player),
                "bundlecap command should toggle the flag");

        server.dispatchCommand(player, "clicksorted bundlecap");
        assertEquals(initial, plugin.getSortingPrefs().getBundleCapEnabled(player),
                "Second toggle should restore the original value");
    }

    @Test
    void bundlecapCommandSendsStatusMessage() {
        PlayerMock player = addOpPlayer("Alice");
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted bundlecap");

        assertTrue(anyMessageContains(player, "ENABLED", "DISABLED", "cap", "Bundle"),
                "Expected bundle-cap status message");
    }

    @Test
    void bundlecapCommandIsPlayerOnly() {
        assertDoesNotThrow(() ->
                server.dispatchCommand(server.getConsoleSender(), "clicksorted bundlecap"));
    }

    // -------------------------------------------------------------------------
    // PlayerSortingPrefs — getBundleCapEnabled / setBundleCapEnabled PDC round-trip
    // -------------------------------------------------------------------------

    @Test
    void defaultBundleCapMatchesConfig() {
        PlayerMock player = server.addPlayer("Alice");
        assertEquals(plugin.getConfigManager().main().getDefaultBundleCap(),
                plugin.getSortingPrefs().getBundleCapEnabled(player),
                "New player should get the default bundle-cap setting from config");
    }

    @Test
    void setBundleCapEnabledPersistsInPDC() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        boolean initial = prefs.getBundleCapEnabled(player);

        prefs.setBundleCapEnabled(player, !initial);

        assertEquals(!initial, prefs.getBundleCapEnabled(player));
        NamespacedKey key = new NamespacedKey(plugin, "bundle_cap");
        byte expected = (!initial) ? (byte) 1 : (byte) 0;
        assertEquals(expected,
                player.getPersistentDataContainer().get(key, PersistentDataType.BYTE));
    }

    // -------------------------------------------------------------------------
    // /clicksorted bundle — sort + pack
    // -------------------------------------------------------------------------

    @Test
    void bundleCommandPacksPartialsIntoEmptyBundle() {
        PlayerMock player = addOpPlayer("Alice");

        // Populate main inventory (slots 9–35): an empty bundle + 2 partial stacks
        player.getInventory().setItem(9, new ItemStack(Material.BUNDLE, 1));
        player.getInventory().setItem(10, new ItemStack(Material.COBBLESTONE, 10));
        player.getInventory().setItem(11, new ItemStack(Material.DIRT, 5));

        server.dispatchCommand(player, "clicksorted bundle");

        // After packing, only the bundle should remain in those slots (partials absorbed)
        int bundleCount = 0;
        int cobblesSlots = 0;
        int dirtSlots = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            if (item.getType() == Material.BUNDLE) bundleCount++;
            if (item.getType() == Material.COBBLESTONE) cobblesSlots++;
            if (item.getType() == Material.DIRT) dirtSlots++;
        }

        assertEquals(1, bundleCount, "Bundle should still be present");
        assertEquals(0, cobblesSlots, "Cobblestone partial should have been absorbed");
        assertEquals(0, dirtSlots, "Dirt partial should have been absorbed");

        // Verify bundle contents
        ItemStack bundle = null;
        for (int i = 9; i < 36; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == Material.BUNDLE) {
                bundle = item;
                break;
            }
        }
        assertNotNull(bundle);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        assertNotNull(meta);
        assertFalse(meta.getItems().isEmpty(), "Bundle should contain the packed items");
    }

    @Test
    void bundleCommandSendsFeedbackMessage() {
        PlayerMock player = addOpPlayer("Alice");
        player.getInventory().setItem(9, new ItemStack(Material.BUNDLE, 1));
        player.getInventory().setItem(10, new ItemStack(Material.COBBLESTONE, 5));
        drainMessages(player);

        server.dispatchCommand(player, "clicksorted bundle");

        assertTrue(anyMessageContains(player, "Sorted", "packed", "bundle"),
                "Expected bundle-pack feedback message");
    }

    @Test
    void bundleCommandWithNoBundles_stillSortsAndSendsMessage() {
        PlayerMock player = addOpPlayer("Alice");
        // No bundle in inventory — the sort should still run, and 0 partials packed
        player.getInventory().setItem(9, new ItemStack(Material.COBBLESTONE, 5));
        player.getInventory().setItem(10, new ItemStack(Material.DIRT, 3));
        drainMessages(player);

        assertDoesNotThrow(() -> server.dispatchCommand(player, "clicksorted bundle"));
        assertTrue(anyMessageContains(player, "0", "Sorted", "packed"),
                "Expected a message indicating 0 stacks were packed");
    }

    @Test
    void bundleCommandIsPlayerOnly() {
        assertDoesNotThrow(() ->
                server.dispatchCommand(server.getConsoleSender(), "clicksorted bundle"));
    }
}
