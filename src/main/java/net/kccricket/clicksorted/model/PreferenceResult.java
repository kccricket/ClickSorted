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

import net.kccricket.clicksorted.events.PlayerPreferenceChangeEvent;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The outcome of a {@link PlayerSortingPrefs} mutator call, distinguishing three cases a caller
 * needs to report differently: the value was actually changed and persisted ({@link #APPLIED}),
 * the requested value already matched the stored one so nothing happened ({@link #UNCHANGED}), or
 * a {@link PlayerPreferenceChangeEvent} listener vetoed the change ({@link Outcome#CANCELLED}).
 */
public record PreferenceResult(Outcome outcome, @Nullable Component cancelReason) {
    public enum Outcome { APPLIED, UNCHANGED, CANCELLED }

    public static final PreferenceResult APPLIED = new PreferenceResult(Outcome.APPLIED, null);
    public static final PreferenceResult UNCHANGED = new PreferenceResult(Outcome.UNCHANGED, null);

    /** A listener cancelled the change; {@code reason} is the listener's explanation, or {@code null} if none was given. */
    public static PreferenceResult cancelled(@Nullable Component reason) {
        return new PreferenceResult(Outcome.CANCELLED, reason);
    }

    public boolean applied() {
        return outcome == Outcome.APPLIED;
    }

    public boolean cancelled() {
        return outcome == Outcome.CANCELLED;
    }
}
