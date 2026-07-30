package net.kccricket.clicksorted.gui;

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
import net.kccricket.clicksorted.model.PendingPrefs;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds unsaved {@link PreferencesDialog} edits across a "Locked Slots…" / "Bundle Blacklist…"
 * button round trip: those buttons stash the player's in-progress scalar edits here before opening
 * the corresponding inventory GUI, and this listener re-shows the dialog — seeded from the stash —
 * once that GUI closes, so nothing the player already typed/toggled is lost. A directly-opened
 * lock/blacklist GUI (e.g. via {@code /clicksorted lock-slots}) has no stash entry and is untouched.
 *
 * <p>The stash is a {@link ConcurrentHashMap} (keyed by player UUID) rather than instance state on
 * any single inventory, matching this plugin's existing Folia-safety pattern (see
 * {@link net.kccricket.clicksorted.security.ActionThrottle}): two players in different regions
 * stashing/restoring concurrently cannot corrupt each other's state.
 */
public class PreferencesDialogService implements Listener {

    private final ClickSortedPlugin plugin;
    private final Map<UUID, PendingPrefs> stash = new ConcurrentHashMap<>();

    public PreferencesDialogService(ClickSortedPlugin plugin) {
        this.plugin = plugin;
    }

    /** Stashes {@code prefs} for {@code player}, overwriting any previous stash entry. */
    public void stash(Player player, PendingPrefs prefs) {
        stash.put(player.getUniqueId(), prefs);
    }

    /** Discards any stashed edits for {@code player} without re-showing the dialog. */
    public void clearStash(Player player) {
        stash.remove(player.getUniqueId());
    }

    /** @return true if {@code player} currently has unsaved dialog edits stashed */
    public boolean hasStash(Player player) {
        return stash.containsKey(player.getUniqueId());
    }

    /**
     * When a lock/blacklist GUI opened via the preferences dialog closes, re-show the dialog one
     * tick later (on the player's region scheduler, so this is safe on Folia) seeded from the
     * stashed edits. A GUI with no stash entry (opened directly via command) is left alone.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof LockGuiHolder) && !(holder instanceof BlacklistGuiHolder)) {
            return;
        }
        PendingPrefs pending = stash.remove(player.getUniqueId());
        if (pending == null) {
            return;
        }
        player.getScheduler().run(plugin, task -> PreferencesDialog.open(plugin, player, pending), null);
    }

    /**
     * Avoid leaking a stash entry across sessions if the player disconnects mid-flow. Also clears
     * this player's {@link net.kccricket.kcmclib.text.Messenger} rate-limit state, so that map
     * doesn't grow for the lifetime of the server.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        stash.remove(event.getPlayer().getUniqueId());
        plugin.messages().forget(event.getPlayer());
    }
}
