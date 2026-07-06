package net.kccricket.clicksorted.model;

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

/**
 * A snapshot of the scalar preferences shown in the preferences dialog, either read from a
 * player's live prefs (to seed a fresh dialog) or extracted from a submitted/in-progress
 * {@code DialogResponseView} (to apply on Save, or to stash across a GUI-launch button round trip).
 * Every field is nullable: {@code null} means "this input wasn't present" — either because the
 * viewing player lacked the matching permission, or (for a stash) because the value was never set.
 */
public record PendingPrefs(
        Boolean enabled,
        ClickMethod clickMethod,
        SortingMethod sortingMethod,
        StartCorner startCorner,
        FillAxis fillAxis,
        Boolean sortOverItems,
        Boolean bundleInInventory,
        Boolean bundleInContainers,
        Integer bundleStackLimit) {
}
