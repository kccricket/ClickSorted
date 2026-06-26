package net.kccricket.clicksorted;

import net.kccricket.clicksorted.sort.ProtectedItems;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the admin-enforced "do not touch" blacklist ({@link ProtectedItems}).
 *
 * <p>Covers:
 * <ul>
 *   <li>{@link ProtectedItems#nameToken(String)} normalization edge cases (unit-level).</li>
 *   <li>Config material: the protected slot survives a sort untouched while other items sort.</li>
 *   <li>Config name: matched case-insensitively; non-matching name is not protected.</li>
 *   <li>Unknown config material is skipped on load without crashing.</li>
 *   <li>Permission material node: granting the node protects the slot.</li>
 *   <li>Permission name slug: named item is matched via its slug node.</li>
 *   <li>Protected item is not packed when bundle packing is enabled.</li>
 *   <li>Container sorts are also protected (not just player inventories).</li>
 * </ul>
 *
 * <p><b>Important:</b> OP players in Bukkit have <em>all</em> permissions, including undeclared
 * dynamic nodes. Tests that verify sort behavior use non-OP players (who have
 * {@code clicksorted.sort.*} via its {@code default: true} declaration but lack undeclared dynamic
 * nodes) so that the permission-based blacklist channel can be tested in isolation.
 */
class ProtectedItemsTest extends AbstractClickSortedTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Build an ItemStack with a custom Adventure display name. */
    private static ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material, 1);
        var meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text(name));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Set the config blacklist materials list and reload config so MainConfig caches the new values.
     * Uses {@code load()} (not {@code reload()}) so the in-memory config change is not discarded by
     * a {@code reloadConfig()} disk read.
     */
    private void setConfigMaterials(List<String> materials) {
        plugin.getConfig().set("blacklist.materials", materials);
        plugin.getConfigManager().main().load();
    }

    private void setConfigNames(List<String> names) {
        plugin.getConfig().set("blacklist.names", names);
        plugin.getConfigManager().main().load();
    }

    /** Fire a SWAP_OFFHAND click targeting rawSlot 27 (→ player main storage slot 9). */
    private void sortMainStorage(PlayerMock player) {
        plugin.getSortingPrefs().setSortOverItems(player, true);
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        InventoryView view = player.openInventory(chest);
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 27, ClickType.SWAP_OFFHAND,
                InventoryAction.UNKNOWN) {
            @Override
            public ItemStack getCurrentItem() {
                return new ItemStack(Material.STONE, 1);
            }
        };
        server.getPluginManager().callEvent(event);
    }

    /** Count the total amount of {@code mat} across all inventory slots. */
    private static int totalAmount(Inventory inv, Material mat) {
        int n = 0;
        for (ItemStack is : inv.getContents()) {
            if (is != null && is.getType() == mat) n += is.getAmount();
        }
        return n;
    }

    /** Count the number of slots in [from, to) that contain {@code mat}. */
    private static int countSlots(Inventory inv, int from, int to, Material mat) {
        int n = 0;
        for (int i = from; i < to; i++) {
            ItemStack is = inv.getItem(i);
            if (is != null && is.getType() == mat) n++;
        }
        return n;
    }

    // -------------------------------------------------------------------------
    // ProtectedItems.nameToken — unit-level normalization tests
    // -------------------------------------------------------------------------

    @Test
    void nameToken_simpleSpaces() {
        assertEquals("creative_menu", ProtectedItems.nameToken("Creative Menu"));
    }

    @Test
    void nameToken_colorCodes() {
        // Legacy §X codes are stripped before slugging so the digit doesn't bleed through.
        assertEquals("creative_menu", ProtectedItems.nameToken("§6Creative Menu!"));
    }

    @Test
    void nameToken_punctuationCollapsed() {
        assertEquals("epic_loot_box", ProtectedItems.nameToken("Epic-Loot-Box!"));
    }

    @Test
    void nameToken_alreadyLowercase() {
        assertEquals("magic_sword", ProtectedItems.nameToken("magic_sword"));
    }

    @Test
    void nameToken_allSymbolsProducesEmptyToken() {
        assertEquals("", ProtectedItems.nameToken("!!!"));
    }

    @Test
    void nameToken_leadingTrailingSpecialCharsStripped() {
        assertEquals("hello_world", ProtectedItems.nameToken("...Hello World..."));
    }

    @Test
    void nameToken_numbersPreserved() {
        assertEquals("item_42", ProtectedItems.nameToken("Item 42"));
    }

    // -------------------------------------------------------------------------
    // Config material blacklist — integration
    // -------------------------------------------------------------------------

    @Test
    void configMaterial_protectedSlotUntouched_whileOthersSort() {
        setConfigMaterials(List.of("DIRT"));
        // Non-OP player: has clicksorted.sort.* (default:true) but NOT undeclared blacklist nodes.
        PlayerMock player = server.addPlayer("Alice");

        // Two STONE stacks that should merge, and DIRT that should be untouched.
        player.getInventory().setItem(9, new ItemStack(Material.STONE, 5));
        player.getInventory().setItem(10, new ItemStack(Material.STONE, 10));
        player.getInventory().setItem(11, new ItemStack(Material.DIRT, 3));

        sortMainStorage(player);

        // STONE must have merged (sort ran).
        assertEquals(1, countSlots(player.getInventory(), 9, 36, Material.STONE),
                "STONE stacks should merge — sort must have run");
        assertEquals(15, totalAmount(player.getInventory(), Material.STONE));

        // DIRT slot must be untouched — same slot, same amount.
        ItemStack slot11 = player.getInventory().getItem(11);
        assertNotNull(slot11, "Protected DIRT slot must still contain an item");
        assertEquals(Material.DIRT, slot11.getType(), "Slot 11 must still be DIRT");
        assertEquals(3, slot11.getAmount(), "DIRT amount must be unchanged");
    }

    @Test
    void configMaterial_unprotectedMaterialSortsNormally() {
        setConfigMaterials(List.of("DIRT"));
        PlayerMock player = server.addPlayer("Alice");

        player.getInventory().setItem(9, new ItemStack(Material.STONE, 5));
        player.getInventory().setItem(10, new ItemStack(Material.STONE, 10));

        sortMainStorage(player);

        assertEquals(1, countSlots(player.getInventory(), 9, 36, Material.STONE),
                "Non-blacklisted STONE must still merge normally");
    }

    @Test
    void configMaterial_unknownEntrySkippedOnLoad_noException() {
        // Setting an unrecognised material must not throw — it is warned and skipped.
        assertDoesNotThrow(() -> setConfigMaterials(List.of("NOT_A_REAL_MATERIAL_XYZ")));
        // After loading, the blacklist should be empty (unknown entry was dropped).
        assertTrue(plugin.getConfigManager().main().getBlacklistMaterials().isEmpty(),
                "Unknown material entry should be skipped and not appear in the cached set");
    }

    // -------------------------------------------------------------------------
    // Config name blacklist — case-insensitive matching
    // -------------------------------------------------------------------------

    @Test
    void configName_protectedByNameCaseInsensitively() {
        // Config entry uses mixed case; item display name uses a different case — must still match.
        setConfigNames(List.of("Creative Menu"));
        PlayerMock player = server.addPlayer("Alice");

        ItemStack protectedItem = named(Material.NETHER_STAR, "creative menu"); // lowercase variant
        protectedItem.setAmount(1);
        player.getInventory().setItem(9, protectedItem);
        player.getInventory().setItem(10, new ItemStack(Material.STONE, 5));
        player.getInventory().setItem(11, new ItemStack(Material.STONE, 10));

        sortMainStorage(player);

        // STONE must have merged (sort ran).
        assertEquals(1, countSlots(player.getInventory(), 9, 36, Material.STONE),
                "STONE must merge — sort must have run");

        // The named nether star must be untouched in slot 9.
        ItemStack slot9 = player.getInventory().getItem(9);
        assertNotNull(slot9, "Protected named item must remain");
        assertEquals(Material.NETHER_STAR, slot9.getType(), "Slot 9 must still contain the protected nether star");
    }

    @Test
    void configName_differentNameIsNotProtected() {
        setConfigNames(List.of("Creative Menu"));
        PlayerMock player = server.addPlayer("Alice");

        // "Regular Item" is not blacklisted — item can be sorted/moved.
        ItemStack notProtected = named(Material.NETHER_STAR, "Regular Item");
        notProtected.setAmount(1);
        player.getInventory().setItem(9, notProtected);
        player.getInventory().setItem(10, new ItemStack(Material.STONE, 64));

        sortMainStorage(player);

        // Item counts must be conserved (sort ran, items moved freely).
        assertEquals(64, totalAmount(player.getInventory(), Material.STONE),
                "STONE amount must be conserved");
        assertEquals(1, totalAmount(player.getInventory(), Material.NETHER_STAR),
                "Unprotected named item must still be present");
    }

    // -------------------------------------------------------------------------
    // Permission material node
    // -------------------------------------------------------------------------

    @Test
    void permissionMaterialNode_protectsSlot() {
        // Non-OP player with the specific blacklist permission attached.
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "clicksorted.blacklist.material.dirt", true);

        player.getInventory().setItem(9, new ItemStack(Material.STONE, 5));
        player.getInventory().setItem(10, new ItemStack(Material.STONE, 10));
        player.getInventory().setItem(11, new ItemStack(Material.DIRT, 7));

        sortMainStorage(player);

        // Sort must have run (STONE merged).
        assertEquals(1, countSlots(player.getInventory(), 9, 36, Material.STONE),
                "STONE must merge — sort must have run");

        // DIRT must be untouched.
        ItemStack slot11 = player.getInventory().getItem(11);
        assertNotNull(slot11, "Permission-protected DIRT slot must still contain an item");
        assertEquals(Material.DIRT, slot11.getType());
        assertEquals(7, slot11.getAmount(), "DIRT amount must be unchanged");
    }

    @Test
    void permissionMaterialNode_onlyAffectsGrantedPlayer() {
        // A non-OP player without the blacklist permission: DIRT is sorted normally.
        PlayerMock player = server.addPlayer("Bob");  // no blacklist attachment
        // (clicksorted.blacklist.material.dirt is an undeclared dynamic node — non-OP returns false)

        player.getInventory().setItem(9, new ItemStack(Material.STONE, 5));
        player.getInventory().setItem(10, new ItemStack(Material.DIRT, 7));
        player.getInventory().setItem(11, new ItemStack(Material.STONE, 10));

        sortMainStorage(player);

        // Both STONE stacks must have merged — sort ran and DIRT was not protected.
        assertEquals(1, countSlots(player.getInventory(), 9, 36, Material.STONE),
                "STONE must merge for a player without the blacklist permission");
        assertEquals(15, totalAmount(player.getInventory(), Material.STONE),
                "STONE amount must be conserved");
        // DIRT is somewhere in the inventory, possibly relocated.
        assertEquals(7, totalAmount(player.getInventory(), Material.DIRT),
                "DIRT amount must be conserved for a player without the blacklist permission");
    }

    // -------------------------------------------------------------------------
    // Permission name slug node
    // -------------------------------------------------------------------------

    @Test
    void permissionNameSlugNode_protectsNamedItem() {
        // "creative_menu" is the slug for "Creative Menu"
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "clicksorted.blacklist.name.creative_menu", true);

        ItemStack menu = named(Material.NETHER_STAR, "Creative Menu");
        menu.setAmount(1);
        player.getInventory().setItem(9, menu);
        player.getInventory().setItem(10, new ItemStack(Material.STONE, 5));
        player.getInventory().setItem(11, new ItemStack(Material.STONE, 10));

        sortMainStorage(player);

        // STONE must have merged (sort ran).
        assertEquals(1, countSlots(player.getInventory(), 9, 36, Material.STONE),
                "STONE must merge — sort must have run");

        // Protected named item must still be in slot 9.
        ItemStack slot9 = player.getInventory().getItem(9);
        assertNotNull(slot9, "Permission-name-protected item must remain");
        assertEquals(Material.NETHER_STAR, slot9.getType(), "Slot 9 must still hold the protected nether star");
        assertTrue(slot9.hasItemMeta() && slot9.getItemMeta().hasDisplayName(),
                "Protected item must retain its display name");
    }

    // -------------------------------------------------------------------------
    // Protected item is not packed when bundle packing is enabled
    // -------------------------------------------------------------------------

    @Test
    void configMaterial_protectedItemNotPacked_whenBundlePackingOn() {
        setConfigMaterials(List.of("DIRT"));
        PlayerMock player = server.addPlayer("Alice");
        plugin.getSortingPrefs().setBundlePackInInventory(player, true);

        // A bundle + protected DIRT (must not be packed) + cobblestone (may pack).
        player.getInventory().setItem(9, new ItemStack(Material.BUNDLE, 1));
        player.getInventory().setItem(10, new ItemStack(Material.DIRT, 20));
        player.getInventory().setItem(11, new ItemStack(Material.COBBLESTONE, 8));

        sortMainStorage(player);

        // DIRT must still be in the inventory (slot not touched).
        assertEquals(20, totalAmount(player.getInventory(), Material.DIRT),
                "All protected DIRT must still be in the inventory");

        // DIRT must NOT be inside the bundle.
        for (ItemStack is : player.getInventory().getContents()) {
            if (is != null && is.getType() == Material.BUNDLE) {
                org.bukkit.inventory.meta.BundleMeta meta =
                        (org.bukkit.inventory.meta.BundleMeta) is.getItemMeta();
                for (ItemStack bundled : meta.getItems()) {
                    if (bundled != null) {
                        assertNotEquals(Material.DIRT, bundled.getType(),
                                "Protected DIRT must not be packed into the bundle");
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Container sort is also protected
    // -------------------------------------------------------------------------

    @Test
    void configMaterial_containerSortProtectsSlot() {
        setConfigMaterials(List.of("DIRT"));
        PlayerMock player = server.addPlayer("Alice");

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, new ItemStack(Material.DIRT, 5));
        chest.setItem(1, new ItemStack(Material.STONE, 10));
        chest.setItem(2, new ItemStack(Material.STONE, 5));
        InventoryView view = player.openInventory(chest);

        // Click raw slot 1 (STONE in chest) to trigger container sort.
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 1, ClickType.SWAP_OFFHAND,
                InventoryAction.UNKNOWN) {
            @Override
            public ItemStack getCurrentItem() {
                return new ItemStack(Material.STONE, 1);
            }
        };
        server.getPluginManager().callEvent(event);

        // STONE must have merged (sort ran).
        int stoneSlots = 0;
        for (int i = 0; i < chest.getSize(); i++) {
            ItemStack is = chest.getItem(i);
            if (is != null && is.getType() == Material.STONE) stoneSlots++;
        }
        assertEquals(1, stoneSlots, "STONE stacks must merge in the container — sort must have run");

        // DIRT in slot 0 must be untouched.
        ItemStack slot0 = chest.getItem(0);
        assertNotNull(slot0, "Protected DIRT in slot 0 must remain");
        assertEquals(Material.DIRT, slot0.getType(), "Slot 0 must still be DIRT");
        assertEquals(5, slot0.getAmount(), "DIRT amount must be unchanged");
    }
}
