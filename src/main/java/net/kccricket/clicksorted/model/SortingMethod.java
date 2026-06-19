package net.kccricket.clicksorted.model;

/*
 This file is part of ClickSorted

 ClickSorted is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 ClickSorted is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with ClickSorted.  If not, see <http://www.gnu.org/licenses/>.
 */

import net.kccricket.clicksorted.ClickSortedPlugin;
import net.kccricket.clicksorted.text.ItemNames;
import org.bukkit.inventory.ItemStack;

public enum SortingMethod {
    NAME, GROUP, TREEMAP;

    public boolean isAvailable() {
        return switch (this) {
            case GROUP -> ClickSortedPlugin.getInstance().getConfigManager().groups().isAvailable();
            default -> true;
        };
    }

    /**
     * @return true if this method groups items by type and packs each type into a proportional
     *         block (handled by {@link net.kccricket.clicksorted.sort.TreemapPacker}) rather than
     *         laying the sorted sequence out linearly via {@link net.kccricket.clicksorted.sort.SlotOrder}.
     */
    public boolean isTreemap() {
        return this == TREEMAP;
    }

    public String makeSortPrefix(ItemStack stack) {
        return switch (this) {
            // TREEMAP orders/merges by name; placement (not ordering) is what differs.
            case NAME, TREEMAP -> ItemNames.lookup(stack);
            case GROUP -> {
                String grp = ClickSortedPlugin.getInstance().getConfigManager().groups().getGroup(stack);
                yield String.format("%s-%s", grp, stack.getType());
            }
        };
    }

    public static final SortingMethod DEFAULT = NAME;

    public static SortingMethod parse(String sortingMethod, SortingMethod defaultMethod) {
        return EnumParse.parse(SortingMethod.class, sortingMethod, defaultMethod);
    }
}
