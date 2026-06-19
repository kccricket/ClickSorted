package net.kccricket.clicksorted.model;

import net.kccricket.clicksorted.logging.Log;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.Map.Entry;

public class SortKey implements Comparable<SortKey> {
    private final String sortPrefix;
    private final Material material;
    private final int durability;
    private final String metaStr;
    private final ItemMeta meta;

    public SortKey(ItemStack stack, SortingMethod sortMethod) {
        String prefix = sortMethod.makeSortPrefix(stack);
        if (prefix == null) {
            this.sortPrefix = stack.getType().toString();
            Log.warning("Can't determine sort prefix for " + stack + " (using "
                    + this.sortPrefix + ")");
        } else {
            this.sortPrefix = prefix;
        }
        this.material = stack.getType();
        // getItemMeta() returns a fresh deep copy on each call, so read it once and reuse it for both
        // the durability probe and the stored meta (Damageable is an ItemMeta) to avoid a redundant
        // clone per SortKey on the sort hot path.
        ItemMeta itemMeta = stack.getItemMeta();
        this.durability = itemMeta instanceof Damageable damageable ? damageable.getDamage() : 0;
        this.meta = itemMeta;
        this.metaStr = makeMetaString();
    }

    /**
     * @return the sortPrefix
     */
    public String getSortPrefix() {
        return sortPrefix;
    }

    /**
     * @return the materialID
     */
    public Material getMaterial() {
        return material;
    }

    /**
     * @return the durability
     */
    public int getDurability() {
        return durability;
    }

    /**
     * @return the metaStr
     */
    public String getMetaStr() {
        return metaStr;
    }

    public ItemStack toItemStack(int amount) {
        ItemStack stack = new ItemStack(getMaterial(), amount);
        stack.setItemMeta(meta);
        return stack;
    }

    @Override
    public int compareTo(SortKey other) {
        if (other == null) {
            return 1;
        }

        int c = this.getSortPrefix().compareTo(other.getSortPrefix());
        if (c != 0) {
            return c;
        }

        // the Material enum members are arranged by item ID
        c = this.getMaterial().ordinal() - other.getMaterial().ordinal();
        if (c != 0) {
            return c;
        }

        c = this.getDurability() - other.getDurability();
        if (c != 0) {
            return c;
        }

        return this.getMetaStr().compareTo(other.getMetaStr());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SortKey sortKey = (SortKey) o;
        // Identity keys on metaStr (the serialized form of meta), not the ItemMeta object itself: this
        // keeps the deep ItemMeta.equals/hashCode off the sort hot path and keeps equals consistent with
        // compareTo, which already orders by metaStr. The meta field is retained only for toItemStack.
        return durability == sortKey.durability && material == sortKey.material
                && metaStr.equals(sortKey.metaStr) && sortPrefix.equals(sortKey.sortPrefix);
    }

    @Override
    public int hashCode() {
        int result = sortPrefix.hashCode();
        result = 31 * result + material.hashCode();
        result = 31 * result + durability;
        result = 31 * result + metaStr.hashCode();
        return result;
    }

    private String makeMetaString() {
        if (meta == null) {
            return "";
        }
        Map<String, Object> map = meta.serialize();

        StringBuilder sb = new StringBuilder();
        for (Entry<String, Object> entry : map.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue().toString())
                    .append(";");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("SortKey[%s|%s|%d|%s]", getSortPrefix(), getMaterial()
                .toString(), getDurability(), getMetaStr());
    }
}
