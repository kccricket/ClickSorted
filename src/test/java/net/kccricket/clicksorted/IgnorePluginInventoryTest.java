package net.kccricket.clicksorted;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for the {@code ignore_plugin_inventory} config branch. A custom (non-vanilla)
 * {@link InventoryHolder} stands in for a third-party plugin GUI: when the flag is on it must be
 * skipped, when off it sorts normally.
 */
class IgnorePluginInventoryTest extends AbstractClickSortedTest {

    /** A non-vanilla holder — its class lives outside {@code org.bukkit.*}, so it is "plugin" owned. */
    private static final class PluginHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private Inventory pluginChest() {
        PluginHolder holder = new PluginHolder();
        Inventory chest = server.createInventory(holder, InventoryType.CHEST);
        holder.inventory = chest;
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
    void flagOn_pluginInventoryIsSkipped() {
        plugin.getConfig().set("ignore_plugin_inventory", true);

        PlayerMock player = addOpPlayer("Alice");
        Inventory chest = pluginChest();
        InventoryView view = player.openInventory(chest);

        server.getPluginManager().callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 0));

        assertEquals(2, stoneSlots(chest), "plugin-owned inventory should not be sorted when the flag is on");
    }

    @Test
    void flagOff_pluginInventoryIsSorted() {
        plugin.getConfig().set("ignore_plugin_inventory", false);

        PlayerMock player = addOpPlayer("Bob");
        Inventory chest = pluginChest();
        InventoryView view = player.openInventory(chest);

        server.getPluginManager().callEvent(fireClick(view, ClickType.SWAP_OFFHAND, 0));

        assertEquals(1, stoneSlots(chest), "plugin-owned inventory should be sorted when the flag is off");
    }
}
