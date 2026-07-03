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
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Global per-player rate limiter. A single transient cooldown clock per player gates <em>every</em>
 * plugin-driven action (sort, bundle-pack, prefs cycle, lock toggles, commands), capping how fast a
 * scripted or hacked client can force main-thread work regardless of which feature is invoked.
 *
 * <p>The minimum interval between actions is read from {@code action_cooldown_ms} on each call, so
 * {@code /clicksorted reload} takes effect live. A value ≤ 0 disables throttling entirely. Players
 * holding {@code clicksorted.throttle.bypass} (default op) are never throttled.
 *
 * <p>State is in-memory only — never persisted. The map is keyed by {@link UUID} so it is unaffected
 * by name changes and bounded by the online player count in practice.
 */
public class ActionThrottle {

    /** Permission that exempts a player from throttling. */
    public static final String BYPASS_NODE = "clicksorted.throttle.bypass";

    private final ClickSortedPlugin plugin;
    private final Map<UUID, Long> lastAction = new ConcurrentHashMap<>();

    /** Time source, overridable in tests to avoid real sleeps. */
    private LongSupplier clock = System::currentTimeMillis;

    public ActionThrottle(ClickSortedPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Record an action attempt for {@code player} and decide whether it may proceed, using the
     * configured cooldown.
     *
     * @return {@code true} if the action is allowed (and the player's clock is advanced);
     *         {@code false} if it falls within the cooldown window and should be dropped.
     */
    public boolean allow(Player player) {
        return allow(player, plugin.getConfigManager().main().getActionCooldownMs());
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
                plugin.getConfigManager().lang().getColoredMessage(player.locale(), "actionTooFast"));
        return true;
    }

    /**
     * Core throttle decision against an explicit cooldown. Exposed (package-private) for tests.
     *
     * <p>On denial the player's timestamp is intentionally <em>not</em> updated, so a sustained flood
     * does not keep sliding the window forward — the next allowed action is still measured from the
     * last action that actually ran.
     */
    boolean allow(Player player, int cooldownMs) {
        if (cooldownMs <= 0) {
            return true;
        }
        if (Permissions.isAllowedTo(player, BYPASS_NODE)) {
            return true;
        }
        long now = clock.getAsLong();
        // Atomic check-then-act: a single compute() decides and conditionally advances the
        // window so concurrent callers for the same player can't both pass the gate.
        AtomicBoolean allowed = new AtomicBoolean(false);
        lastAction.compute(player.getUniqueId(), (id, last) -> {
            if (last == null || now - last >= cooldownMs) {
                allowed.set(true);
                return now;          // allowed: advance the window
            }
            return last;             // denied: leave the timestamp untouched
        });
        return allowed.get();
    }

    /** Test seam: override the time source. */
    void setClock(LongSupplier clock) {
        this.clock = clock;
    }
}
