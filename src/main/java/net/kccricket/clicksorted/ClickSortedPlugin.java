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
import net.kccricket.clicksorted.gui.BlacklistGuiListener;
import net.kccricket.clicksorted.gui.LockGuiListener;
import net.kccricket.clicksorted.gui.PreferencesDialogService;
import net.kccricket.kcmclib.logging.Log;
import net.kccricket.kcmclib.migration.MigrationException;
import net.kccricket.clicksorted.migration.Migrations;
import net.kccricket.clicksorted.migration.PlayerMigrationListener;
import net.kccricket.clicksorted.model.PlayerSortingPrefs;
import net.kccricket.clicksorted.security.ActionThrottle;
import net.kccricket.clicksorted.selftest.SelfTestListener;
import net.kccricket.clicksorted.selftest.SelfTestManager;
import net.kccricket.clicksorted.sort.InventoryClickListener;
import net.kccricket.clicksorted.sort.InventorySortService;
import net.kccricket.kcmclib.text.Messenger;
import net.kccricket.kcmclib.update.ModrinthUpdateChecker;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class ClickSortedPlugin extends JavaPlugin {
    private Messenger messenger;
    private Metrics metrics;
    private PlayerSortingPrefs sortingPrefs;
    private ConfigManager configManager;
    private InventorySortService sortService;
    private ActionThrottle actionThrottle;
    private Migrations migrations;
    private ModrinthUpdateChecker updateChecker;
    private PreferencesDialogService preferencesDialogService;
    private SelfTestManager selfTestManager;

    private static ClickSortedPlugin instance = null;

    @Override
    public void onEnable() {
        instance = this;

        Log.init(this);

        migrations = new Migrations(this);
        migrations.migrateFiles();

        configManager = new ConfigManager(this);
        try {
            configManager.loadAll();
        } catch (MigrationException e) {
            Log.severe("Config migration failed; disabling ClickSorted. Fix or remove the offending "
                    + "config value, then restart.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        messenger = new Messenger(configManager);

        if (getConfig().getBoolean("enable_metrics", true)) {
            metrics = new Metrics(this, 31833);
        }

        sortingPrefs = new PlayerSortingPrefs(this);
        actionThrottle = new ActionThrottle(this);

        updateChecker = new ModrinthUpdateChecker(this, "clicksorted",
                () -> configManager.main().getCheckForUpdates(),
                () -> configManager.main().getUpdateCheckIntervalHours(),
                (latest, current) -> List.of(
                        "A new version of ClickSorted is available: " + latest
                                + " (you are running " + current + ").",
                        "Download: https://modrinth.com/plugin/clicksorted | "
                                + "https://hangar.papermc.io/kccricket/ClickSorted | "
                                + "https://github.com/kccricket/ClickSorted/releases"));
        updateChecker.restart();

        sortService = new InventorySortService(this);
        preferencesDialogService = new PreferencesDialogService(this);
        // Constructed once, never per-reload (mirrors ActionThrottle/PreferencesDialogService), so a
        // reload can't orphan a running LIVE session — see Migrations.migrate/reload handling below.
        selfTestManager = new SelfTestManager(this);

        PluginManager pm = this.getServer().getPluginManager();
        pm.registerEvents(new InventoryClickListener(this, sortService), this);
        pm.registerEvents(new LockGuiListener(this), this);
        pm.registerEvents(new BlacklistGuiListener(this), this);
        pm.registerEvents(new PlayerMigrationListener(this), this);
        pm.registerEvents(preferencesDialogService, this);
        pm.registerEvents(new SelfTestListener(this, selfTestManager), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(ClickSortedCommands.build(this), "Manage the ClickSorted plugin"));
    }

    @Override
    public void onDisable() {
        if (selfTestManager != null) {
            selfTestManager.abortAll();
        }
        if (updateChecker != null) {
            updateChecker.stop();
        }
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

    /**
     * The single entry point for sending a message to a player or console — see
     * {@link Messenger}'s class javadoc. Built in {@link #onEnable} once {@link #configManager}
     * exists (it reads lang live through it, so there is nothing to re-init on reload).
     */
    public Messenger messages() {
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

    public ActionThrottle getActionThrottle() {
        return actionThrottle;
    }

    public Migrations getMigrations() {
        return migrations;
    }

    public ModrinthUpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    /** @return the stash/restore service backing the {@code /clicksorted menu} preferences dialog */
    public PreferencesDialogService getPreferencesDialogService() {
        return preferencesDialogService;
    }

    public SelfTestManager getSelfTestManager() {
        return selfTestManager;
    }
}
