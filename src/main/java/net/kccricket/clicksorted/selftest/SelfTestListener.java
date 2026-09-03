package net.kccricket.clicksorted.selftest;

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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Optional;

/**
 * The LIVE-phase event hook: watches for the exact real-client gesture a running session is
 * waiting for and evaluates it. Registered permanently in {@code onEnable} (no dynamic
 * register/unregister — this codebase has no precedent for that, and it sidesteps any question of
 * {@code HandlerList} mutation safety on Folia); every handler no-ops immediately for a player with
 * no running session, so there is no cost when the feature is idle.
 *
 * <p>Runs at {@link EventPriority#MONITOR}, strictly after {@code InventoryClickListener}'s
 * {@link EventPriority#HIGHEST} — so by the time this fires, the production sort (if any) has
 * already happened and the inventory reflects its result.
 */
public final class SelfTestListener implements Listener {

    private final ClickSortedPlugin plugin;
    private final SelfTestManager manager;

    public SelfTestListener(ClickSortedPlugin plugin, SelfTestManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        SelfTestSession session = manager.sessionFor(player);
        if (session == null || session.phase != SelfTestSession.Phase.LIVE) {
            return;
        }
        LiveCycle cycle = session.currentLiveCycle();
        if (cycle == null) {
            return;
        }
        try {
            if (!cycle.matches(event, player, session)) {
                return;
            }
            Optional<String> failure = cycle.evaluate(event, plugin, player, session);
            manager.recordLiveResult(session, failure.isEmpty(), failure.orElse(null));
        } catch (RuntimeException | LinkageError e) {
            // Same reasoning as SelfTestRunner.runOne: a version incompatibility can surface here as a
            // LinkageError, and reporting it as a failed cycle (not letting it crash the listener) is
            // what makes the self-test diagnose a version break instead of being one.
            manager.recordLiveResult(session, false, "threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Restores an orphaned backup from an interrupted run before the player can touch anything. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (SelfTestSession.restoreOrphanedBackup(plugin, event.getPlayer())) {
            plugin.messages().to(event.getPlayer()).status().send("selfTestBackupRestored");
        }
    }

    /** Avoids leaking a running session (and its forced test settings) across the player's session. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.abortSilently(event.getPlayer());
    }
}
