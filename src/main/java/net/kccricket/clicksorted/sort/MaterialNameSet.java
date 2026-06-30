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

import java.util.Locale;
import java.util.Set;

/**
 * Immutable set of materials and display names used for matching items in both
 * {@link BundleBlacklist} and {@link ProtectedItems}. Name matching is case-insensitive:
 * {@code namesLower} must be pre-lowercased ({@link Locale#ROOT}) by callers on construction,
 * and resolved names are lowercased at lookup time.
 */
public record MaterialNameSet(Set<Material> materials, Set<String> namesLower) {

    /** An empty set that matches nothing. */
    public static final MaterialNameSet EMPTY = new MaterialNameSet(Set.of(), Set.of());

    /**
     * Returns {@code true} if {@code type} is in the material set, or if {@code resolvedName}
     * (after lowercasing) is in the name set.
     *
     * @param type         the item's material
     * @param resolvedName the item's resolved plain-text name from {@link ItemNames#lookup}; may be null
     */
    public boolean contains(Material type, String resolvedName) {
        if (materials.contains(type)) return true;
        return resolvedName != null && !namesLower.isEmpty()
                && namesLower.contains(resolvedName.toLowerCase(Locale.ROOT));
    }

    /**
     * Convenience overload: resolves the item's name via {@link ItemNames#lookup} and delegates to
     * {@link #contains(Material, String)}.
     */
    public boolean contains(ItemStack is) {
        return contains(is.getType(), ItemNames.lookup(is));
    }

    /** Returns {@code true} when both sets are empty (matches nothing). */
    public boolean isEmpty() {
        return materials.isEmpty() && namesLower.isEmpty();
    }
}
