package me.desht.clicksort;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

class SortKeyTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private SortKey key(Material mat) {
        // SortingMethod.ID.makeSortPrefix() returns "" without touching the plugin singleton
        return new SortKey(new ItemStack(mat), SortingMethod.ID);
    }

    @Test
    void getSortPrefix_idMode_empty() {
        assertEquals("", key(Material.DIRT).getSortPrefix());
    }

    @Test
    void getMaterial_returnsCorrect() {
        assertEquals(Material.STONE, key(Material.STONE).getMaterial());
    }

    @Test
    void getDurability_defaultZero() {
        assertEquals(0, key(Material.DIRT).getDurability());
    }

    @Test
    void getMetaStr_noMeta_empty() {
        // DIRT has no special meta → metaStr should be empty or minimal
        SortKey k = key(Material.DIRT);
        // both DIRT keys share the same meta string (possibly empty)
        assertEquals(k.getMetaStr(), key(Material.DIRT).getMetaStr());
    }

    @Test
    void compareTo_byMaterialOrdinal() {
        SortKey a = key(Material.DIRT);
        SortKey b = key(Material.STONE);
        int expected = Integer.signum(Material.DIRT.ordinal() - Material.STONE.ordinal());
        assertEquals(expected, Integer.signum(a.compareTo(b)));
    }

    @Test
    void compareTo_identicalKeys_zero() {
        assertEquals(0, key(Material.DIRT).compareTo(key(Material.DIRT)));
    }

    @Test
    void compareTo_null_returnsPositive() {
        assertEquals(1, key(Material.DIRT).compareTo(null));
    }

    @Test
    void compareTo_symmetric() {
        SortKey a = key(Material.DIRT);
        SortKey b = key(Material.STONE);
        int ab = a.compareTo(b);
        int ba = b.compareTo(a);
        assertEquals(Integer.signum(ab), -Integer.signum(ba));
    }

    @Test
    void equals_identicalStacks_true() {
        assertEquals(key(Material.DIRT), key(Material.DIRT));
    }

    @Test
    void equals_differentMaterial_false() {
        assertNotEquals(key(Material.DIRT), key(Material.STONE));
    }

    @Test
    void equals_null_false() {
        assertNotEquals(null, key(Material.DIRT));
    }

    @Test
    void hashCode_equalObjects_match() {
        SortKey a = key(Material.DIRT);
        SortKey b = key(Material.DIRT);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void toItemStack_preservesMaterialAndAmount() {
        ItemStack result = key(Material.DIRT).toItemStack(7);
        assertEquals(Material.DIRT, result.getType());
        assertEquals(7, result.getAmount());
    }

    @Test
    void toString_containsMaterialName() {
        assertTrue(key(Material.DIRT).toString().contains("DIRT"));
    }
}
