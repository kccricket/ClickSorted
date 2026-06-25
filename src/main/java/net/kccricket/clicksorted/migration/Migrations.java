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
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns the full catalog of <em>what is stored where</em> and <em>which lineage applies</em>, and
 * exposes one entry point per store. Callers (config loader, join listener) pass only the store; the
 * loader/store has no knowledge of which settings get migrated.
 *
 * <p>The catalog has <em>three</em> kinds of rules per config store, applied in order on every
 * {@link #migrate(ConfigurationSection)} call:
 * <ol>
 *   <li><b>Structural transforms</b> ({@link ConfigTransform}) — derive new state from old (e.g.
 *       translate a deprecated numeric range into an equivalent slot list). Run <em>before</em>
 *       the removal pass so the old keys are still readable. Adding a future transform is a
 *       one-line append to {@link #CONFIG_TRANSFORMS}.</li>
 *   <li><b>Value-remap lineages</b> ({@link ValueMigration}) — rewrite a stored string token to
 *       its canonical form across potentially many historical renames in a single pass.</li>
 *   <li><b>Deprecated-path removal</b> — drop keys that have been removed from the schema.
 *       Source-key removal for structural transforms is done here, not inside the transform.</li>
 * </ol>
 *
 * <p>PDC migrations have only value-remap lineages and deprecated-key removal (no structural
 * transforms are in scope), applied symmetrically by {@link #migrate(Player)}.
 */
public final class Migrations {

    /**
     * A self-contained structural config migration: derives new config state from old values,
     * in place. Returns {@code true} if anything was changed.
     *
     * <p>Source-key removal is <strong>not</strong> the transform's responsibility — list the
     * old paths in {@link #DEPRECATED_CONFIG_PATHS} so the removal pass cleans them up after
     * the transforms have already read them.
     */
    @FunctionalInterface
    interface ConfigTransform {
        boolean apply(ConfigurationSection config);
    }

    /**
     * {@code defaults.click_mode} in config and the per-player {@code click} PDC key.
     * Renames the 1.0.0 {@code ClickMethod} constants to their current spellings. A future rename is
     * a pure append, e.g. {@code .rename("SINGLE").to("SINGLE_CLICK").to("SINGLE_PUNCH")}.
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
     * Structural config transforms, applied in order before value-remap and removal passes.
     * To add a future structural migration, append here — the engine loop in
     * {@link #migrate(ConfigurationSection)} iterates this list generically.
     */
    private static final List<ConfigTransform> CONFIG_TRANSFORMS = List.of(
            Migrations::migrateSortBounds);

    /** Config paths (value-remap): path → lineage. */
    private static final Map<String, ValueMigration> CONFIG = Map.of(
            "defaults.click_mode", CLICK_METHOD,
            "defaults.start_corner", START_CORNER,
            "defaults.fill_axis", FILL_AXIS);

    /** Per-player PDC keys (value-remap): key name → lineage. */
    private static final Map<String, ValueMigration> PDC = Map.of(
            "click", CLICK_METHOD,
            "start_corner", START_CORNER,
            "fill_axis", FILL_AXIS);

    /** Config paths for settings that have been removed entirely and should be dropped. */
    private static final List<String> DEPRECATED_CONFIG_PATHS = List.of(
            "defaults.shift_click",
            "player_sort_min",
            "player_sort_max");

    /** Per-player PDC key names for settings that have been removed entirely and should be dropped. */
    private static final List<String> DEPRECATED_PDC_KEYS = List.of(
            "shift_click");

    private final ClickSortedPlugin plugin;

    public Migrations(ClickSortedPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Applies the full config catalog to {@code config} in place, in three passes:
     * <ol>
     *   <li>Structural transforms (read old keys before removal).</li>
     *   <li>Value-remap lineages.</li>
     *   <li>Deprecated-path removal.</li>
     * </ol>
     * The caller is responsible for persisting the configuration afterward.
     *
     * @return true if any value was rewritten, any deprecated path was removed, or any
     *         structural transform made a change
     */
    public boolean migrate(ConfigurationSection config) {
        boolean changed = false;
        // Pass 1: structural transforms — must run before removal so old keys are still readable.
        for (ConfigTransform transform : CONFIG_TRANSFORMS) {
            changed |= transform.apply(config);
        }
        // Pass 2: value-remap lineages.
        for (Map.Entry<String, ValueMigration> entry : CONFIG.entrySet()) {
            changed |= apply(config, entry.getKey(), entry.getValue());
        }
        // Pass 3: deprecated-path removal (also removes source keys for structural transforms).
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
    // Structural transform implementations
    // -------------------------------------------------------------------------

    /**
     * Translates legacy {@code player_sort_min} / {@code player_sort_max} into equivalent
     * {@code locked_slots.player} entries so that any customised sort window is preserved.
     *
     * <p>The old settings defined a contiguous sortable window {@code [min, max)} within the
     * player main-storage range {@code [9, 36)}. The slots outside that window — {@code [9, min)}
     * and {@code [max, 36)} — are now expressed as admin-locked slots. This method computes those
     * slots and unions them into the existing {@code locked_slots.player} list (deduplicated,
     * sorted). If neither key is present, or if both are at their defaults (9 and 36),
     * no change is made.
     *
     * <p>Source-key removal is handled by {@link #DEPRECATED_CONFIG_PATHS}, not here.
     *
     * @return {@code true} if {@code locked_slots.player} was written (changed)
     */
    private static boolean migrateSortBounds(ConfigurationSection config) {
        boolean hasSortMin = config.contains("player_sort_min");
        boolean hasSortMax = config.contains("player_sort_max");
        if (!hasSortMin && !hasSortMax) {
            return false; // neither key present — idempotent no-op on subsequent loads
        }

        int min = Math.max(0, Math.min(config.getInt("player_sort_min", 9), MainConfig.PLAYER_STORAGE_END));
        int max = Math.max(0, Math.min(config.getInt("player_sort_max", MainConfig.PLAYER_STORAGE_END), MainConfig.PLAYER_STORAGE_END));

        // Slots formerly excluded: [9, min) ∪ [max, 36)
        List<Integer> excluded = new ArrayList<>();
        for (int i = 9; i < min; i++) excluded.add(i);
        for (int i = max; i < MainConfig.PLAYER_STORAGE_END; i++) excluded.add(i);

        if (excluded.isEmpty()) {
            return false; // min/max were at their effective defaults — nothing to lock
        }

        // Union with any pre-existing locked_slots.player entries (deduplicated, sorted).
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
