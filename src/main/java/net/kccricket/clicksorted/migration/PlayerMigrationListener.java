package net.kccricket.clicksorted.migration;

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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Runs per-player data migrations once, when a player joins. Player preferences live in the PDC, so
 * join is the single point at which a returning player's stored values are brought up to date — no
 * read path needs to tolerate legacy spellings.
 */
public class PlayerMigrationListener implements Listener {

    private final ClickSortedPlugin plugin;

    public PlayerMigrationListener(ClickSortedPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getMigrations().migrate(event.getPlayer());
    }
}
