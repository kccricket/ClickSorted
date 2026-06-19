package net.kccricket.clicksorted.model;

import net.kccricket.clicksorted.logging.Log;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

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

    // Note: this orders by metaStr while equals() also weighs the full ItemMeta, so compareTo can return
    // 0 for two keys that equals() considers distinct (same metaStr, different meta). That deviation from
    // the Comparable/equals contract is intentional and harmless: compareTo only ORDERS the already-
    // distinct keys (merging is done via equals/hashCode in a HashMap), so the worst case is two
    // identical-looking stacks sorting in an arbitrary adjacent order — never a lost item. Do NOT
    // "reconcile" this by making equals key on metaStr only; see the warning in equals().
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
        // DO NOT drop `meta` from equals/hashCode in favor of metaStr alone. Fungible (stackable) items
        // are merged in a HashMap keyed by SortKey, so two keys that compare equal here get their stacks
        // collapsed into one. metaStr is a LOSSY, unsorted toString of meta.serialize() — distinct
        // ItemMeta can collide to the same metaStr — so keying merge-equality on it alone would merge a
        // custom/plugin itemstack into a plain one and silently destroy the special metadata. Requiring
        // the full ItemMeta.equals as well is the safe choice: it never merges genuinely different meta.
        // (This costs a deep meta compare on the sort path, which is an acceptable price for correctness.)
        return durability == sortKey.durability && material == sortKey.material && Objects.equals(meta, sortKey.meta)
                && metaStr.equals(sortKey.metaStr) && sortPrefix.equals(sortKey.sortPrefix);
    }

    @Override
    public int hashCode() {
        // Must mirror equals: include meta so two SortKeys that differ only by ItemMeta land in different
        // buckets and are never merged. See the warning in equals before changing this.
        int result = sortPrefix.hashCode();
        result = 31 * result + material.hashCode();
        result = 31 * result + durability;
        result = 31 * result + metaStr.hashCode();
        result = 31 * result + (meta != null ? meta.hashCode() : 0);
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
