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
import net.kccricket.clicksorted.logging.Log;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;

/**
 * Owns the full catalog of <em>what is stored where</em> and <em>which lineage applies</em>, and
 * exposes one entry point per store. Callers (config loader, join listener) pass only the store; the
 * loader/store has no knowledge of which settings get migrated.
 * <p>
 * The catalog has two kinds of rules per store: value-remap lineages ({@link ValueMigration}) keyed
 * by storage location (config path / PDC key name), and deprecated locations to drop outright. As a
 * setting's stored values are renamed, extend the matching lineage; as a setting is removed, add its
 * location to the deprecated list.
 */
public final class Migrations {

    /**
     * {@code defaults.click_mode} in config and the per-player {@code click} PDC key.
     * Renames the 1.0.0 {@code ClickMethod} constants to their current spellings. A future rename is
     * a pure append, e.g. {@code .rename("SINGLE").to("SINGLE_CLICK").to("SINGLE_PUNCH")}.
     */
    public static final ValueMigration CLICK_METHOD = ValueMigration.builder()
            .rename("DOUBLE").to("DOUBLE_CLICK")
            .rename("SINGLE").to("SINGLE_CLICK")
            .build();

    /** Config paths (value-remap): path → lineage. */
    private static final Map<String, ValueMigration> CONFIG = Map.of(
            "defaults.click_mode", CLICK_METHOD);

    /** Per-player PDC keys (value-remap): key name → lineage. */
    private static final Map<String, ValueMigration> PDC = Map.of(
            "click", CLICK_METHOD);

    /** Config paths for settings that have been removed entirely and should be dropped. */
    private static final List<String> DEPRECATED_CONFIG_PATHS = List.of(
            "defaults.shift_click");

    /** Per-player PDC key names for settings that have been removed entirely and should be dropped. */
    private static final List<String> DEPRECATED_PDC_KEYS = List.of(
            "shift_click");

    private final ClickSortedPlugin plugin;

    public Migrations(ClickSortedPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Applies the config catalog to {@code config} in place: rewrites renamed values and drops
     * deprecated paths. The caller is responsible for persisting the configuration afterward.
     *
     * @return true if any value was rewritten or any deprecated path was removed
     */
    public boolean migrate(ConfigurationSection config) {
        boolean changed = false;
        for (Map.Entry<String, ValueMigration> entry : CONFIG.entrySet()) {
            changed |= apply(config, entry.getKey(), entry.getValue());
        }
        for (String path : DEPRECATED_CONFIG_PATHS) {
            changed |= removePath(config, path);
        }
        return changed;
    }

    /**
     * Applies the PDC catalog to {@code player}'s persistent data in place: rewrites renamed values
     * and drops deprecated keys. Invoked once per session on join; a no-op when nothing needs
     * migrating.
     */
    public void migrate(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        for (Map.Entry<String, ValueMigration> entry : PDC.entrySet()) {
            apply(pdc, new NamespacedKey(plugin, entry.getKey()), entry.getValue());
        }
        for (String name : DEPRECATED_PDC_KEYS) {
            removeKey(pdc, new NamespacedKey(plugin, name));
        }
    }

    // -------------------------------------------------------------------------
    // Per-location rewrite / removal helpers
    // -------------------------------------------------------------------------

    /**
     * Rewrites a legacy value stored under {@code key} in {@code pdc} to its canonical form.
     *
     * @return true if a value was present and rewritten
     */
    private boolean apply(PersistentDataContainer pdc, NamespacedKey key, ValueMigration migration) {
        String current = pdc.get(key, PersistentDataType.STRING);
        String migrated = migration.migrate(current);
        if (migrated != null && !migrated.equals(current)) {
            pdc.set(key, PersistentDataType.STRING, migrated);
            Log.debug("migrated PDC " + key.getKey() + ": " + current + " -> " + migrated);
            return true;
        }
        return false;
    }

    /**
     * Rewrites a legacy value stored at {@code path} in {@code config} to its canonical form.
     *
     * @return true if a value was present and rewritten
     */
    private boolean apply(ConfigurationSection config, String path, ValueMigration migration) {
        String current = config.getString(path);
        String migrated = migration.migrate(current);
        if (migrated != null && !migrated.equals(current)) {
            config.set(path, migrated);
            Log.debug("migrated config " + path + ": " + current + " -> " + migrated);
            return true;
        }
        return false;
    }

    /**
     * Drops a deprecated {@code key} from {@code pdc} if present.
     *
     * @return true if the key was present and removed
     */
    private boolean removeKey(PersistentDataContainer pdc, NamespacedKey key) {
        if (pdc.getKeys().contains(key)) {
            pdc.remove(key);
            Log.debug("removed deprecated PDC key " + key.getKey());
            return true;
        }
        return false;
    }

    /**
     * Drops a deprecated {@code path} from {@code config} if present.
     *
     * @return true if the path was present and removed
     */
    private boolean removePath(ConfigurationSection config, String path) {
        if (config.contains(path)) {
            config.set(path, null);
            Log.debug("removed deprecated config path " + path);
            return true;
        }
        return false;
    }
}
