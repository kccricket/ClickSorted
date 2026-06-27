package net.kccricket.clicksorted;

import net.kccricket.clicksorted.gui.LockGuiHolder;
import org.bukkit.Material;
import java.util.List;
import java.util.Set;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link net.kccricket.clicksorted.gui.LockGuiListener}.
 *
 * Opens the lock GUI via {@link LockGuiHolder} and fires synthetic {@link InventoryClickEvent}s
 * to verify that:
 * <ul>
 *   <li>Pane clicks toggle lock state and update the pane material.</li>
 *   <li>Divider clicks are ignored.</li>
 *   <li>Every click (including in the real inventory below) is cancelled unconditionally.</li>
 * </ul>
 */
class LockGuiListenerTest extends AbstractClickSortedTest {

    /** Open the lock GUI for the player and return the resulting InventoryView. */
    private InventoryView openLockGui(PlayerMock player) {
        return player.openInventory(new LockGuiHolder(plugin, player).getInventory());
    }

    /**
     * Fire a click at the given rawSlot in the lock GUI.  getCurrentItem() is wired to return
     * the item currently in that chest slot (or AIR if empty) so the plugin's null-guard does
     * not short-circuit before our listener runs.
     */
    private InventoryClickEvent guiClick(InventoryView view, int rawSlot) {
        int topSize = view.getTopInventory().getSize();
        ItemStack current;
        if (rawSlot < topSize) {
            current = view.getTopInventory().getItem(rawSlot);
        } else {
            current = view.getBottomInventory().getItem(rawSlot - topSize);
        }
        if (current == null) current = new ItemStack(Material.AIR);
        final ItemStack finalCurrent = current;
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, rawSlot, ClickType.LEFT,
                InventoryAction.UNKNOWN) {
            @Override
            public ItemStack getCurrentItem() {
                return finalCurrent;
            }
        };
        server.getPluginManager().callEvent(event);
        return event;
    }

    // --- Cancellation ---

    @Test
    void clickInChestAreaIsCancelled() {
        PlayerMock player = addOpPlayer("Alice");
        InventoryView view = openLockGui(player);

        InventoryClickEvent event = guiClick(view, 0);

        assertTrue(event.isCancelled(), "All clicks in the lock GUI must be cancelled");
    }

    @Test
    void clickInRealInventoryAreaIsCancelled() {
        PlayerMock player = addOpPlayer("Alice");
        player.getInventory().setItem(0, stack(Material.STONE, 1));
        InventoryView view = openLockGui(player);

        // rawSlot >= 45 means the real bottom inventory.
        InventoryClickEvent event = guiClick(view, 45);

        assertTrue(event.isCancelled(), "Clicks in the real inventory below must be cancelled");
    }

    @Test
    void clickInRealInventoryDoesNotMoveItems() {
        PlayerMock player = addOpPlayer("Alice");
        player.getInventory().setItem(0, stack(Material.STONE, 5));
        InventoryView view = openLockGui(player);

        guiClick(view, 45);  // real inventory slot 0

        ItemStack after = player.getInventory().getItem(0);
        assertNotNull(after, "Real inventory item must not be removed");
        assertEquals(Material.STONE, after.getType());
        assertEquals(5, after.getAmount());
    }

    // --- Pane toggle ---

    @Test
    void clickPaneTogglesLockFromUnlockedToLocked() {
        PlayerMock player = addOpPlayer("Alice");
        InventoryView view = openLockGui(player);

        // Chest slot 0 maps to player inv slot 9. Initially unlocked → lime pane.
        assertEquals(Material.LIME_STAINED_GLASS_PANE,
                view.getTopInventory().getItem(0).getType(),
                "Unlocked pane should be lime");

        guiClick(view, 0);

        assertTrue(plugin.getSortingPrefs().getLockedSlots(player).contains(9),
                "Player inv slot 9 should be locked after clicking chest slot 0");
        assertEquals(Material.BARRIER,
                view.getTopInventory().getItem(0).getType(),
                "Locked pane should turn red");
    }

    @Test
    void clickPaneTogglesLockFromLockedToUnlocked() {
        PlayerMock player = addOpPlayer("Alice");
        plugin.getSortingPrefs().setLockedSlots(player, Set.of(9));
        InventoryView view = openLockGui(player);

        // Should open with red pane at slot 0 (locked).
        assertEquals(Material.BARRIER,
                view.getTopInventory().getItem(0).getType(),
                "Pre-locked pane should open as red");

        guiClick(view, 0);

        assertFalse(plugin.getSortingPrefs().getLockedSlots(player).contains(9),
                "Player inv slot 9 should be unlocked after clicking");
        assertEquals(Material.LIME_STAINED_GLASS_PANE,
                view.getTopInventory().getItem(0).getType(),
                "Unlocked pane should turn lime");
    }

    @Test
    void clickHotbarPaneTogglesCorrectInvSlot() {
        PlayerMock player = addOpPlayer("Alice");
        InventoryView view = openLockGui(player);

        // Chest slot 36 maps to hotbar slot 0.
        guiClick(view, 36);

        assertTrue(plugin.getSortingPrefs().getLockedSlots(player).contains(0),
                "Hotbar slot 0 should be locked after clicking chest slot 36");
    }

    // --- Admin-locked slots ---

    private void setAdminLockedSlots(List<Integer> slots) {
        plugin.getConfig().set("locked_slots.player", slots);
        plugin.getConfigManager().main().load();
    }

    @Test
    void adminConfigLockedSlotDoesNotToggle() {
        PlayerMock player = addOpPlayer("Alice");
        setAdminLockedSlots(List.of(27)); // inv slot 27 → chest slot 18
        InventoryView view = openLockGui(player);

        guiClick(view, 18);  // chest slot 18 → inv slot 27 (admin-locked)

        assertTrue(plugin.getSortingPrefs().getLockedSlots(player).isEmpty(),
                "Clicking an admin-locked slot must not modify the player's per-player locks");
    }

    @Test
    void adminConfigLockedSlotRendersAsIronBars() {
        PlayerMock player = addOpPlayer("Alice");
        setAdminLockedSlots(List.of(27)); // inv slot 27 → chest slot 18
        InventoryView view = openLockGui(player);

        assertEquals(Material.IRON_BARS,
                view.getTopInventory().getItem(18).getType(),
                "Admin-locked slot should render as IRON_BARS pane");
    }

    @Test
    void adminPermissionLockedSlotDoesNotToggle() {
        // Non-OP player with an explicit clicksorted.lock.player.slot.27 node.
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "clicksorted.lock.player.slot.27", true);
        InventoryView view = openLockGui(player);

        guiClick(view, 18);  // chest slot 18 → inv slot 27 (permission-locked for this player)

        assertTrue(plugin.getSortingPrefs().getLockedSlots(player).isEmpty(),
                "Clicking a permission-locked slot must not toggle per-player locks");
    }

    @Test
    void adminPermissionLockedSlotRendersAsIronBars() {
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "clicksorted.lock.player.slot.27", true);
        InventoryView view = openLockGui(player);

        assertEquals(Material.IRON_BARS,
                view.getTopInventory().getItem(18).getType(),
                "Permission-locked slot should render as IRON_BARS pane");
    }

    @Test
    void adminLockedHotbarSlotRendersAsIronBars() {
        // Hotbar slot 0 is also covered — chest slot 36 maps to inv slot 0.
        PlayerMock player = addOpPlayer("Alice");
        setAdminLockedSlots(List.of(0)); // inv slot 0 (hotbar slot 0) → chest slot 36
        InventoryView view = openLockGui(player);

        assertEquals(Material.IRON_BARS,
                view.getTopInventory().getItem(36).getType(),
                "Admin-locked hotbar slot 0 should render as IRON_BARS");
    }

    @Test
    void adminLockedHotbarSlotDoesNotToggle() {
        PlayerMock player = addOpPlayer("Alice");
        setAdminLockedSlots(List.of(0)); // inv slot 0 (hotbar) → chest slot 36
        InventoryView view = openLockGui(player);

        guiClick(view, 36);

        assertTrue(plugin.getSortingPrefs().getLockedSlots(player).isEmpty(),
                "Clicking an admin-locked hotbar slot must not toggle per-player locks");
    }

    // --- Divider ---

    @Test
    void clickDividerDoesNotToggleAnyLock() {
        PlayerMock player = addOpPlayer("Alice");
        InventoryView view = openLockGui(player);

        guiClick(view, 27);  // first divider slot

        assertTrue(plugin.getSortingPrefs().getLockedSlots(player).isEmpty(),
                "Clicking the divider should not lock any slot");
    }

    @Test
    void dividerSlotsAreBlackPanesExceptLast() {
        PlayerMock player = addOpPlayer("Alice");
        InventoryView view = openLockGui(player);

        for (int slot = 27; slot < 35; slot++) {
            assertEquals(Material.BLACK_STAINED_GLASS_PANE,
                    view.getTopInventory().getItem(slot).getType(),
                    "Divider slot " + slot + " should be a black pane");
        }
        // Slot 35 (rightmost divider) is the help head.
        assertEquals(Material.BOOK,
                view.getTopInventory().getItem(35).getType(),
                "Rightmost divider slot (35) should be the help book");
    }
}
