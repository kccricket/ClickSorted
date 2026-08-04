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
import net.kccricket.clicksorted.gui.DialogSupport;
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
import org.bukkit.event.HandlerList;
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
    private SelfTestListener selfTestListener;

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

        PluginManager pm = this.getServer().getPluginManager();
        pm.registerEvents(new InventoryClickListener(this, sortService), this);
        pm.registerEvents(new LockGuiListener(this), this);
        pm.registerEvents(new BlacklistGuiListener(this), this);
        pm.registerEvents(new PlayerMigrationListener(this), this);

        // Dialog API (io.papermc.paper.dialog.Dialog) post-dates this plugin's api-version floor —
        // see DialogSupport. Not constructed/registered at all on a server too old for it; /clicksorted
        // menu is separately gated the same way in ClickSortedCommands.
        if (DialogSupport.AVAILABLE) {
            preferencesDialogService = new PreferencesDialogService(this);
            pm.registerEvents(preferencesDialogService, this);
        }

        refreshSelfTest();

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(ClickSortedCommands.build(this), "Manage the ClickSorted plugin"));
    }

    /**
     * Lazily constructs/tears down the self-test subsystem ({@link SelfTestManager} +
     * {@link SelfTestListener}) to match the current {@code enable_selftest} config value — off by
     * default, since a run stages and restores the tester's real inventory, so most servers should
     * never even load this code. Called once from {@link #onEnable} and again after every
     * {@code /clicksorted admin reload} (the flag can change at runtime); idempotent when the
     * subsystem is already in the desired state.
     */
    public void refreshSelfTest() {
        boolean shouldRun = configManager.main().getEnableSelftest();
        if (shouldRun && selfTestManager == null) {
            selfTestManager = new SelfTestManager(this);
            selfTestListener = new SelfTestListener(this, selfTestManager);
            getServer().getPluginManager().registerEvents(selfTestListener, this);
        } else if (!shouldRun && selfTestManager != null) {
            selfTestManager.abortAll();
            HandlerList.unregisterAll(selfTestListener);
            selfTestListener = null;
            selfTestManager = null;
        }
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

    /**
     * @return the stash/restore service backing the {@code /clicksorted menu} preferences dialog,
     *         or {@code null} on a server without the Dialog API ({@link DialogSupport#AVAILABLE}
     *         false) — every caller of this getter is itself only reachable from inside an
     *         already-open dialog, which can't happen on such a server since {@code menu} is gated
     *         the same way.
     */
    public PreferencesDialogService getPreferencesDialogService() {
        return preferencesDialogService;
    }

    /**
     * @return the self-test manager, or {@code null} when {@code enable_selftest} is off (the
     *         default) — see {@link #refreshSelfTest()}. Every {@code /clicksorted admin selftest}
     *         subcommand is gated behind the same flag (see {@code canSelftest} in
     *         {@code ClickSortedCommands}), so this is only reachable non-null there; callers
     *         outside that gated subtree must null-check.
     */
    public SelfTestManager getSelfTestManager() {
        return selfTestManager;
    }
}
