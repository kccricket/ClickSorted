package net.kccricket.clicksorted;

import net.kccricket.clicksorted.model.PlayerSortingPrefs;
import net.kccricket.clicksorted.model.SortKey;
import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.sort.BundlePacker;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the per-player bundle blacklist feature.
 *
 * <p>Covers three concerns:
 * <ol>
 *   <li>Per-player PDC storage (add / remove / clear / round-trip / unknown-token tolerance).</li>
 *   <li>{@link BundlePacker} respects a blacklist: blacklisted loose items are not packed, and
 *       blacklisted items already inside a bundle are left in place (not unpacked).</li>
 *   <li>Integration: a full sort via {@link net.kccricket.clicksorted.sort.InventorySortService}
 *       respects the player's blacklist — loose stacks are still merged normally, just never bundled.</li>
 * </ol>
 */
class BundleBlacklistTest extends AbstractClickSortedTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Build a bundle ItemStack pre-loaded with the given contents. */
    private static ItemStack bundle(ItemStack... contents) {
        ItemStack b = new ItemStack(Material.BUNDLE, 1);
        if (contents.length > 0) {
            BundleMeta meta = (BundleMeta) b.getItemMeta();
            meta.setItems(Arrays.asList(contents));
            b.setItemMeta(meta);
        }
        return b;
    }

    /**
     * Pool the given loose items and pack them into {@code bundles} (mutated in place) using the given
     * blacklist, returning the leftover loose stacks. Mirrors InventorySortService.packAndSort.
     */
    private static List<ItemStack> pack(List<ItemStack> loose, List<ItemStack> bundles,
                                        int stackLimit, Set<Material> blacklist) {
        Map<SortKey, Long> pool = new LinkedHashMap<>();
        Map<SortKey, ItemStack> samples = new LinkedHashMap<>();
        for (ItemStack is : loose) {
            if (is == null) continue;
            if (!BundlePacker.canBundle(is, blacklist)) continue; // mirrors InventorySortService loop
            SortKey key = new SortKey(is, SortingMethod.NAME);
            pool.merge(key, (long) is.getAmount(), (a, b) -> Long.sum(a, b));
            samples.putIfAbsent(key, is);
        }
        return BundlePacker.packIntoBundles(pool, samples, bundles, stackLimit, blacklist);
    }

    /** True if the bundle contains at least one stack of the given material. */
    private static boolean bundleHas(ItemStack bundleItem, Material mat) {
        BundleMeta meta = (BundleMeta) bundleItem.getItemMeta();
        for (ItemStack is : meta.getItems()) {
            if (is != null && is.getType() == mat) return true;
        }
        return false;
    }

    /** Total loose slots in [from, to) that contain the given material. */
    private static int looseSlots(Inventory inv, int from, int to, Material mat) {
        int n = 0;
        for (int i = from; i < to; i++) {
            ItemStack is = inv.getItem(i);
            if (is != null && is.getType() == mat) n++;
        }
        return n;
    }

    /** Find first bundle in [from, to) or null. */
    private static ItemStack findBundle(Inventory inv, int from, int to) {
        for (int i = from; i < to; i++) {
            ItemStack is = inv.getItem(i);
            if (is != null && is.getType() == Material.BUNDLE) return is;
        }
        return null;
    }

    /** Fire a SWAP_OFFHAND click on the player's main storage (rawSlot 27). */
    private void sortMainStorage(PlayerMock player) {
        plugin.getSortingPrefs().setSortOverItems(player, true);
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        InventoryView view = player.openInventory(chest);
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 27, ClickType.SWAP_OFFHAND, InventoryAction.UNKNOWN) {
            @Override
            public ItemStack getCurrentItem() {
                return new ItemStack(Material.STONE, 1);
            }
        };
        server.getPluginManager().callEvent(event);
    }

    // -------------------------------------------------------------------------
    // PlayerSortingPrefs — PDC storage
    // -------------------------------------------------------------------------

    @Test
    void blacklistEmptyByDefault() {
        PlayerMock player = server.addPlayer("Alice");
        assertTrue(plugin.getSortingPrefs().getBundleBlacklist(player).isEmpty(),
                "New player should have an empty blacklist");
    }

    @Test
    void addThenGetRoundTrips() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        boolean added = prefs.addToBundleBlacklist(player, Material.DIRT);
        assertTrue(added, "First add should return true");
        assertTrue(prefs.getBundleBlacklist(player).contains(Material.DIRT));
    }

    @Test
    void addDuplicateReturnsFalse() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        prefs.addToBundleBlacklist(player, Material.DIRT);
        boolean addedAgain = prefs.addToBundleBlacklist(player, Material.DIRT);
        assertFalse(addedAgain, "Re-adding an existing entry should return false");
        assertEquals(1, prefs.getBundleBlacklist(player).size());
    }

    @Test
    void removeReturnsTrueWhenPresent() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        prefs.addToBundleBlacklist(player, Material.COBBLESTONE);
        boolean removed = prefs.removeFromBundleBlacklist(player, Material.COBBLESTONE);
        assertTrue(removed);
        assertFalse(prefs.getBundleBlacklist(player).contains(Material.COBBLESTONE));
    }

    @Test
    void removeReturnsFalseWhenAbsent() {
        PlayerMock player = server.addPlayer("Alice");
        boolean removed = plugin.getSortingPrefs().removeFromBundleBlacklist(player, Material.DIRT);
        assertFalse(removed);
    }

    @Test
    void clearEmptiesBlacklist() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        prefs.addToBundleBlacklist(player, Material.DIRT);
        prefs.addToBundleBlacklist(player, Material.SAND);
        prefs.clearBundleBlacklist(player);
        assertTrue(prefs.getBundleBlacklist(player).isEmpty(), "Blacklist should be empty after clear");
    }

    @Test
    void multipleEntriesRoundTrip() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        prefs.addToBundleBlacklist(player, Material.DIRT);
        prefs.addToBundleBlacklist(player, Material.SAND);
        prefs.addToBundleBlacklist(player, Material.GRAVEL);

        Set<Material> stored = prefs.getBundleBlacklist(player);
        assertTrue(stored.contains(Material.DIRT));
        assertTrue(stored.contains(Material.SAND));
        assertTrue(stored.contains(Material.GRAVEL));
        assertEquals(3, stored.size());
    }

    // -------------------------------------------------------------------------
    // BundlePacker — blacklisted loose items are not packed
    // -------------------------------------------------------------------------

    @Test
    void blacklistedLooseItemStaysLoose() {
        ItemStack b = bundle();
        List<ItemStack> bundles = new ArrayList<>(List.of(b));

        // The pack() helper filters via canBundle(is, blacklist) before pooling, so blacklisted
        // items never enter packIntoBundles at all — they don't appear in the leftover list either.
        // What we verify here is that the bundle never receives blacklisted dirt.
        pack(List.of(new ItemStack(Material.DIRT, 16)), bundles, 0, Set.of(Material.DIRT));

        assertFalse(bundleHas(b, Material.DIRT),
                "Blacklisted dirt must not be packed into the bundle");
    }

    @Test
    void canBundleBlacklistAwareReturnsFalseForBlacklisted() {
        ItemStack dirt = new ItemStack(Material.DIRT, 16);
        // Without blacklist: dirt is normally bundleable.
        assertTrue(BundlePacker.canBundle(dirt),
                "Dirt should be bundleable without a blacklist");
        // With blacklist containing DIRT: must return false.
        assertFalse(BundlePacker.canBundle(dirt, Set.of(Material.DIRT)),
                "canBundle with blacklist must return false for blacklisted material");
    }

    @Test
    void nonBlacklistedItemStillPacks() {
        ItemStack b = bundle();
        List<ItemStack> bundles = new ArrayList<>(List.of(b));

        List<ItemStack> leftover = pack(
                List.of(new ItemStack(Material.COBBLESTONE, 16)),
                bundles,
                0,
                Set.of(Material.DIRT)); // COBBLESTONE is NOT blacklisted

        assertFalse(leftover.stream().anyMatch(is -> is != null && is.getType() == Material.COBBLESTONE),
                "Non-blacklisted cobblestone should be packed away");
        assertTrue(bundleHas(b, Material.COBBLESTONE),
                "Non-blacklisted cobblestone should be in the bundle");
    }

    // -------------------------------------------------------------------------
    // BundlePacker — blacklisted items inside bundles are retained (not unpacked)
    // -------------------------------------------------------------------------

    @Test
    void blacklistedItemInsideBundleIsRetained() {
        // Bundle pre-loaded with blacklisted dirt.
        ItemStack b = bundle(new ItemStack(Material.DIRT, 16));
        List<ItemStack> bundles = new ArrayList<>(List.of(b));

        // Pack with no loose items; only the existing bundle's contents matter.
        pack(List.of(), bundles, 0, Set.of(Material.DIRT));

        assertTrue(bundleHas(b, Material.DIRT),
                "Blacklisted dirt already in the bundle must stay there (not unpacked)");
    }

    @Test
    void nonBlacklistedItemInsideBundleIsRepacked() {
        // Bundle pre-loaded with a full stack of cobblestone — should be pooled out and reappear
        // as a leftover (full stack has no bundleable remainder).
        ItemStack b = bundle(new ItemStack(Material.COBBLESTONE, 64));
        List<ItemStack> bundles = new ArrayList<>(List.of(b));

        List<ItemStack> leftover = pack(List.of(), bundles, 0, Set.of(Material.DIRT));

        boolean fullStackLoose = leftover.stream().anyMatch(
                is -> is != null && is.getType() == Material.COBBLESTONE && is.getAmount() == 64);
        assertTrue(fullStackLoose, "Non-blacklisted bundle contents should be pooled and returned loose if a full stack");
    }

    @Test
    void mixedBundle_blacklistedRetained_otherRepacked() {
        // Bundle has both blacklisted DIRT and non-blacklisted COBBLESTONE.
        ItemStack b = bundle(
                new ItemStack(Material.DIRT, 16),
                new ItemStack(Material.COBBLESTONE, 64));
        List<ItemStack> bundles = new ArrayList<>(List.of(b));

        List<ItemStack> leftover = pack(List.of(), bundles, 0, Set.of(Material.DIRT));

        // Dirt stays in bundle; cobblestone (full stack) is pooled out and returned loose.
        assertTrue(bundleHas(b, Material.DIRT),
                "Blacklisted dirt must remain in the bundle");
        assertFalse(bundleHas(b, Material.COBBLESTONE),
                "Non-blacklisted cobblestone full stack must be unpacked from the bundle");
        boolean cobbLoose = leftover.stream().anyMatch(
                is -> is != null && is.getType() == Material.COBBLESTONE);
        assertTrue(cobbLoose, "Cobblestone should emerge as a loose leftover");
    }

    // -------------------------------------------------------------------------
    // Integration — sort service respects the player's blacklist
    // -------------------------------------------------------------------------

    @Test
    void blacklistedLooseStacksAreMergedButNotBundled() {
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setBundlePackInventory(player, true);
        plugin.getSortingPrefs().addToBundleBlacklist(player, Material.DIRT);

        // A bundle + two partial dirt stacks (should merge to one stack) + one cobblestone partial.
        player.getInventory().setItem(9, new ItemStack(Material.BUNDLE, 1));
        player.getInventory().setItem(10, new ItemStack(Material.DIRT, 20));
        player.getInventory().setItem(11, new ItemStack(Material.DIRT, 15));
        player.getInventory().setItem(12, new ItemStack(Material.COBBLESTONE, 8));

        sortMainStorage(player);

        // Dirt must be loose and merged into a single stack (20+15=35).
        int dirtLoose = looseSlots(player.getInventory(), 9, 36, Material.DIRT);
        assertEquals(1, dirtLoose, "Two dirt partials should merge into one loose stack");
        int totalDirt = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack is = player.getInventory().getItem(i);
            if (is != null && is.getType() == Material.DIRT) totalDirt += is.getAmount();
        }
        assertEquals(35, totalDirt, "All dirt should be accounted for");

        // Cobblestone (not blacklisted) packs into the bundle.
        ItemStack bundle = findBundle(player.getInventory(), 9, 36);
        assertNotNull(bundle, "Bundle should still be present");
        assertFalse(bundleHas(bundle, Material.DIRT), "Blacklisted dirt must not be in the bundle");
        assertTrue(bundleHas(bundle, Material.COBBLESTONE), "Non-blacklisted cobblestone should pack");
    }

    @Test
    void blacklistedItemInBundleRemainsAfterSort() {
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setBundlePackInventory(player, true);
        plugin.getSortingPrefs().addToBundleBlacklist(player, Material.DIRT);

        // Start with dirt already inside the bundle.
        ItemStack preloaded = bundle(new ItemStack(Material.DIRT, 10));
        player.getInventory().setItem(9, preloaded);
        // A loose cobblestone to make sorting do something.
        player.getInventory().setItem(10, new ItemStack(Material.COBBLESTONE, 5));

        sortMainStorage(player);

        ItemStack bundleAfter = findBundle(player.getInventory(), 9, 36);
        assertNotNull(bundleAfter, "Bundle should remain");
        assertTrue(bundleHas(bundleAfter, Material.DIRT),
                "Blacklisted dirt inside the bundle must stay there after sorting");
    }
}
