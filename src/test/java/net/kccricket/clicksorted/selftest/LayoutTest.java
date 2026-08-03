package net.kccricket.clicksorted.selftest;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LayoutTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static Map<String, ItemStack> palette() {
        return Map.of(
                "a", new ItemStack(Material.STONE, 1),
                "b", new ItemStack(Material.DIRT, 1));
    }

    @Test
    void parse_simpleTokens_placesAtSequentialSlots() {
        ItemStack[] out = Layout.parse("a:5 . b:3", 5, palette());
        assertEquals(Material.STONE, out[0].getType());
        assertEquals(5, out[0].getAmount());
        assertNull(out[1]);
        assertEquals(Material.DIRT, out[2].getType());
        assertEquals(3, out[2].getAmount());
        assertNull(out[3]);
        assertNull(out[4]);
    }

    @Test
    void parse_bareKeyToken_defaultsAmountToOne() {
        ItemStack[] out = Layout.parse("a", 1, palette());
        assertEquals(1, out[0].getAmount());
    }

    @Test
    void parse_emptySpec_allSlotsNull() {
        ItemStack[] out = Layout.parse("", 3, palette());
        assertNull(out[0]);
        assertNull(out[1]);
        assertNull(out[2]);
    }

    @Test
    void parse_tooManyTokens_throws() {
        assertThrows(IllegalArgumentException.class, () -> Layout.parse("a a a", 2, palette()));
    }

    @Test
    void parse_unknownKey_throws() {
        assertThrows(IllegalArgumentException.class, () -> Layout.parse("z:1", 1, palette()));
    }

    @Test
    void parse_bundleToken_buildsBundleWithContents() {
        ItemStack[] out = Layout.parse("B[a:20 b:10]:1", 1, palette());
        ItemStack bundle = out[0];
        assertEquals(Material.BUNDLE, bundle.getType());
        assertEquals(1, bundle.getAmount());
        assertTrue(bundle.getItemMeta() instanceof BundleMeta);
        List<ItemStack> contents = ((BundleMeta) bundle.getItemMeta()).getItems();
        assertEquals(2, contents.size());
        assertEquals(Material.STONE, contents.get(0).getType());
        assertEquals(20, contents.get(0).getAmount());
        assertEquals(Material.DIRT, contents.get(1).getType());
        assertEquals(10, contents.get(1).getAmount());
    }

    @Test
    void render_fallsBackToMaterialNameWhenNoDisplayNameSet() {
        ItemStack[] staged = Layout.parse("a:5 . b:3", 4, palette());
        String rendered = Layout.render(staged);
        assertEquals("STONE:5 . DIRT:3 .", rendered);
    }
}
