package me.desht.clicksort;

/*
 This file is part of ClickSort

 ClickSort is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 ClickSort is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with ClickSort.  If not, see <http://www.gnu.org/licenses/>.
 */

import me.desht.dhutils.ItemNames;
import me.desht.dhutils.LogUtils;
import org.bukkit.inventory.ItemStack;

public enum SortingMethod {
    NAME, GROUP;

    public SortingMethod cycle() {
        return values()[(ordinal() + 1) % values().length];
    }

    public boolean isAvailable() {
        return switch (this) {
            case GROUP -> ClickSortPlugin.getInstance().getItemGrouping().isAvailable();
            default -> true;
        };
    }

    public String makeSortPrefix(ItemStack stack) {
        switch (this) {
            case NAME:
                String name = ItemNames.lookup(stack);
                return name;
            case GROUP:
                String grp = ClickSortPlugin.getInstance().getItemGrouping().getGroup(stack);
                return String.format("%s-%s", grp, stack.getType());
            default:
                return "";
        }
    }

    public static final SortingMethod DEFAULT = NAME;

    public static SortingMethod parse(String sortingMethod) {
        ClickSortPlugin inst = ClickSortPlugin.getInstance();
        return parse(sortingMethod, inst != null ? inst.getDefaultSortingMethod() : DEFAULT);
    }

    public static SortingMethod parse(String sortingMethod, SortingMethod defaultMethod) {
        try {
            return SortingMethod.valueOf(sortingMethod);
        } catch (IllegalArgumentException e) {
            LogUtils.warning("invalid sort method " + sortingMethod + " - default to " + defaultMethod);
            return defaultMethod;
        }
    }
}
