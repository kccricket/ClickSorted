package net.kccricket.clicksort;

/*
 * This file is part of ClickSort
 *
 * ClickSort is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * ClickSort is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with ClickSort. If not, see <http://www.gnu.org/licenses/>.
 */

import net.kccricket.clicksort.commands.ClickSortCommands;
import net.kccricket.clicksort.config.ConfigManager;
import net.kccricket.clicksort.logging.Log;
import net.kccricket.clicksort.model.PlayerSortingPrefs;
import net.kccricket.clicksort.sort.InventoryClickListener;
import net.kccricket.clicksort.sort.InventorySortService;
import net.kccricket.clicksort.sort.PrefsCycleHandler;
import net.kccricket.clicksort.text.CooldownMessenger;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ClickSortPlugin extends JavaPlugin {
    private final CooldownMessenger messenger = new CooldownMessenger();
    private Metrics metrics;
    private PlayerSortingPrefs sortingPrefs;
    private ConfigManager configManager;

    private static ClickSortPlugin instance = null;

    @Override
    public void onEnable() {
        instance = this;

        Log.init(this);

        configManager = new ConfigManager(this);
        configManager.loadAll();

        if (getConfig().getBoolean("enable_metrics", true)) {
            metrics = new Metrics(this, 9432);
        }

        sortingPrefs = new PlayerSortingPrefs(this);

        InventorySortService sortService = new InventorySortService(this);
        PrefsCycleHandler cycleHandler = new PrefsCycleHandler(this);

        PluginManager pm = this.getServer().getPluginManager();
        pm.registerEvents(new InventoryClickListener(this, sortService, cycleHandler), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(ClickSortCommands.build(this), "Manage the ClickSort plugin"));
    }

    @Override
    public void onDisable() {
        if (metrics != null) {
            metrics.shutdown();
        }
        if (configManager != null) {
            configManager.saveAll();
        }
        instance = null;
    }

    public static ClickSortPlugin getInstance() {
        return instance;
    }

    public CooldownMessenger getMessenger() {
        return messenger;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    /** @return the sorting prefs for all players */
    public PlayerSortingPrefs getSortingPrefs() {
        return sortingPrefs;
    }
}
