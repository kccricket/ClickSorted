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
 * Integration tests for the {@code drop_excess} overflow branch in {@code InventorySortService}.
 * Overflow happens when re-emitting merged stacks yields more stacks than there are sortable slots —
 * reproduced here with over-sized stacks (amount &gt; max stack size), each of which expands into
 * several stacks on write-back. {@code true} drops the excess; {@code false} aborts with a notice.
 */
class OverflowSortTest extends AbstractClickSortedTest {

    /** Materials with a 64 max stack; each over-stacked entry expands to 4 stacks (200 = 3×64 + 8). */
    private static final Material[] MATS = {
            Material.STONE, Material.DIRT, Material.COBBLESTONE, Material.SAND, Material.GRAVEL,
            Material.OAK_LOG, Material.OAK_PLANKS, Material.GLASS, Material.COAL, Material.REDSTONE
    };

    /** Fill slots 0..MATS.length with one over-sized stack of each material. */
    private Inventory fillOverstacked(PlayerMock player) {
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        for (int i = 0; i < MATS.length; i++) {
            chest.setItem(i, new ItemStack(MATS[i], 200));
        }
        return chest;
    }

    private long nonEmptySlots(Inventory inv) {
        long n = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() != Material.AIR) n++;
        }
        return n;
    }

    @Test
    void overstackedFixtureActuallyOverflows() {
        // Guard: confirm MockBukkit stores the over-sized amount (otherwise the branch isn't reached).
        PlayerMock player = addOpPlayer("Probe");
        Inventory chest = fillOverstacked(player);
        assertEquals(200, chest.getItem(0).getAmount(),
                "MockBukkit should store the over-sized amount un-clamped");
    }

    @Test
    void dropExcessTrue_dropsOverflowAndSorts() {
        plugin.getConfig().set("drop_excess", true);

        PlayerMock player = addOpPlayer("Alice");
        Inventory chest = fillOverstacked(player);
        InventoryView view = player.openInventory(chest);

        server.getPluginManager().callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 0));

        // 10 materials × 4 stacks = 40 stacks into 27 slots → chest fills, the rest are dropped.
        assertEquals(27, nonEmptySlots(chest), "all sortable slots should be filled");
        assertTrue(anyMessageContains(player, "MSG.dropItems"),
                "player should be alerted that excess items were dropped");
    }

    @Test
    void dropExcessFalse_abortsAndNotifies() {
        plugin.getConfig().set("drop_excess", false);

        PlayerMock player = addOpPlayer("Bob");
        Inventory chest = fillOverstacked(player);
        InventoryView view = player.openInventory(chest);

        server.getPluginManager().callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 0));

        assertEquals(MATS.length, nonEmptySlots(chest), "aborted sort must leave the inventory untouched");
        assertEquals(200, chest.getItem(0).getAmount(), "original over-sized stack should be unchanged");
        assertTrue(anyMessageContains(player, "MSG.invOverFlow"),
                "player should see the inventory-overflow notice");
    }
}
