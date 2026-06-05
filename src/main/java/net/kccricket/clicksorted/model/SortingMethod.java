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
import net.kccricket.clicksorted.logging.Log;
import net.kccricket.clicksorted.text.ItemNames;
import org.bukkit.inventory.ItemStack;

public enum SortingMethod {
    NAME, GROUP;

    public SortingMethod cycle() {
        return values()[(ordinal() + 1) % values().length];
    }

    public boolean isAvailable() {
        return switch (this) {
            case GROUP -> ClickSortedPlugin.getInstance().getConfigManager().groups().isAvailable();
            default -> true;
        };
    }

    public String makeSortPrefix(ItemStack stack) {
        return switch (this) {
            case NAME -> ItemNames.lookup(stack);
            case GROUP -> {
                String grp = ClickSortedPlugin.getInstance().getConfigManager().groups().getGroup(stack);
                yield String.format("%s-%s", grp, stack.getType());
            }
            default -> "";
        };
    }

    public static final SortingMethod DEFAULT = NAME;

    public static SortingMethod parse(String sortingMethod) {
        ClickSortedPlugin inst = ClickSortedPlugin.getInstance();
        return parse(sortingMethod, inst != null ? inst.getConfigManager().main().getDefaultSortingMethod() : DEFAULT);
    }

    public static SortingMethod parse(String sortingMethod, SortingMethod defaultMethod) {
        try {
            return SortingMethod.valueOf(sortingMethod);
        } catch (IllegalArgumentException e) {
            Log.warning("invalid sort method " + sortingMethod + " - default to " + defaultMethod);
            return defaultMethod;
        }
    }
}
