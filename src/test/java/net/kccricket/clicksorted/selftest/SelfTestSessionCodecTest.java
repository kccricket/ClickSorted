package net.kccricket.clicksorted.selftest;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round-trips {@link SelfTestSession#encodeItems}/{@link SelfTestSession#decodeItems} in isolation. */
class SelfTestSessionCodecTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void roundTrip_nullAndAirBecomeEmptySentinelAndDecodeBackToNull() {
        ItemStack[] items = {null, new ItemStack(Material.AIR)};
        List<String> encoded = SelfTestSession.encodeItems(items);
        assertEquals("", encoded.get(0));
        assertEquals("", encoded.get(1));

        ItemStack[] decoded = SelfTestSession.decodeItems(encoded);
        assertNull(decoded[0]);
        assertNull(decoded[1]);
    }

    @Test
    void roundTrip_plainStackPreservesTypeAndAmount() {
        ItemStack[] items = {new ItemStack(Material.STONE, 42)};
        ItemStack[] decoded = SelfTestSession.decodeItems(SelfTestSession.encodeItems(items));
        assertEquals(Material.STONE, decoded[0].getType());
        assertEquals(42, decoded[0].getAmount());
    }

    @Test
    void roundTrip_namedStackPreservesDisplayName() {
        ItemStack is = new ItemStack(Material.DIAMOND_SWORD, 1);
        ItemMeta meta = is.getItemMeta();
        meta.displayName(Component.text("Excalibur"));
        is.setItemMeta(meta);

        ItemStack[] decoded = SelfTestSession.decodeItems(SelfTestSession.encodeItems(new ItemStack[]{is}));
        assertTrue(decoded[0].isSimilar(is));
    }

    @Test
    void roundTrip_bundleWithContentsPreservesInnerItems() {
        ItemStack bundle = new ItemStack(Material.BUNDLE, 1);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        meta.setItems(List.of(new ItemStack(Material.STONE, 5), new ItemStack(Material.DIRT, 3)));
        bundle.setItemMeta(meta);

        ItemStack[] decoded = SelfTestSession.decodeItems(SelfTestSession.encodeItems(new ItemStack[]{bundle}));
        assertEquals(Material.BUNDLE, decoded[0].getType());
        BundleMeta decodedMeta = (BundleMeta) decoded[0].getItemMeta();
        List<ItemStack> innerItems = decodedMeta.getItems();
        assertEquals(2, innerItems.size());
        long stoneAmt = innerItems.stream().filter(i -> i.getType() == Material.STONE).mapToInt(ItemStack::getAmount).sum();
        long dirtAmt = innerItems.stream().filter(i -> i.getType() == Material.DIRT).mapToInt(ItemStack::getAmount).sum();
        assertEquals(5L, stoneAmt);
        assertEquals(3L, dirtAmt);
    }

    @Test
    void decodeItems_corruptBlobNullsThatSlotWithoutAbortingTheRest() {
        String validBlob = Base64.getEncoder().encodeToString(new ItemStack(Material.STONE, 1).serializeAsBytes());
        List<String> blobs = List.of("not-valid-base64!!!", validBlob);

        ItemStack[] decoded = SelfTestSession.decodeItems(blobs);
        assertNull(decoded[0]);
        assertNotNull(decoded[1]);
        assertEquals(Material.STONE, decoded[1].getType());
    }
}
