package net.kccricket.clicksorted;

import net.kccricket.clicksorted.sort.SortEngine;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SortEngine#mergeStacks}: the in-place, slot-aligned stack-combining step used by
 * the bundle-pack flow. Combining must drain small stacks into larger ones while leaving the larger
 * stacks in their positions, must clear emptied slots, and must never merge bundles together.
 *
 * Uses MockBukkit (via AbstractClickSortedTest) because ItemStack/meta are server-side objects.
 */
class SortEngineTest extends AbstractClickSortedTest {

    @Test
    void mergeStacks_drainsSmallIntoLarge_keepingLargePosition() {
        // 42 (slot 0) + 2 (slot 1) → the larger stack at slot 0 grows to 44, slot 1 cleared.
        List<ItemStack> items = new ArrayList<>(Arrays.asList(
                new ItemStack(Material.COBBLESTONE, 42),
                new ItemStack(Material.COBBLESTONE, 2)));

        SortEngine.mergeStacks(items);

        assertNotNull(items.get(0));
        assertEquals(44, items.get(0).getAmount(), "Larger stack keeps its slot and grows");
        assertNull(items.get(1), "Drained small stack's slot is cleared");
    }

    @Test
    void mergeStacks_distinctItemsUntouched() {
        List<ItemStack> items = new ArrayList<>(Arrays.asList(
                new ItemStack(Material.COBBLESTONE, 5),
                new ItemStack(Material.DIRT, 3)));

        SortEngine.mergeStacks(items);

        assertEquals(Material.COBBLESTONE, items.get(0).getType());
        assertEquals(5, items.get(0).getAmount());
        assertEquals(Material.DIRT, items.get(1).getType());
        assertEquals(3, items.get(1).getAmount());
    }

    @Test
    void mergeStacks_overflowKeepsMinimumStacks() {
        // 5 × 32 = 160 → minimum 3 stacks (64, 64, 32); the other two slots clear.
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            items.add(new ItemStack(Material.COBBLESTONE, 32));
        }

        SortEngine.mergeStacks(items);

        int stacks = 0;
        int total = 0;
        for (ItemStack is : items) {
            if (is == null) continue;
            stacks++;
            total += is.getAmount();
        }
        assertEquals(3, stacks, "160 items collapse to the minimum 3 stacks");
        assertEquals(160, total, "No items lost");
    }

    @Test
    void mergeStacks_alreadyMinimal_leavesAmountsUnchanged() {
        // Two stacks that can't consolidate (60 + 60 = 120 needs 2 stacks) must not be rebalanced.
        List<ItemStack> items = new ArrayList<>(Arrays.asList(
                new ItemStack(Material.COBBLESTONE, 60),
                new ItemStack(Material.COBBLESTONE, 60)));

        SortEngine.mergeStacks(items);

        assertEquals(60, items.get(0).getAmount(), "No needless rebalancing");
        assertEquals(60, items.get(1).getAmount(), "No needless rebalancing");
    }

    @Test
    void mergeStacks_bundlesNeverMerged() {
        // Bundles are non-stackable and each must remain a separate slot (bin).
        List<ItemStack> items = new ArrayList<>(Arrays.asList(
                new ItemStack(Material.BUNDLE, 1),
                new ItemStack(Material.BUNDLE, 1)));

        SortEngine.mergeStacks(items);

        assertNotNull(items.get(0));
        assertNotNull(items.get(1));
        assertEquals(Material.BUNDLE, items.get(0).getType());
        assertEquals(Material.BUNDLE, items.get(1).getType());
    }
}
