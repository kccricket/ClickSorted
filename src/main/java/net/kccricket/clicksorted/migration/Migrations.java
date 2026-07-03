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
import net.kccricket.clicksorted.config.MainConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static net.kccricket.clicksorted.migration.Migration.*;

/**
 * Owns the full catalog of <em>what is stored where</em> and <em>which rules apply</em>, and
 * exposes one entry point per store. Callers pass only the store; the loader/store has no
 * knowledge of which settings get migrated.
 *
 * <h3>Rule hierarchy</h3>
 * <ol>
 *   <li><b>File migrations</b> ({@link FileMigration}) — relocate or rewrite whole files in the
 *       data folder, run once at plugin enable via {@link #migrateFiles()}, <em>before</em> any
 *       config file is loaded. Adding a future file migration is a one-line append to
 *       {@link #FILE_MIGRATIONS}. Kept as a separate dimension from the config/PDC store rules
 *       below because it operates on the filesystem, not a {@link Store}.</li>
 *   <li><b>Config-only structural transforms</b> ({@link ConfigTransform}) — derive new config
 *       state from old values in place before any removal pass. Adding a future transform is a
 *       one-line append to {@link #CONFIG_TRANSFORMS}.</li>
 *   <li><b>Config-only root-path removal</b> — drop root-level keys that have been removed
 *       from the schema (e.g. {@code player_sort_min}). Source-key removal for structural
 *       transforms belongs here, not inside the transform itself.</li>
 *   <li><b>Shared rules</b> ({@link Migration}) — applied to both config (via {@link Store.ConfigStore},
 *       namespace {@code defaults.*}) and player PDC (via {@link Store.PdcStore}). Key renames run
 *       first so that value-remap and conditional rules see the renamed keys. Because PDC leaf names
 *       now match config-default leaf names (both use e.g. {@code click_mode}), a single declared
 *       rule covers both stores with no per-key mapping table.</li>
 * </ol>
 *
 * <h3>NONE migration</h3>
 * {@code ClickMethod.NONE} (disabled state) has been replaced by a dedicated boolean
 * {@code enabled} preference. A player or admin with {@code click_mode=NONE} is migrated to
 * {@code enabled=false} and {@code click_mode=SWAP}. This is expressed as a pure data rule in
 * {@link #SHARED} and runs symmetrically on both stores.
 */
public final class Migrations {

    /**
     * A self-contained structural config migration: derives new config state from old values,
     * in place. Returns {@code true} if anything was changed.
     *
     * <p>Source-key removal is <strong>not</strong> the transform's responsibility — list the
     * old paths in {@link #DEPRECATED_ROOT_PATHS} so the removal pass cleans them up after
     * the transforms have already read them.
     */
    @FunctionalInterface
    interface ConfigTransform {
        boolean apply(ConfigurationSection config);
    }

    /**
     * {@code defaults.click_mode} in config and the per-player {@code click_mode} PDC key.
     * Renames the 1.0.0 {@code ClickMethod} constants to their current spellings.
     */
    public static final ValueMigration CLICK_METHOD = ValueMigration.builder()
            .rename("DOUBLE").to("DOUBLE_CLICK")
            .rename("SINGLE").to("SINGLE_CLICK")
            .build();

    /**
     * {@code defaults.start_corner} in config and the per-player {@code start_corner} PDC key.
     * No historical aliases yet; extend with {@code .rename(old).to(new)} if a constant is renamed.
     */
    public static final ValueMigration START_CORNER = ValueMigration.builder().build();

    /**
     * {@code defaults.fill_axis} in config and the per-player {@code fill_axis} PDC key.
     * No historical aliases yet; extend with {@code .rename(old).to(new)} if a constant is renamed.
     */
    public static final ValueMigration FILL_AXIS = ValueMigration.builder().build();

    /**
     * File-level migrations, applied once at enable via {@link #migrateFiles()}, before any config
     * file loads. To add a future file migration, append here.
     *
     * <p>{@code lang.yml} (the pre-i18n single-file lang store) relocates to the sparse
     * per-locale override {@code lang/en_us.yml}: only keys an admin actually changed survive the
     * move (unchanged keys fall back to the plugin's bundled default and track future updates to
     * it), and the original is kept alongside as {@code lang.yml.bak}.
     */
    private static final List<FileMigration> FILE_MIGRATIONS = List.of(
            FileMigration.extractChangedKeys("lang.yml", "lang/en_us.yml", "lang/en_us.yml", ".bak"));

    /**
     * Structural config transforms, applied in order before root-path removal and shared rules.
     * To add a future structural migration, append here.
     */
    private static final List<ConfigTransform> CONFIG_TRANSFORMS = List.of(
            Migrations::migrateSortBounds);

    /**
     * Root-level config paths to drop. These are not in {@code defaults.*} so they fall outside
     * the {@link Store.ConfigStore} namespace and are handled separately.
     */
    private static final List<String> DEPRECATED_ROOT_PATHS = List.of(
            "player_sort_min",
            "player_sort_max");

