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

import net.kccricket.clicksorted.logging.Log;

/**
 * Shared, null-tolerant parsing of a stored string into an enum constant. Centralizes the
 * "null → default, unknown → warn + default" behavior that every player-preference enum
 * ({@link SortingMethod}, {@link ClickMethod}, {@link StartCorner}, {@link FillAxis}) needs, so the
 * fallback semantics and the warning message stay identical across all of them.
 */
final class EnumParse {

    private EnumParse() {
    }

    /**
     * @param type        the enum class to resolve {@code raw} against
     * @param raw         the stored value (may be {@code null} or unknown)
     * @param defaultValue the value to fall back to when {@code raw} is {@code null} or not a constant
     * @return the matching constant, or {@code defaultValue} (with a warning) when it cannot be resolved
     */
    static <E extends Enum<E>> E parse(Class<E> type, String raw, E defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException e) {
            Log.warning("invalid " + type.getSimpleName() + " '" + raw + "' - default to " + defaultValue);
            return defaultValue;
        }
    }
}
