package net.kccricket.clicksorted.text;

import net.kccricket.clicksorted.ClickSortedPlugin;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Class to get the displayed name (as the client shows) for an item.
 */
public class ItemNames {

    /**
     * The item's explicit name as plain text, following the precedence
     * {@code custom_name} (anvil/plugin display name) → {@code item_name}
     * (data-pack/plugin base name), or {@code null} if it has neither.
     *
     * <p>Books are not special-cased here (see {@link #lookup(ItemStack)}).
     *
     * @param stack the item stack
     * @return the explicit name as plain text, or {@code null} if none
     */
    public static String explicitName(ItemStack stack) {
        if (!stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta.hasDisplayName()) {
            return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        }
        if (meta.hasItemName()) {
            return PlainTextComponentSerializer.plainText().serialize(meta.itemName());
        }
        return null;
    }
    /**
     * Given an item stack, return a friendly printable name for the item, as
     * the (English-language) vanilla Minecraft client would display it.
     *
     * @param stack the item stack
     * @return a friendly printable name for the item
     */
    public static String lookup(ItemStack stack) {
        if (stack.getItemMeta() instanceof BookMeta bookMeta) {
            String title = bookMeta.getTitle();
            return title == null ? null : title.replaceAll("§.", "");
        }
        ClickSortedPlugin inst = ClickSortedPlugin.getInstance();
        if (inst == null) {
            return stack.getType().name();
        }
        return inst.getConfigManager().items().getItemName(stack);
    }

}