    /**
     * Shared migration rules, applied in order to both config ({@link Store.ConfigStore}) and PDC
     * ({@link Store.PdcStore}). Order encodes the data dependency:
     * <ol>
     *   <li>Key renames first — so legacy PDC {@code click=DOUBLE} becomes {@code click_mode=DOUBLE}
     *       before the remap turns it into {@code DOUBLE_CLICK}.</li>
     *   <li>Value remaps — canonical-name enforcement for each renamed setting.</li>
     *   <li>Conditional branch — {@code NONE} → {@code enabled=false, click_mode=SWAP}.
     *       {@code NONE} is intentionally absent from the {@link #CLICK_METHOD} lineage so the remap
     *       does not rewrite it before this condition can observe it.</li>
     *   <li>Deprecated-key removal last.</li>
     * </ol>
     */
    private static final List<Migration> SHARED = List.of(
            renameKey("click",            "click_mode"),         // PDC-only in practice; no-op on config
            renameKey("sort",             "sort_mode"),           // PDC-only in practice; no-op on config
            renameBooleanKey("bundle_inventory", "bundle_in_inventory"), // PDC-only in practice; no-op on config
            renameBooleanKey("bundle_others",    "bundle_in_containers"), // PDC-only in practice; no-op on config
            remap("click_mode",      CLICK_METHOD),
            remap("start_corner",    START_CORNER),
            remap("fill_axis",       FILL_AXIS),
            when("click_mode").is("NONE").then(set("enabled", false), set("click_mode", "SWAP")),
            remove("shift_click"));

    private final ClickSortedPlugin plugin;

    public Migrations(ClickSortedPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Applies the full config catalog to {@code config} in place, in three passes:
     * <ol>
     *   <li>Structural transforms (read old keys before removal).</li>
     *   <li>Deprecated root-path removal (source keys for structural transforms).</li>
     *   <li>Shared rules over {@code defaults.*}.</li>
     * </ol>
     * The caller is responsible for persisting the configuration afterward.
     *
     * @return true if any value was rewritten, any deprecated path was removed, or any
     *         structural transform made a change
     */
    public boolean migrate(ConfigurationSection config) {
        try {
            boolean changed = false;
            for (ConfigTransform transform : CONFIG_TRANSFORMS) {
                changed |= transform.apply(config);
            }
            for (String path : DEPRECATED_ROOT_PATHS) {
                changed |= removePath(config, path);
            }
            changed |= run(SHARED, new Store.ConfigStore(config));
            return changed;
        } catch (RuntimeException e) {
            throw new MigrationException("Config migration failed", e);
        }
    }

    /**
     * Applies the shared catalog to {@code player}'s persistent data in place. Invoked once per
     * session on join; a no-op when nothing needs migrating.
     */
    public void migrate(Player player) {
        run(SHARED, new Store.PdcStore(player.getPersistentDataContainer(), plugin));
    }

    /**
     * Runs the {@link #FILE_MIGRATIONS} catalog against the plugin's data folder. Called once
     * from {@code onEnable}, before {@code ConfigManager#loadAll()} so relocated files (e.g.
     * {@code lang/en_us.yml}) exist by the time their owning config loads.
     *
     * <p>Best-effort: a failure in one migration is logged and does not prevent the others (or
     * plugin enable) from proceeding, since a file-migration hiccup should never brick a server.
     */
    public void migrateFiles() {
        FileMigrationContext ctx = new PluginFileMigrationContext(plugin);
        for (FileMigration migration : FILE_MIGRATIONS) {
            try {
                if (migration.apply(ctx)) {
                    Log.debug("Applied file migration: " + migration);
                }
            } catch (IOException e) {
                Log.warning("File migration failed: " + e.getMessage(), e);
            }
        }
    }

    /** Adapts {@link ClickSortedPlugin} to the store-neutral {@link FileMigrationContext}. */
    private record PluginFileMigrationContext(ClickSortedPlugin plugin) implements FileMigrationContext {
        @Override
        public Path dataFolder() {
            return plugin.getDataFolder().toPath();
        }

        @Override
        public InputStream resource(String path) {
            return plugin.getResource(path);
        }
    }

    // -------------------------------------------------------------------------
    // Structural transform implementations
    // -------------------------------------------------------------------------

    /**
     * Translates legacy {@code player_sort_min} / {@code player_sort_max} into equivalent
     * {@code locked_slots.player} entries so that any customised sort window is preserved.
     */
    private static boolean migrateSortBounds(ConfigurationSection config) {
        boolean hasSortMin = config.contains("player_sort_min");
        boolean hasSortMax = config.contains("player_sort_max");
        if (!hasSortMin && !hasSortMax) {
            return false;
        }

        int min = Math.max(0, Math.min(config.getInt("player_sort_min", 9), MainConfig.PLAYER_STORAGE_END));
        int max = Math.max(0, Math.min(config.getInt("player_sort_max", MainConfig.PLAYER_STORAGE_END), MainConfig.PLAYER_STORAGE_END));

        List<Integer> excluded = new ArrayList<>();
        for (int i = 9; i < min; i++) excluded.add(i);
        for (int i = max; i < MainConfig.PLAYER_STORAGE_END; i++) excluded.add(i);

        if (excluded.isEmpty()) {
            return false;
        }

        Set<Integer> merged = new LinkedHashSet<>(config.getIntegerList("locked_slots.player"));
        merged.addAll(excluded);
        List<Integer> sorted = new ArrayList<>(merged);
        sorted.sort(Integer::compareTo);
        config.set("locked_slots.player", sorted);

        Log.debug("migrateSortBounds: player_sort_min=" + min + ", player_sort_max=" + max
                + " → locked " + sorted);
        return true;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean run(List<Migration> migrations, Store store) {
        boolean changed = false;
        for (Migration m : migrations) {
            changed |= m.apply(store);
        }
        return changed;
    }

    private boolean removePath(ConfigurationSection config, String path) {
        if (config.contains(path)) {
            config.set(path, null);
            Log.debug("removed deprecated config path " + path);
            return true;
        }
        return false;
    }
}
