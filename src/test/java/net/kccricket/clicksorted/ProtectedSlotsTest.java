package net.kccricket.clicksorted;

import net.kccricket.clicksorted.sort.ProtectedSlots;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the admin-enforced player slot lock ({@link ProtectedSlots}).
 *
 * <p>Covers:
 * <ul>
 *   <li>{@link ProtectedSlots#EMPTY} and {@link ProtectedSlots#isEmpty()}.</li>
 *   <li>Config-slot channel: slots in the config list are blocked; others are not.</li>
 *   <li>Out-of-range config values are warned and skipped on load (no exception).</li>
 *   <li>Permission channel: {@code clicksorted.lock.player.slot.<n>} granted explicitly blocks
 *       the slot for that player.</li>
 *   <li>OP guard: an OP player without an explicit node grant is <em>not</em> blocked (the
 *       {@code isPermissionSet} check prevents the {@code PermissionDefault.OP} false-positive).</li>
 *   <li>Integration: admin-locked slot (config) is never read/moved/overwritten during a sort.</li>
 *   <li>Integration: permission-locked slot is preserved only for the player who has the node.</li>
 *   <li>Integration: container sorts are unaffected by {@code clicksorted.lock.player.slot.N}.</li>
 * </ul>
 *
 * <p><b>Important:</b> OP players have <em>all</em> permissions in Bukkit, including undeclared
 * dynamic nodes. Tests that verify the permission channel use <em>non-OP</em> players (who have
 * {@code clicksorted.sort.*} via {@code default: true} but lack undeclared dynamic nodes) so the
 * {@code isPermissionSet} guard can be tested in isolation.
 */
class ProtectedSlotsTest extends AbstractClickSortedTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void setAdminLockedSlots(List<Integer> slots) {
        plugin.getConfig().set("locked_slots.player", slots);
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

    // -------------------------------------------------------------------------
    // EMPTY / isEmpty
    // -------------------------------------------------------------------------

    @Test
    void empty_blocksNothing() {
        assertFalse(ProtectedSlots.EMPTY.blocks(0));
        assertFalse(ProtectedSlots.EMPTY.blocks(9));
        assertFalse(ProtectedSlots.EMPTY.blocks(35));
    }

    @Test
    void empty_isEmpty() {
        assertTrue(ProtectedSlots.EMPTY.isEmpty());
    }

    @Test
    void nonEmpty_isNotEmpty() {
        ProtectedSlots ps = new ProtectedSlots(Set.of(9), null);
        assertFalse(ps.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Config channel
    // -------------------------------------------------------------------------

    @Test
    void configSlot_blocksMatchingSlot() {
        ProtectedSlots ps = new ProtectedSlots(Set.of(9, 15), null);
        assertTrue(ps.blocks(9));
        assertTrue(ps.blocks(15));
        assertFalse(ps.blocks(10));
        assertFalse(ps.blocks(0));
    }

    @Test
    void configSlot_unknownValueSkippedOnLoad_noException() {
        // Out-of-range values (negative, >= 36) must not throw — they are warned and skipped.
        assertDoesNotThrow(() -> setAdminLockedSlots(List.of(-1, 36, 100)));
        // After loading, the cached set should be empty (all values were out of range).
        assertTrue(plugin.getConfigManager().main().getLockedPlayerSlots().isEmpty(),
                "Out-of-range slot indices should be skipped and not appear in the cached set");
    }

    @Test
    void configSlot_validValuesLoadCorrectly() {
        setAdminLockedSlots(List.of(0, 9, 35));
        Set<Integer> slots = plugin.getConfigManager().main().getLockedPlayerSlots();
        assertTrue(slots.contains(0));
        assertTrue(slots.contains(9));
        assertTrue(slots.contains(35));
        assertEquals(3, slots.size());
    }

    // -------------------------------------------------------------------------
    // Permission channel
    // -------------------------------------------------------------------------

    @Test
    void permissionNode_explicitlyGrantedBlocksSlot() {
        // Non-OP player — clicksorted.lock.player.slot.20 is an undeclared dynamic node.
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "clicksorted.lock.player.slot.20", true);

        ProtectedSlots ps = ProtectedSlots.forSort(player, plugin.getConfigManager().main());
        assertTrue(ps.blocks(20), "Explicitly granted slot lock must block slot 20");
        assertFalse(ps.blocks(21), "Slot 21 without a node must not be blocked");
    }

    @Test
    void permissionNode_opWithoutExplicitNodeIsNotBlocked() {
        // OP players have all permissions via PermissionDefault.OP — the isPermissionSet
        // guard must prevent this from triggering the slot lock.
        PlayerMock op = addOpPlayer("Bob");

        ProtectedSlots ps = ProtectedSlots.forSort(op, plugin.getConfigManager().main());
        assertFalse(ps.blocks(9),
                "OP without an explicit clicksorted.lock.player.slot.9 node must not be blocked");
        assertFalse(ps.blocks(0),
                "OP without an explicit node must not be blocked on any slot");
    }

    @Test
    void permissionNode_onlyAffectsGrantedPlayer() {
        PlayerMock alice = server.addPlayer("Alice");
        alice.addAttachment(plugin, "clicksorted.lock.player.slot.15", true);

        PlayerMock bob = server.addPlayer("Bob"); // no attachment

        assertTrue(ProtectedSlots.forSort(alice, plugin.getConfigManager().main()).blocks(15),
                "Alice (granted) must be blocked at slot 15");
        assertFalse(ProtectedSlots.forSort(bob, plugin.getConfigManager().main()).blocks(15),
                "Bob (not granted) must not be blocked at slot 15");
    }

    // -------------------------------------------------------------------------
    // Integration: config channel — sort behaviour
    // -------------------------------------------------------------------------

    @Test
    void configLockedSlot_isUntouchedBySortWhileOthersSort() {
        setAdminLockedSlots(List.of(11));
        PlayerMock player = server.addPlayer("Alice"); // non-OP

        player.getInventory().setItem(9, new ItemStack(Material.STONE, 5));
        player.getInventory().setItem(10, new ItemStack(Material.STONE, 10));
        player.getInventory().setItem(11, new ItemStack(Material.DIRT, 3));

        sortMainStorage(player);

        // STONE must have merged (sort ran).
        long stoneSlots = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack is = player.getInventory().getItem(i);
            if (is != null && is.getType() == Material.STONE) stoneSlots++;
        }
        assertEquals(1, stoneSlots, "STONE stacks should merge — sort must have run");

        // Admin-locked DIRT slot must be untouched.
        ItemStack slot11 = player.getInventory().getItem(11);
        assertNotNull(slot11, "Admin-locked slot 11 must still have an item");
        assertEquals(Material.DIRT, slot11.getType(), "Slot 11 must still contain DIRT");
        assertEquals(3, slot11.getAmount(), "DIRT amount must be unchanged");
    }

    // -------------------------------------------------------------------------
    // Integration: permission channel — sort behaviour
    // -------------------------------------------------------------------------

    @Test
    void permissionLockedSlot_isUntouchedBySortForGrantedPlayer() {
        PlayerMock alice = server.addPlayer("Alice");
        alice.addAttachment(plugin, "clicksorted.lock.player.slot.11", true);

        alice.getInventory().setItem(9, new ItemStack(Material.STONE, 5));
        alice.getInventory().setItem(10, new ItemStack(Material.STONE, 10));
        alice.getInventory().setItem(11, new ItemStack(Material.DIRT, 7));

        sortMainStorage(alice);

        // STONE must have merged (sort ran).
        long stoneSlots = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack is = alice.getInventory().getItem(i);
            if (is != null && is.getType() == Material.STONE) stoneSlots++;
        }
        assertEquals(1, stoneSlots, "STONE must merge — sort must have run for Alice");

        ItemStack slot11 = alice.getInventory().getItem(11);
        assertNotNull(slot11);
        assertEquals(Material.DIRT, slot11.getType(), "Alice's permission-locked slot 11 must still contain DIRT");
        assertEquals(7, slot11.getAmount());
    }

    @Test
    void permissionLockedSlot_doesNotAffectOtherPlayer() {
        // Grant Alice slot 11; Bob has no such grant — DIRT in slot 11 should sort freely for Bob.
        PlayerMock alice = server.addPlayer("Alice");
        alice.addAttachment(plugin, "clicksorted.lock.player.slot.11", true);

        PlayerMock bob = server.addPlayer("Bob"); // no attachment
        bob.getInventory().setItem(9, new ItemStack(Material.STONE, 5));
        bob.getInventory().setItem(10, new ItemStack(Material.STONE, 10));
        bob.getInventory().setItem(11, new ItemStack(Material.DIRT, 4));

        sortMainStorage(bob);

        // Sort must have run for Bob.
        long stoneSlots = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack is = bob.getInventory().getItem(i);
            if (is != null && is.getType() == Material.STONE) stoneSlots++;
        }
        assertEquals(1, stoneSlots, "STONE must merge for Bob — sort must have run");

        // DIRT total must be conserved (it was free to move).
        int dirtTotal = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack is = bob.getInventory().getItem(i);
            if (is != null && is.getType() == Material.DIRT) dirtTotal += is.getAmount();
        }
        assertEquals(4, dirtTotal, "DIRT amount must be conserved for Bob");
    }

    // -------------------------------------------------------------------------
    // Integration: container sorts unaffected
    // -------------------------------------------------------------------------

    @Test
    void adminLockedPlayerSlot_doesNotAffectContainerSort() {
        // clicksorted.lock.player.slot.N applies only to player inventories, not containers.
        setAdminLockedSlots(List.of(0, 1, 2));
        PlayerMock player = server.addPlayer("Alice");

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, new ItemStack(Material.STONE, 5));
        chest.setItem(1, new ItemStack(Material.STONE, 10));
        InventoryView view = player.openInventory(chest);

        fireClick(view, ClickType.SWAP_OFFHAND, 0);

        // STONE stacks must have merged (admin slot locks did not block the container sort).
        long stoneSlots = 0;
        for (ItemStack is : chest.getContents()) {
            if (is != null && is.getType() == Material.STONE) stoneSlots++;
        }
        assertEquals(1, stoneSlots, "Container sort must be unaffected by admin player slot locks");
    }
}
