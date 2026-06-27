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

import net.kccricket.clicksorted.text.ItemNames;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * Immutable snapshot of a player's bundle blacklist: materials and display names that must not be
 * packed into or unpacked from bundles during sorting.
 *
 * <p>A material entry blocks any item of that type regardless of its name. A name entry blocks
 * any item whose resolved name (plain-text, color-stripped) exactly matches, following the precedence
 * custom name → item name (data-pack/plugin base name) → vanilla / {@code items.yml} name (see
 * {@link ItemNames#lookup}), so a name entry can target renamed items, data-pack-named items, and
 * plain vanilla ones.
 */
public record BundleBlacklist(Set<Material> materials, Set<String> names) {

    /** A blacklist that blocks nothing. Use instead of {@code null} when no blacklist is configured. */
    public static final BundleBlacklist EMPTY = new BundleBlacklist(Set.of(), Set.of());

    /**
     * Returns {@code true} if {@code is} is blocked by this blacklist and should not be packed into
     * or unpacked from a bundle.
     *
     * @param is the item to test; must not be null
     */
    public boolean blocks(ItemStack is) {
        if (materials.contains(is.getType())) {
            return true;
        }
        if (!names.isEmpty()) {
            String name = ItemNames.lookup(is);
            return name != null && names.contains(name);
        }
        return false;
    }
}
