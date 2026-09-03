package net.kccricket.clicksorted.text;

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

import net.kccricket.clicksorted.ClickSortedPlugin;
import net.kccricket.clicksorted.model.PreferenceResult;

import net.kyori.adventure.text.Component;

import org.bukkit.entity.Player;

/**
 * Reports a cancelled {@link PreferenceResult} to the player: the cancelling listener's
 * {@link PreferenceResult#cancelReason()} if it set one, otherwise the generic
 * {@code preferenceChangeBlocked} lang key. Split out of the old {@code text.MessageUtil} when that
 * class was replaced by KcMcLib's {@link net.kccricket.kcmclib.text.Messenger} — this one method
 * stays plugin-local because it's hard-coupled to {@link PreferenceResult}, a type the shared
 * library has no reason to know about. Callers should only invoke this when
 * {@link PreferenceResult#cancelled()} is {@code true}.
 */
public final class PreferenceMessages {

    private PreferenceMessages() {}

    public static void preferenceBlocked(ClickSortedPlugin plugin, Player player, PreferenceResult result) {
        Component reason = result.cancelReason();
        if (reason != null) {
            plugin.messages().to(player).error().send(reason);
        } else {
            plugin.messages().to(player).error().send("preferenceChangeBlocked");
        }
    }
}
