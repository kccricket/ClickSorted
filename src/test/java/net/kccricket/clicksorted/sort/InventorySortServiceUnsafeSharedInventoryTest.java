package net.kccricket.clicksorted.sort;

/*
 * This file is part of ClickSorted
 *
 * ClickSorted is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * ClickSorted is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with ClickSorted. If not, see <http://www.gnu.org/licenses/>.
 */

import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link InventorySortService#isUnsafeSharedInventory(Inventory)} — the Folia-only
 * guard predicate. No plugin bootstrap needed: this exercises the pure predicate against
 * MockBukkit-backed inventories with varying holder/viewer combinations.
 */
class InventorySortServiceUnsafeSharedInventoryTest {

    private static final class PluginHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Inventory pluginInventory() {
        PluginHolder holder = new PluginHolder();
        Inventory inv = server.createInventory(holder, InventoryType.CHEST);
        holder.inventory = inv;
        return inv;
    }

    @Test
    void vanillaInventory_neverUnsafe_regardlessOfViewerCount() {
        Inventory playerInv = server.addPlayer().getInventory();
        assertFalse(InventorySortService.isUnsafeSharedInventory(playerInv));
    }

    @Test
    void pluginInventory_singleViewer_isSafe() {
        Inventory inv = pluginInventory();
        PlayerMock alice = server.addPlayer("Alice");
        alice.openInventory(inv);

        assertFalse(InventorySortService.isUnsafeSharedInventory(inv));
    }

    @Test
    void pluginInventory_multipleViewers_isUnsafe() {
        Inventory inv = pluginInventory();
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        alice.openInventory(inv);
        bob.openInventory(inv);

        assertTrue(InventorySortService.isUnsafeSharedInventory(inv));
    }

    @Test
    void pluginInventory_noViewers_isSafe() {
        Inventory inv = pluginInventory();
        assertFalse(InventorySortService.isUnsafeSharedInventory(inv));
    }
}
