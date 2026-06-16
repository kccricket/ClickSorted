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

/**
 * The grid corner where a sorted layout begins. Combined with {@link FillAxis} it determines the
 * order in which sorted items are written back into the inventory's slots.
 */
public enum StartCorner {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

    public static final StartCorner DEFAULT = TOP_LEFT;

    /** True when this corner is on the top edge (rows fill top → bottom). */
    public boolean topRow() {
        return this == TOP_LEFT || this == TOP_RIGHT;
    }

    /** True when this corner is on the left edge (columns fill left → right). */
    public boolean leftCol() {
        return this == TOP_LEFT || this == BOTTOM_LEFT;
    }

    public static StartCorner parse(String corner) {
        ClickSortedPlugin inst = ClickSortedPlugin.getInstance();
        return parse(corner, inst != null ? inst.getConfigManager().main().getDefaultStartCorner() : DEFAULT);
    }

    public static StartCorner parse(String corner, StartCorner defaultCorner) {
        if (corner == null) {
            return defaultCorner;
        }
        try {
            return StartCorner.valueOf(corner);
        } catch (IllegalArgumentException e) {
            Log.warning("invalid start corner " + corner + " - default to " + defaultCorner);
            return defaultCorner;
        }
    }
}
