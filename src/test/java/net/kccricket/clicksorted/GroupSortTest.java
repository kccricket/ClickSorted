package net.kccricket.clicksorted;

import net.kccricket.clicksorted.model.SortingMethod;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test of the {@code GROUP} sort mode (the other half of the feature; every other sort
 * test uses {@code NAME}). It relies on the bundled {@code groups.yml}, which {@code ResourceUpdater}
 * materialises into the data folder on load. {@code STONE} maps to {@code 030-natural-blocks} and
 * {@code APPLE} to {@code 080-food-and-drinks}, so their group order (STONE first) is the reverse of
 * their by-name order (Apple first) — a clean discriminator that GROUP really uses the group file.
 */
class GroupSortTest extends AbstractClickSortedTest {

    private int firstSlotOf(Inventory inv, Material mat) {
        var contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && contents[i].getType() == mat) return i;
        }
        return -1;
    }

    private void resetItems(Inventory chest) {
        chest.clear();
        chest.setItem(0, stack(Material.APPLE, 1));
        chest.setItem(1, stack(Material.STONE, 1));
    }

    @Test
    void groupModeOrdersByGroupAndDiffersFromName() {
        assertTrue(SortingMethod.GROUP.isAvailable(),
                "bundled groups.yml should make GROUP available in tests");

        PlayerMock player = addOpPlayer("Alice");
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        InventoryView view = player.openInventory(chest);

        // GROUP: STONE (030-natural-blocks) sorts before APPLE (080-food-and-drinks).
        plugin.getSortingPrefs().setSortingMethod(player, SortingMethod.GROUP);
        resetItems(chest);
        server.getPluginManager().callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 1));
        assertTrue(firstSlotOf(chest, Material.STONE) < firstSlotOf(chest, Material.APPLE),
                "GROUP order should place STONE before APPLE");

        // NAME: "Apple" sorts before "Stone" — the reverse order, proving GROUP used the group file.
        plugin.getSortingPrefs().setSortingMethod(player, SortingMethod.NAME);
        resetItems(chest);
        server.getPluginManager().callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 1));
        assertTrue(firstSlotOf(chest, Material.APPLE) < firstSlotOf(chest, Material.STONE),
                "NAME order should place APPLE before STONE");
    }
}
