package net.kccricket.clicksorted;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test that the per-player action throttle gates the click-sort path: a rapid second
 * sort within the cooldown window is dropped and the player receives the rate-limited notice, while
 * the first sort still completes. Uses a non-op player so the {@code clicksorted.throttle.bypass}
 * permission does not exempt it.
 */
class ThrottleIntegrationTest extends AbstractClickSortedTest {

    private long countMaterialSlots(Inventory inv, Material mat) {
        long slots = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() == mat) slots++;
        }
        return slots;
    }

    @Test
    void rapidSecondSortIsThrottledAndNotified() {
        // Long cooldown so the second click is unambiguously inside the window.
        plugin.getConfig().set("action_cooldown_ms", 60_000);

        PlayerMock player = server.addPlayer("NonOp"); // non-op → not exempt from throttling

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, stack(Material.STONE, 5));
        chest.setItem(1, stack(Material.STONE, 10));
        InventoryView view = player.openInventory(chest);

        // First sort: allowed → the two STONE stacks merge into one slot of 15.
        server.getPluginManager().callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 0));
        assertEquals(1, countMaterialSlots(chest, Material.STONE), "first sort should merge the stacks");
        drainMessages(player);

        // Re-fragment, then fire a second sort immediately — it should be throttled (no merge).
        chest.setItem(0, stack(Material.STONE, 5));
        chest.setItem(1, stack(Material.STONE, 10));
        server.getPluginManager().callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 0));

        assertEquals(2, countMaterialSlots(chest, Material.STONE),
                "second sort within the cooldown should be dropped, leaving both stacks");
        assertTrue(anyMessageContains(player, "MSG.actionTooFast"),
                "player should receive the rate-limited throttle notice");
    }

    @Test
    void nonActionClicksDoNotConsumeTheCooldownWindow() {
        // The throttle must only spend the cooldown on confirmed actions, never on ordinary clicks.
        // If the gate were hoisted to the top of the handler this test would fail: the plain clicks
        // would burn (or deny) the window and the following sort would be wrongly dropped.
        plugin.getConfig().set("action_cooldown_ms", 60_000);

        PlayerMock player = server.addPlayer("NonOp");

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, stack(Material.STONE, 5));
        chest.setItem(1, stack(Material.STONE, 10));
        InventoryView view = player.openInventory(chest);

        // A flurry of non-trigger clicks (plain LEFT, not the SWAP sort trigger): no sort, no cycle,
        // no bundle — so the throttle is never engaged.
        for (int i = 0; i < 5; i++) {
            server.getPluginManager().callEvent(fireClick(view, ClickType.LEFT, 0));
        }
        assertEquals(2, countMaterialSlots(chest, Material.STONE), "plain clicks must not sort");

        // The first real sort immediately after must still be allowed.
        server.getPluginManager().callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 0));
        assertEquals(1, countMaterialSlots(chest, Material.STONE),
                "sort after non-action clicks should succeed — the window was never consumed");
    }

    @Test
    void opIsExemptFromThrottlingOnRealClicks() {
        // Op players hold clicksorted.throttle.bypass (default op), so rapid repeated sorts all land.
        plugin.getConfig().set("action_cooldown_ms", 60_000);

        PlayerMock op = addOpPlayer("Admin");

        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, stack(Material.STONE, 5));
        chest.setItem(1, stack(Material.STONE, 10));
        InventoryView view = op.openInventory(chest);

        server.getPluginManager().callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 0));
        assertEquals(1, countMaterialSlots(chest, Material.STONE), "first sort should merge");

        // Re-fragment and sort again immediately — bypass means no throttle.
        chest.setItem(0, stack(Material.STONE, 5));
        chest.setItem(1, stack(Material.STONE, 10));
        server.getPluginManager().callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 0));
        assertEquals(1, countMaterialSlots(chest, Material.STONE),
                "second immediate sort should also merge — op bypasses the throttle");
    }
}
