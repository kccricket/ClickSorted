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

/**
 * The direction a sorted layout flows from its {@link StartCorner}.
 * <ul>
 *   <li>{@code HORIZONTAL} — fill a row across, then drop to the next row.</li>
 *   <li>{@code VERTICAL} — fill a column down, then move to the next column.</li>
 * </ul>
 */
public enum FillAxis {
    HORIZONTAL, VERTICAL;

    public static final FillAxis DEFAULT = HORIZONTAL;

    public static FillAxis parse(String axis, FillAxis defaultAxis) {
        return EnumParse.parse(FillAxis.class, axis, defaultAxis);
    }
}
