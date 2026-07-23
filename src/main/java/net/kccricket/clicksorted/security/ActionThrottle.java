package net.kccricket.clicksorted.security;

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
import net.kccricket.clicksorted.text.MessageUtil;
import org.bukkit.entity.Player;

/**
 * Thin ClickSorted wrapper around the store-neutral {@link net.kccricket.kcmclib.security.ActionThrottle}:
 * supplies the bypass node and a live cooldown reader from {@code action_cooldown_ms}, and adds the
 * rate-limited {@code actionTooFast} chat notice ({@link #throttled(Player)}) that the library
 * throttle deliberately has no messaging/lang dependency to send itself.
 */
public class ActionThrottle {

    /** Permission that exempts a player from throttling. */
    public static final String BYPASS_NODE = "clicksorted.throttle.bypass";

    private final ClickSortedPlugin plugin;
    private final net.kccricket.kcmclib.security.ActionThrottle delegate;

    public ActionThrottle(ClickSortedPlugin plugin) {
        this.plugin = plugin;
        this.delegate = new net.kccricket.kcmclib.security.ActionThrottle(
                BYPASS_NODE, () -> plugin.getConfigManager().main().getActionCooldownMs());
    }

    /**
     * Record an action attempt for {@code player} and decide whether it may proceed, using the
     * configured cooldown.
     *
     * @return {@code true} if the action is allowed; {@code false} if it falls within the cooldown
     *         window and should be dropped.
     */
    public boolean allow(Player player) {
        return delegate.allow(player);
    }

    /**
     * Shared throttle gate: like {@link #allow(Player)} but, on denial, also sends the player the
     * rate-limited {@code actionTooFast} notice. Lets every call site collapse to a single check.
     *
     * @return {@code true} if the action should be dropped (and the notice was sent);
     *         {@code false} if it may proceed.
     */
    public boolean throttled(Player player) {
        if (allow(player)) {
            return false;
        }
        plugin.getMessenger().message(player, "throttle", 3,
                MessageUtil.withPrefix(player,
                        plugin.getConfigManager().lang(player.locale()).getColoredMessage("actionTooFast")));
        return true;
    }
}
