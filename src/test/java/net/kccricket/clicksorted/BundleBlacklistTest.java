package net.kccricket.clicksorted;

import net.kccricket.clicksorted.gui.BlacklistGuiHolder;
import net.kccricket.clicksorted.model.PlayerSortingPrefs;
import net.kccricket.clicksorted.model.SortKey;
import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.sort.BundleBlacklist;
import net.kccricket.clicksorted.sort.BundlePacker;
import net.kccricket.clicksorted.sort.MaterialNameSet;
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
                                        int stackLimit, BundleBlacklist blacklist) {
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

    /** Build an ItemStack with a custom display name (Adventure component). */
    private static ItemStack customNamed(Material material, String name) {
        ItemStack item = new ItemStack(material, 1);
        var meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text(name));
        item.setItemMeta(meta);
        return item;
    }

    /** Build an ItemStack with an {@code item_name} (data-pack/plugin base name) but no custom name. */
    private static ItemStack itemNamed(Material material, String name) {
        ItemStack item = new ItemStack(material, 1);
        var meta = item.getItemMeta();
        meta.itemName(net.kyori.adventure.text.Component.text(name));
        item.setItemMeta(meta);
        return item;
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

    /** Total item amount across slots [from, to) that contain the given material. */
    private static int totalAmount(Inventory inv, int from, int to, Material mat) {
        int n = 0;
        for (int i = from; i < to; i++) {
            ItemStack is = inv.getItem(i);
            if (is != null && is.getType() == mat) n += is.getAmount();
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

        boolean added = prefs.addToBundleBlacklist(player, Material.DIRT).applied();
        assertTrue(added, "First add should return true");
        assertTrue(prefs.getBundleBlacklist(player).contains(Material.DIRT));
    }

    @Test
    void addDuplicateReturnsFalse() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        prefs.addToBundleBlacklist(player, Material.DIRT);
        boolean addedAgain = prefs.addToBundleBlacklist(player, Material.DIRT).applied();
        assertFalse(addedAgain, "Re-adding an existing entry should return false");
        assertEquals(1, prefs.getBundleBlacklist(player).size());
    }

    @Test
    void removeReturnsTrueWhenPresent() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        prefs.addToBundleBlacklist(player, Material.COBBLESTONE);
        boolean removed = prefs.removeFromBundleBlacklist(player, Material.COBBLESTONE).applied();
        assertTrue(removed);
        assertFalse(prefs.getBundleBlacklist(player).contains(Material.COBBLESTONE));
    }

    @Test
    void removeReturnsFalseWhenAbsent() {
        PlayerMock player = server.addPlayer("Alice");
        boolean removed = plugin.getSortingPrefs().removeFromBundleBlacklist(player, Material.DIRT).applied();
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
        pack(List.of(new ItemStack(Material.DIRT, 16)), bundles, 0,
                new BundleBlacklist(new MaterialNameSet(Set.of(Material.DIRT), Set.of())));

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
        assertFalse(BundlePacker.canBundle(dirt, new BundleBlacklist(new MaterialNameSet(Set.of(Material.DIRT), Set.of()))),
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
                new BundleBlacklist(new MaterialNameSet(Set.of(Material.DIRT), Set.of()))); // COBBLESTONE is NOT blacklisted

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
        pack(List.of(), bundles, 0, new BundleBlacklist(new MaterialNameSet(Set.of(Material.DIRT), Set.of())));

        assertTrue(bundleHas(b, Material.DIRT),
                "Blacklisted dirt already in the bundle must stay there (not unpacked)");
    }

    @Test
    void nonBlacklistedItemInsideBundleIsRepacked() {
        // Bundle pre-loaded with a full stack of cobblestone — should be pooled out and reappear
        // as a leftover (full stack has no bundleable remainder).
        ItemStack b = bundle(new ItemStack(Material.COBBLESTONE, 64));
        List<ItemStack> bundles = new ArrayList<>(List.of(b));

        List<ItemStack> leftover = pack(List.of(), bundles, 0, new BundleBlacklist(new MaterialNameSet(Set.of(Material.DIRT), Set.of())));

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

        List<ItemStack> leftover = pack(List.of(), bundles, 0, new BundleBlacklist(new MaterialNameSet(Set.of(Material.DIRT), Set.of())));

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
        plugin.getSortingPrefs().setBundlePackInInventory(player, true);
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
        assertEquals(35, totalAmount(player.getInventory(), 9, 36, Material.DIRT),
                "All dirt should be accounted for");

        // Cobblestone (not blacklisted) packs into the bundle.
        ItemStack bundle = findBundle(player.getInventory(), 9, 36);
        assertNotNull(bundle, "Bundle should still be present");
        assertFalse(bundleHas(bundle, Material.DIRT), "Blacklisted dirt must not be in the bundle");
        assertTrue(bundleHas(bundle, Material.COBBLESTONE), "Non-blacklisted cobblestone should pack");
    }

    // -------------------------------------------------------------------------
    // PlayerSortingPrefs — display-name PDC storage
    // -------------------------------------------------------------------------

    @Test
    void nameBlacklistEmptyByDefault() {
        PlayerMock player = server.addPlayer("Alice");
        assertTrue(plugin.getSortingPrefs().getBundleBlacklistNames(player).isEmpty(),
                "New player should have an empty name blacklist");
    }

    @Test
    void addNameThenGetRoundTrips() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        boolean added = prefs.addToBundleBlacklistName(player, "Magic Sword").applied();
        assertTrue(added, "First name add should return true");
        assertTrue(prefs.getBundleBlacklistNames(player).contains("Magic Sword"));
    }

    @Test
    void addDuplicateNameReturnsFalse() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        prefs.addToBundleBlacklistName(player, "Magic Sword");
        boolean addedAgain = prefs.addToBundleBlacklistName(player, "Magic Sword").applied();
        assertFalse(addedAgain, "Re-adding an existing name entry should return false");
        assertEquals(1, prefs.getBundleBlacklistNames(player).size());
    }

    @Test
    void removeNameReturnsTrueWhenPresent() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        prefs.addToBundleBlacklistName(player, "Magic Sword");
        boolean removed = prefs.removeFromBundleBlacklistName(player, "Magic Sword").applied();
        assertTrue(removed);
        assertFalse(prefs.getBundleBlacklistNames(player).contains("Magic Sword"));
    }

    @Test
    void clearAlsoClearsNameBlacklist() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        prefs.addToBundleBlacklist(player, Material.DIRT);
        prefs.addToBundleBlacklistName(player, "Magic Sword");
        prefs.clearBundleBlacklist(player);
        assertTrue(prefs.getBundleBlacklist(player).isEmpty(), "Material blacklist should be empty after clear");
        assertTrue(prefs.getBundleBlacklistNames(player).isEmpty(), "Name blacklist should be empty after clear");
    }

    // -------------------------------------------------------------------------
    // BundleBlacklist.blocks — name matching
    // -------------------------------------------------------------------------

    @Test
    void blocksReturnsTrueForCustomNamedItem() {
        ItemStack sword = customNamed(Material.DIAMOND_SWORD, "Magic Sword");
        BundleBlacklist blacklist = new BundleBlacklist(new MaterialNameSet(Set.of(), Set.of("magic sword")));
        assertTrue(blacklist.blocks(sword),
                "blocks() must return true for an item whose display name is in the name set");
    }

    @Test
    void blocksReturnsTrueForItemNamedItem() {
        // item_name (data-pack/plugin base name) is resolved like a name via ItemsConfig.getItemName
        // → ItemNames.explicitName, so a name entry blocks an item_name-only item too.
        ItemStack sword = itemNamed(Material.DIAMOND_SWORD, "Magic Sword");
        BundleBlacklist blacklist = new BundleBlacklist(new MaterialNameSet(Set.of(), Set.of("magic sword")));
        assertTrue(blacklist.blocks(sword),
                "blocks() must return true for an item whose item_name is in the name set");
    }

    @Test
    void blocksReturnsFalseForSameMaterialDifferentName() {
        ItemStack plain = new ItemStack(Material.DIAMOND_SWORD, 1); // no custom name
        BundleBlacklist blacklist = new BundleBlacklist(new MaterialNameSet(Set.of(), Set.of("magic sword")));
        assertFalse(blacklist.blocks(plain),
                "blocks() must return false for a same-material item without that display name");
    }

    @Test
    void blocksReturnsFalseForWrongName() {
        ItemStack sword = customNamed(Material.DIAMOND_SWORD, "Other Sword");
        BundleBlacklist blacklist = new BundleBlacklist(new MaterialNameSet(Set.of(), Set.of("magic sword")));
        assertFalse(blacklist.blocks(sword),
                "blocks() must return false when the item's name does not match");
    }

    @Test
    void canBundleReturnsFalseForBlacklistedName() {
        // Use a stackable item; DIAMOND_SWORD is non-stackable so canBundle(is) is already false.
        ItemStack namedDirt = customNamed(Material.DIRT, "Special Dirt");
        BundleBlacklist bl = new BundleBlacklist(new MaterialNameSet(Set.of(), Set.of("special dirt")));
        assertFalse(BundlePacker.canBundle(namedDirt, bl),
                "canBundle must return false for an item whose resolved name is blacklisted");
        // A plain dirt's resolved name ("Dirt" / "DIRT") does not match "Special Dirt".
        assertTrue(BundlePacker.canBundle(new ItemStack(Material.DIRT, 1), bl),
                "canBundle must return true for a same-material item whose name does not match");
    }

    // -------------------------------------------------------------------------
    // BundleBlacklist.blocks — case-insensitive name matching
    // -------------------------------------------------------------------------

    @Test
    void blocksIsCaseInsensitive_lowerBlacklistUpperItem() {
        // Blacklist stores lowercase; item has mixed-case display name → must still block.
        ItemStack sword = customNamed(Material.DIAMOND_SWORD, "Magic Sword");
        BundleBlacklist blacklist = new BundleBlacklist(new MaterialNameSet(Set.of(), Set.of("magic sword")));
        assertTrue(blacklist.blocks(sword),
                "blocks() must match case-insensitively (stored lowercase, item mixed-case)");
    }

    @Test
    void blocksIsCaseInsensitive_upperBlacklistLowerItem() {
        // Even if the blacklist was built with uppercase (e.g. from prefs stored as-is before
        // lowercasing at construction time), the lookup lowercases the resolved name.
        // Here we simulate a pre-lowered blacklist vs. an item whose name happens to be all caps.
        ItemStack sword = customNamed(Material.DIAMOND_SWORD, "MAGIC SWORD");
        BundleBlacklist blacklist = new BundleBlacklist(new MaterialNameSet(Set.of(), Set.of("magic sword")));
        assertTrue(blacklist.blocks(sword),
                "blocks() must match case-insensitively (stored lowercase, item uppercase)");
    }

    // -------------------------------------------------------------------------
    // Integration — sort service respects the player's name blacklist
    // -------------------------------------------------------------------------

    @Test
    void blacklistedItemInBundleRemainsAfterSort() {
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setBundlePackInInventory(player, true);
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

    @Test
    void namedItemBlacklistedByName_staysLoose_plainPacksNormally() {
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setBundlePackInInventory(player, true);
        plugin.getSortingPrefs().addToBundleBlacklistName(player, "Special Dirt");

        // One "Special Dirt" (named) and one plain dirt — same material.
        ItemStack namedDirt = customNamed(Material.DIRT, "Special Dirt");
        namedDirt.setAmount(16);
        ItemStack plainDirt = new ItemStack(Material.DIRT, 16);
        ItemStack cobble = new ItemStack(Material.COBBLESTONE, 8);

        player.getInventory().setItem(9, bundle());
        player.getInventory().setItem(10, namedDirt);
        player.getInventory().setItem(11, plainDirt);
        player.getInventory().setItem(12, cobble);

        sortMainStorage(player);

        ItemStack b = findBundle(player.getInventory(), 9, 36);
        assertNotNull(b, "Bundle should be present after sort");

        // Named dirt must not be in the bundle.
        boolean namedInBundle = false;
        for (ItemStack is : ((org.bukkit.inventory.meta.BundleMeta) b.getItemMeta()).getItems()) {
            if (is != null && is.getType() == Material.DIRT && is.hasItemMeta()
                    && is.getItemMeta().hasDisplayName()) {
                namedInBundle = true;
            }
        }
        assertFalse(namedInBundle, "Named 'Special Dirt' must not be packed into the bundle");

        // Cobblestone (not blacklisted) should be in the bundle.
        assertTrue(bundleHas(b, Material.COBBLESTONE), "Non-blacklisted cobblestone should pack into bundle");
    }

    @Test
    void namedItemInsideBundle_blacklistedByName_retainedAfterSort() {
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setBundlePackInInventory(player, true);
        plugin.getSortingPrefs().addToBundleBlacklistName(player, "Special Dirt");

        // Pre-load a bundle with a named dirt item.
        ItemStack namedDirt = customNamed(Material.DIRT, "Special Dirt");
        namedDirt.setAmount(10);
        ItemStack b = bundle(namedDirt);
        player.getInventory().setItem(9, b);
        player.getInventory().setItem(10, new ItemStack(Material.COBBLESTONE, 5));

        sortMainStorage(player);

        ItemStack bundleAfter = findBundle(player.getInventory(), 9, 36);
        assertNotNull(bundleAfter, "Bundle should remain after sort");

        // Named dirt must still be inside the bundle (not unpacked).
        boolean namedRetained = false;
        for (ItemStack is : ((org.bukkit.inventory.meta.BundleMeta) bundleAfter.getItemMeta()).getItems()) {
            if (is != null && is.getType() == Material.DIRT && is.hasItemMeta()
                    && is.getItemMeta().hasDisplayName()) {
                namedRetained = true;
            }
        }
        assertTrue(namedRetained, "Named 'Special Dirt' inside the bundle must stay there after sorting");
    }

    // -------------------------------------------------------------------------
    // BundlePacker — blacklisted bundle is skipped as a bin (not packed into, not unpacked)
    // -------------------------------------------------------------------------

    @Test
    void blacklistedBundle_notPackedInto() {
        // The bundle's own material (BUNDLE) is blacklisted — it must not be used as a bin.
        ItemStack b = bundle();
        List<ItemStack> bundles = new ArrayList<>(List.of(b));

        List<ItemStack> leftover = pack(
                List.of(new ItemStack(Material.COBBLESTONE, 8)),
                bundles,
                0,
                new BundleBlacklist(new MaterialNameSet(Set.of(Material.BUNDLE), Set.of())));

        assertFalse(bundleHas(b, Material.COBBLESTONE),
                "A bundle blacklisted by material must not receive packed items");
        boolean cobbLoose = leftover.stream().anyMatch(
                is -> is != null && is.getType() == Material.COBBLESTONE);
        assertTrue(cobbLoose, "Cobblestone must stay loose when its target bundle is blacklisted");
    }

    @Test
    void blacklistedBundle_contentsNotUnpacked() {
        // The bundle's own material (BUNDLE) is blacklisted — its contents must not be pooled out.
        ItemStack b = bundle(new ItemStack(Material.COBBLESTONE, 64));
        List<ItemStack> bundles = new ArrayList<>(List.of(b));

        List<ItemStack> leftover = pack(
                List.of(),
                bundles,
                0,
                new BundleBlacklist(new MaterialNameSet(Set.of(Material.BUNDLE), Set.of())));

        assertTrue(bundleHas(b, Material.COBBLESTONE),
                "Contents of a blacklisted bundle must not be unpacked");
        boolean cobbLoose = leftover.stream().anyMatch(
                is -> is != null && is.getType() == Material.COBBLESTONE);
        assertFalse(cobbLoose, "No cobblestone should appear loose if the bundle was not unpacked");
    }

    @Test
    void namedBundle_blacklistedByName_skippedAsBin() {
        // A named bundle ("Keepsake") is blacklisted by name — it must not be packed into or unpacked.
        ItemStack b = customNamed(Material.BUNDLE, "Keepsake");
        // Pre-load it with cobblestone.
        BundleMeta meta = (BundleMeta) b.getItemMeta();
        meta.setItems(List.of(new ItemStack(Material.COBBLESTONE, 32)));
        b.setItemMeta(meta);
        List<ItemStack> bundles = new ArrayList<>(List.of(b));

        List<ItemStack> leftover = pack(
                List.of(new ItemStack(Material.DIRT, 8)),
                bundles,
                0,
                new BundleBlacklist(new MaterialNameSet(Set.of(), Set.of("keepsake"))));

        // Cobblestone must still be inside (not unpacked).
        assertTrue(bundleHas(b, Material.COBBLESTONE),
                "Contents of a name-blacklisted bundle must not be unpacked");
        // Dirt must not be packed in.
        assertFalse(bundleHas(b, Material.DIRT),
                "Dirt must not be packed into a name-blacklisted bundle");
        // Dirt comes back loose.
        boolean dirtLoose = leftover.stream().anyMatch(
                is -> is != null && is.getType() == Material.DIRT);
        assertTrue(dirtLoose, "Dirt should remain loose when the only bin is name-blacklisted");
    }

    @Test
    void nonBlacklistedBundle_stillPacksNormally_whenBlacklistTargetsDifferentMaterial() {
        // A plain BUNDLE is NOT the blacklisted material; it must still pack normally.
        ItemStack b = bundle();
        List<ItemStack> bundles = new ArrayList<>(List.of(b));

        List<ItemStack> leftover = pack(
                List.of(new ItemStack(Material.COBBLESTONE, 8)),
                bundles,
                0,
                new BundleBlacklist(new MaterialNameSet(Set.of(Material.DIRT), Set.of()))); // BUNDLE is not blacklisted

        assertTrue(bundleHas(b, Material.COBBLESTONE),
                "A non-blacklisted bundle must still accept packed items");
        boolean cobbLoose = leftover.stream().anyMatch(
                is -> is != null && is.getType() == Material.COBBLESTONE);
        assertFalse(cobbLoose, "Cobblestone must not appear loose when packed into the bundle");
    }

    // -------------------------------------------------------------------------
    // BlacklistGuiListener — GUI add-path classifies item_name items as name entries
    // -------------------------------------------------------------------------

    @Test
    void guiAddPath_itemNamedItem_addedAsNameEntry() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();

        // Open the blacklist GUI so the listener recognises the holder, then click an item_name-only
        // feather in the player's real inventory region (raw slot >= GUI_SIZE).
        InventoryView view = player.openInventory(new BlacklistGuiHolder(plugin, player).getInventory());
        ItemStack featherItemNamed = itemNamed(Material.FEATHER, "Fancy Feather");
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, BlacklistGuiHolder.GUI_SIZE,
                ClickType.LEFT, InventoryAction.PICKUP_ALL) {
            @Override
            public ItemStack getCurrentItem() {
                return featherItemNamed;
            }
        };
        server.getPluginManager().callEvent(event);

        assertTrue(prefs.getBundleBlacklistNames(player).contains("Fancy Feather"),
                "item_name item must be added to the name blacklist by its item_name text");
        assertFalse(prefs.getBundleBlacklist(player).contains(Material.FEATHER),
                "item_name item must NOT be added as a material entry");
    }

    // -------------------------------------------------------------------------
    // Integration — blacklisted bundle is sorted normally but skipped as a bin
    // -------------------------------------------------------------------------

    @Test
    void integration_blacklistedBundle_sortedNormallyButNotUsedAsBin() {
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setBundlePackInInventory(player, true);
        plugin.getSortingPrefs().addToBundleBlacklist(player, Material.BUNDLE);

        // A pre-loaded bundle + loose cobblestone; bundle is blacklisted by material.
        ItemStack b = bundle(new ItemStack(Material.COBBLESTONE, 16));
        player.getInventory().setItem(9, b);
        player.getInventory().setItem(10, new ItemStack(Material.COBBLESTONE, 8));

        sortMainStorage(player);

        // Bundle must still be present in main storage (sorted, not destroyed).
        ItemStack bundleAfter = findBundle(player.getInventory(), 9, 36);
        assertNotNull(bundleAfter, "Blacklisted bundle must still be present after sort");

        // Bundle contents must be untouched (the 16 cobblestone inside is not pooled/repacked).
        assertTrue(bundleHas(bundleAfter, Material.COBBLESTONE),
                "Contents of blacklisted bundle must remain inside it after sort");

        // The loose 8 cobblestone must remain loose (not packed into the blacklisted bundle).
        assertEquals(8, totalAmount(player.getInventory(), 9, 36, Material.COBBLESTONE),
                "Loose cobblestone must not be absorbed into the blacklisted bundle");
    }
}
