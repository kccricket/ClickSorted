package me.desht.dhutils;

import me.desht.clicksort.ClickSortPlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/**
 * Class to get the displayed name (as the client shows) for an item.
 */
public class ItemNames {
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
        ClickSortPlugin inst = ClickSortPlugin.getInstance();
        if (inst == null) {
            return stack.getType().name();
        }
        return inst.getConfigManager().items().getItemName(stack);
    }

}
