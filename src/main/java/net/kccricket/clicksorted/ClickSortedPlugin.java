package net.kccricket.clicksorted;

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

import net.kccricket.clicksorted.commands.ClickSortedCommands;
import net.kccricket.clicksorted.config.ConfigManager;
import net.kccricket.clicksorted.gui.LockGuiListener;
import net.kccricket.clicksorted.logging.Log;
import net.kccricket.clicksorted.model.PlayerSortingPrefs;
import net.kccricket.clicksorted.sort.InventoryClickListener;
import net.kccricket.clicksorted.sort.InventorySortService;
import net.kccricket.clicksorted.sort.PrefsCycleHandler;
import net.kccricket.clicksorted.text.CooldownMessenger;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ClickSortedPlugin extends JavaPlugin {
    private final CooldownMessenger messenger = new CooldownMessenger();
    private Metrics metrics;
    private PlayerSortingPrefs sortingPrefs;
    private ConfigManager configManager;
    private InventorySortService sortService;

    private static ClickSortedPlugin instance = null;

    @Override
    public void onEnable() {
        instance = this;

        Log.init(this);

        configManager = new ConfigManager(this);
        configManager.loadAll();

        if (getConfig().getBoolean("enable_metrics", true)) {
            metrics = new Metrics(this, 31833);
        }

        sortingPrefs = new PlayerSortingPrefs(this);

        sortService = new InventorySortService(this);
        PrefsCycleHandler cycleHandler = new PrefsCycleHandler(this);

        PluginManager pm = this.getServer().getPluginManager();
        pm.registerEvents(new InventoryClickListener(this, sortService, cycleHandler), this);
        pm.registerEvents(new LockGuiListener(this), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(ClickSortedCommands.build(this), "Manage the ClickSorted plugin"));
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

    public static ClickSortedPlugin getInstance() {
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

    public InventorySortService getSortService() {
        return sortService;
    }
}
