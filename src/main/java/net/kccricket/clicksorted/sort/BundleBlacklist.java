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

import org.bukkit.inventory.ItemStack;

/**
 * Immutable snapshot of a player's bundle blacklist: materials and display names that must not be
 * packed into or unpacked from bundles during sorting.
 *
 * <p>A material entry blocks any item of that type regardless of its name. A name entry blocks
 * any item whose resolved name (plain-text, color-stripped) matches case-insensitively, following
 * the precedence custom name → item name (data-pack/plugin base name) → vanilla / {@code items.yml}
 * name (see {@link net.kccricket.clicksorted.text.ItemNames#lookup}), so a name entry can target
 * renamed items, data-pack-named items, and plain vanilla ones.
 *
 * <p>Name matching is case-insensitive. The {@link MaterialNameSet} backing this record stores names
 * pre-lowercased; callers must lowercase names before construction (see the construction sites in
 * {@code InventorySortService}).
 */
public record BundleBlacklist(MaterialNameSet set) {

    /** A blacklist that blocks nothing. Use instead of {@code null} when no blacklist is configured. */
    public static final BundleBlacklist EMPTY = new BundleBlacklist(MaterialNameSet.EMPTY);

    /**
     * Returns {@code true} if {@code is} is blocked by this blacklist and should not be packed into
     * or unpacked from a bundle.
     *
     * @param is the item to test; must not be null
     */
    public boolean blocks(ItemStack is) {
        return set.contains(is);
    }
}
