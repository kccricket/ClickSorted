package net.kccricket.clicksorted;

import net.kccricket.clicksorted.model.PlayerSortingPrefs;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

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
    void bundleCommandCombinesSameItemStacksInsteadOfBundling() {
        PlayerMock player = addOpPlayer("Alice");

        // 42 + 2 of the same item, with a bundle present. They should merge into one 44-stack
        // in the inventory, and NOT be spent into the bundle.
        player.getInventory().setItem(9, new ItemStack(Material.BUNDLE, 1));
        player.getInventory().setItem(10, new ItemStack(Material.COBBLESTONE, 42));
        player.getInventory().setItem(11, new ItemStack(Material.COBBLESTONE, 2));

        server.dispatchCommand(player, "clicksorted bundle");

        int cobbleSlots = 0;
        int cobbleTotal = 0;
        ItemStack bundle = null;
        for (int i = 9; i < 36; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            if (item.getType() == Material.BUNDLE) bundle = item;
            if (item.getType() == Material.COBBLESTONE) {
                cobbleSlots++;
                cobbleTotal += item.getAmount();
            }
        }

        assertEquals(1, cobbleSlots, "The two partials should combine into a single stack");
        assertEquals(44, cobbleTotal, "Combined stack should hold all 44 items");

        assertNotNull(bundle);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        assertNotNull(meta);
        assertTrue(meta.getItems().isEmpty(), "Bundle capacity should not be spent on mergeable partials");
    }

    @Test
    void bundleCommandCombineThenPackFreesMoreSlots() {
        PlayerMock player = addOpPlayer("Alice");

        // Five 32-count stacks of one 64-stackable item + an empty bundle.
        // Combine → 64 + 64 + 32 (3 stacks); pack the 32-remainder into the bundle
        // → two full stacks + a bundle = 3 occupied slots (down from 6).
        player.getInventory().setItem(9, new ItemStack(Material.BUNDLE, 1));
        for (int i = 0; i < 5; i++) {
            player.getInventory().setItem(10 + i, new ItemStack(Material.COBBLESTONE, 32));
        }

        server.dispatchCommand(player, "clicksorted bundle");

        int occupied = 0;
        int cobbleSlots = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            occupied++;
            if (item.getType() == Material.COBBLESTONE) {
                cobbleSlots++;
                assertEquals(64, item.getAmount(), "Loose cobblestone stacks should be full");
            }
        }

        assertEquals(3, occupied, "Bundle + two full stacks = 3 occupied slots (was 6)");
        assertEquals(2, cobbleSlots, "Two full cobblestone stacks should remain loose");
    }

    @Test
    void bundleCommandPreservesPositions() {
        PlayerMock player = addOpPlayer("Alice");

        // Layout: a large stack, a small same-item stack, an unrelated item, and a bundle, spread
        // out with gaps. After /bundle: the large stack and unrelated item and bundle keep their
        // exact slots; only the small stack's slot empties (merged into the large one).
        player.getInventory().setItem(12, new ItemStack(Material.COBBLESTONE, 50)); // large
        player.getInventory().setItem(20, new ItemStack(Material.DIAMOND_SWORD, 1)); // unrelated, non-bundleable
        player.getInventory().setItem(25, new ItemStack(Material.COBBLESTONE, 6));  // small
        player.getInventory().setItem(30, new ItemStack(Material.BUNDLE, 1));       // bundle

        server.dispatchCommand(player, "clicksorted bundle");

        ItemStack large = player.getInventory().getItem(12);
        assertNotNull(large, "Large stack must stay in slot 12");
        assertEquals(Material.COBBLESTONE, large.getType());
        assertEquals(56, large.getAmount(), "Small stack should merge into the large one in place");

        ItemStack unrelated = player.getInventory().getItem(20);
        assertNotNull(unrelated, "Unrelated item must stay in slot 20");
        assertEquals(Material.DIAMOND_SWORD, unrelated.getType());

        assertNull(player.getInventory().getItem(25), "Merged-away small stack's slot should be empty");

        ItemStack bundle = player.getInventory().getItem(30);
        assertNotNull(bundle, "Bundle must stay in slot 30");
        assertEquals(Material.BUNDLE, bundle.getType());
    }

    @Test
    void bundleCommandHotbarBundleAbsorbsMainItemButHotbarItemStaysPut() {
        PlayerMock player = addOpPlayer("Alice");

        // Hotbar: a bundle (slot 0, a valid bin) and a loose item (slot 1, must never move).
        player.getInventory().setItem(0, new ItemStack(Material.BUNDLE, 1));
        player.getInventory().setItem(1, new ItemStack(Material.DIAMOND, 16));
        // Main storage: a small partial that should be absorbed by the hotbar bundle.
        player.getInventory().setItem(9, new ItemStack(Material.COBBLESTONE, 10));

        server.dispatchCommand(player, "clicksorted bundle");

        // Hotbar item is untouched.
        ItemStack hotbarItem = player.getInventory().getItem(1);
        assertNotNull(hotbarItem, "Hotbar item must stay put");
        assertEquals(Material.DIAMOND, hotbarItem.getType());
        assertEquals(16, hotbarItem.getAmount(), "Hotbar item amount unchanged");

        // Main cobblestone is gone (absorbed into the hotbar bundle).
        assertNull(player.getInventory().getItem(9), "Main partial should have been absorbed");

        ItemStack hotbarBundle = player.getInventory().getItem(0);
        assertNotNull(hotbarBundle);
        assertEquals(Material.BUNDLE, hotbarBundle.getType());
        BundleMeta meta = (BundleMeta) hotbarBundle.getItemMeta();
        assertNotNull(meta);
        assertFalse(meta.getItems().isEmpty(), "Hotbar bundle should hold the absorbed cobblestone");
    }

    @Test
    void bundleCommandLeavesLockedItemAndLockedBundleUntouched() {
        PlayerMock player = addOpPlayer("Alice");

        // Locked main slot holding a partial item, and a locked main slot holding a bundle. Neither
        // may be pooled, used as a bin, or written to.
        player.getInventory().setItem(20, new ItemStack(Material.COBBLESTONE, 12));
        ItemStack lockedBundle = new ItemStack(Material.BUNDLE, 1);
        player.getInventory().setItem(21, lockedBundle);
        plugin.getSortingPrefs().setLockedSlots(player, java.util.Set.of(20, 21));

        // An unlocked partial elsewhere — with no unlocked bundle, it simply stays loose.
        player.getInventory().setItem(25, new ItemStack(Material.DIRT, 8));

        server.dispatchCommand(player, "clicksorted bundle");

        ItemStack locked = player.getInventory().getItem(20);
        assertNotNull(locked, "Locked item must stay");
        assertEquals(Material.COBBLESTONE, locked.getType());
        assertEquals(12, locked.getAmount(), "Locked item amount unchanged");

        ItemStack bundle = player.getInventory().getItem(21);
        assertNotNull(bundle, "Locked bundle must stay");
        assertEquals(Material.BUNDLE, bundle.getType());
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        assertNotNull(meta);
        assertTrue(meta.getItems().isEmpty(), "Locked bundle must never be used as a bin");
    }

    @Test
    void bundleCommandIsPlayerOnly() {
        assertDoesNotThrow(() ->
                server.dispatchCommand(server.getConsoleSender(), "clicksorted bundle"));
    }
}
