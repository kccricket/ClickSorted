package net.kccricket.clicksorted;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for permission gating on the sort path. By default a player may sort containers;
 * explicitly revoking {@code clicksorted.sort.container} must block it while leaving the rest of the
 * sort pipeline (the top-level {@code clicksorted.sort} check) intact.
 */
class SortPermissionTest extends AbstractClickSortedTest {

    private Inventory fragmentedChest() {
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, stack(Material.STONE, 5));
        chest.setItem(1, stack(Material.STONE, 10));
        return chest;
    }

    private long stoneSlots(Inventory inv) {
        long n = 0;
        for (var item : inv.getContents()) {
            if (item != null && item.getType() == Material.STONE) n++;
        }
        return n;
    }

    @Test
    void containerSortAllowedByDefault() {
        PlayerMock player = server.addPlayer("Default"); // non-op; container perm defaults true
        Inventory chest = fragmentedChest();
        InventoryView view = player.openInventory(chest);

        server.getPluginManager().callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 0));

        assertEquals(1, stoneSlots(chest), "container sort should work with the default permission");
    }

    @Test
    void revokingContainerPermissionBlocksSort() {
        PlayerMock player = server.addPlayer("Restricted");
        // Keep top-level clicksorted.sort, revoke only the container node.
        player.addAttachment(plugin, "clicksorted.sort.container", false);
        player.recalculatePermissions();

        Inventory chest = fragmentedChest();
        InventoryView view = player.openInventory(chest);

        server.getPluginManager().callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 0));

        assertEquals(2, stoneSlots(chest),
                "container sort must be blocked when clicksorted.sort.container is denied");
    }
}
