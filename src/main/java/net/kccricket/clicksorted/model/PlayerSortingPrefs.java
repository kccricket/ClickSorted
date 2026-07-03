package net.kccricket.clicksorted.model;

/*
 This file is part of ClickSorted

 ClickSorted is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 ClickSorted is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with ClickSorted.  If not, see <http://www.gnu.org/licenses/>.
 */

import net.kccricket.clicksorted.ClickSortedPlugin;
import net.kccricket.clicksorted.events.PlayerPreferenceChangeEvent;
import net.kccricket.clicksorted.events.PlayerPreferenceChangeEvent.Change;
import net.kccricket.clicksorted.events.PlayerPreferenceChangeEvent.LockedSlotChange;
import net.kccricket.clicksorted.events.PlayerPreferenceChangeEvent.ValueChange;
import net.kccricket.clicksorted.events.Preference;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PlayerSortingPrefs {
    private final ClickSortedPlugin plugin;
    private final NamespacedKey sortKey;
    private final NamespacedKey clickKey;
    private final NamespacedKey enabledKey;
    private final NamespacedKey sortOverItemsKey;
    private final NamespacedKey lockedSlotsKey;
    private final NamespacedKey bundleInInventoryKey;
    private final NamespacedKey bundleInContainersKey;
    private final NamespacedKey bundleStackLimitKey;
    private final PdcStringSet<Material> bundleBlacklist;
    private final PdcStringSet<String> bundleBlacklistNames;
    private final NamespacedKey startCornerKey;
    private final NamespacedKey fillAxisKey;

    public PlayerSortingPrefs(ClickSortedPlugin plugin) {
        this.plugin = plugin;
        this.sortKey = new NamespacedKey(plugin, "sort_mode");
        this.clickKey = new NamespacedKey(plugin, "click_mode");
        this.enabledKey = new NamespacedKey(plugin, "enabled");
        this.sortOverItemsKey = new NamespacedKey(plugin, "sort_over_items");
        this.lockedSlotsKey = new NamespacedKey(plugin, "locked_slots");
        this.bundleInInventoryKey = new NamespacedKey(plugin, "bundle_in_inventory");
        this.bundleInContainersKey = new NamespacedKey(plugin, "bundle_in_containers");
        this.bundleStackLimitKey = new NamespacedKey(plugin, "bundle_stack_limit");
        this.bundleBlacklist = new PdcStringSet<>(
                new NamespacedKey(plugin, "bundle_blacklist"), ",",
                Material::matchMaterial, Material::name,
                () -> EnumSet.noneOf(Material.class));
        this.bundleBlacklistNames = new PdcStringSet<>(
                new NamespacedKey(plugin, "bundle_blacklist_names"), "\n",
                token -> token.isBlank() ? null : token, name -> name,
                LinkedHashSet::new);
        this.startCornerKey = new NamespacedKey(plugin, "start_corner");
        this.fillAxisKey = new NamespacedKey(plugin, "fill_axis");
    }

    public SortingMethod getSortingMethod(Player player) {
        String stored = player.getPersistentDataContainer().get(sortKey, PersistentDataType.STRING);
        return SortingMethod.parse(stored, plugin.getConfigManager().main().getDefaultSortingMethod());
    }

    public PreferenceResult setSortingMethod(Player player, SortingMethod sortMethod) {
        return setEnumPref(player, Preference.SORT_MODE, sortKey, getSortingMethod(player), sortMethod);
    }

    /**
     * Returns true if click-sorting is enabled for this player.
     * Falls back to the server default when the player has no stored preference.
     */
    public boolean getEnabled(Player player) {
        return getBool(player, enabledKey, plugin.getConfigManager().main()::getDefaultEnabled);
    }

    public PreferenceResult setEnabled(Player player, boolean enabled) {
        return setBoolPref(player, Preference.ENABLED, enabledKey, getEnabled(player), enabled);
    }

    public ClickMethod getClickMethod(Player player) {
        String stored = player.getPersistentDataContainer().get(clickKey, PersistentDataType.STRING);
        return ClickMethod.parse(stored, plugin.getConfigManager().main().getDefaultClickMethod());
    }

    public PreferenceResult setClickMethod(Player player, ClickMethod clickMethod) {
        return setEnumPref(player, Preference.CLICK_MODE, clickKey, getClickMethod(player), clickMethod);
    }

    public StartCorner getStartCorner(Player player) {
        String stored = player.getPersistentDataContainer().get(startCornerKey, PersistentDataType.STRING);
        return StartCorner.parse(stored, plugin.getConfigManager().main().getDefaultStartCorner());
    }

    public PreferenceResult setStartCorner(Player player, StartCorner corner) {
        return setEnumPref(player, Preference.START_CORNER, startCornerKey, getStartCorner(player), corner);
    }

    public FillAxis getFillAxis(Player player) {
        String stored = player.getPersistentDataContainer().get(fillAxisKey, PersistentDataType.STRING);
        return FillAxis.parse(stored, plugin.getConfigManager().main().getDefaultFillAxis());
    }

    public PreferenceResult setFillAxis(Player player, FillAxis axis) {
        return setEnumPref(player, Preference.FILL_AXIS, fillAxisKey, getFillAxis(player), axis);
    }

    /**
     * Returns true if sorting fires even while the player is hovering an occupied slot.
     * Falls back to the server default when the player has no stored preference.
     */
    public boolean getSortOverItems(Player player) {
        return getBool(player, sortOverItemsKey, plugin.getConfigManager().main()::getDefaultSortOverItems);
    }

    public PreferenceResult setSortOverItems(Player player, boolean enabled) {
        return setBoolPref(player, Preference.SORT_OVER_ITEMS, sortOverItemsKey, getSortOverItems(player), enabled);
    }

    /**
     * Returns true if bundle packing is enabled for the player's own inventory.
     * Falls back to the server default when the player has no stored preference.
     */
    public boolean getBundlePackInInventory(Player player) {
        return getBool(player, bundleInInventoryKey, plugin.getConfigManager().main()::getDefaultBundlePackInInventory);
    }

    public PreferenceResult setBundlePackInInventory(Player player, boolean enabled) {
        return setBoolPref(player, Preference.BUNDLE_IN_INVENTORY, bundleInInventoryKey,
                getBundlePackInInventory(player), enabled);
    }

    /**
     * Returns true if bundle packing is enabled for other (container) inventories the player sorts.
     * Falls back to the server default when the player has no stored preference.
     */
    public boolean getBundlePackInContainers(Player player) {
        return getBool(player, bundleInContainersKey, plugin.getConfigManager().main()::getDefaultBundlePackInContainers);
    }

    public PreferenceResult setBundlePackInContainers(Player player, boolean enabled) {
        return setBoolPref(player, Preference.BUNDLE_IN_CONTAINERS, bundleInContainersKey,
                getBundlePackInContainers(player), enabled);
    }

    /** Reads a boolean preference stored as a byte, falling back to {@code def} when unset. */
    private static boolean getBool(Player player, NamespacedKey key, java.util.function.BooleanSupplier def) {
        Byte stored = player.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return stored != null ? stored != 0 : def.getAsBoolean();
    }

    /** Writes a boolean preference as a byte. */
    private static void setBool(Player player, NamespacedKey key, boolean enabled) {
        player.getPersistentDataContainer().set(key, PersistentDataType.BYTE, enabled ? (byte) 1 : (byte) 0);
    }

    /**
     * Returns the per-player bundle stack limit (max distinct entries per bundle); 0 means weight-only.
     * Falls back to the server default when the player has no stored preference.
     */
    public int getBundleStackLimit(Player player) {
        Integer stored = player.getPersistentDataContainer().get(bundleStackLimitKey, PersistentDataType.INTEGER);
        return stored != null ? stored : plugin.getConfigManager().main().getDefaultBundleStackLimit();
    }

    public PreferenceResult setBundleStackLimit(Player player, int limit) {
        int clamped = Math.max(0, limit);
        int oldValue = getBundleStackLimit(player);
        if (oldValue == clamped) {
            // Still pin the value: the effective old value may come from the config default,
            // and an explicit choice must survive a later default change.
            player.getPersistentDataContainer().set(bundleStackLimitKey, PersistentDataType.INTEGER, clamped);
            return PreferenceResult.UNCHANGED;
        }
        PreferenceResult result = fire(player, Preference.BUNDLE_STACK_LIMIT, oldValue, clamped);
        if (result.applied()) {
            player.getPersistentDataContainer().set(bundleStackLimitKey, PersistentDataType.INTEGER, clamped);
        }
        return result;
    }

    /**
     * Returns the player's bundle blacklist: materials that will not be packed into or unpacked from
     * bundles during sorting. Unknown tokens (e.g. from a removed material) are silently dropped.
     * Returns an empty, unmodifiable set when no blacklist has been stored.
     */
    public Set<Material> getBundleBlacklist(Player player) { return bundleBlacklist.get(player); }

    /**
     * Adds a material to the player's bundle blacklist.
     *
     * @return {@link PreferenceResult#APPLIED} if newly added, {@link PreferenceResult#UNCHANGED}
     *         if already present, or a cancelled result if a listener vetoed the change
     */
    public PreferenceResult addToBundleBlacklist(Player player, Material material) {
        return mutateBlacklist(player, bundleBlacklist, Preference.BUNDLE_BLACKLIST_MATERIAL, material, true);
    }

    /**
     * Removes a material from the player's bundle blacklist.
     *
     * @return {@link PreferenceResult#APPLIED} if removed, {@link PreferenceResult#UNCHANGED}
     *         if not present, or a cancelled result if a listener vetoed the change
     */
    public PreferenceResult removeFromBundleBlacklist(Player player, Material material) {
        return mutateBlacklist(player, bundleBlacklist, Preference.BUNDLE_BLACKLIST_MATERIAL, material, false);
    }

    /** Clears all entries (materials and display names) from the player's bundle blacklist. */
    public PreferenceResult clearBundleBlacklist(Player player) {
        if (bundleBlacklist.get(player).isEmpty() && bundleBlacklistNames.get(player).isEmpty()) {
            return PreferenceResult.UNCHANGED;
        }
        PreferenceResult result = fire(player, new ValueChange<>(Preference.BUNDLE_BLACKLIST_CLEAR, null, null));
        if (result.applied()) {
            bundleBlacklist.clear(player);
            bundleBlacklistNames.clear(player);
        }
        return result;
    }

    /**
     * Returns the player's display-name bundle blacklist: plain-text display names that will not be
     * packed into or unpacked from bundles during sorting. Blank tokens are silently dropped.
     * Returns an empty, unmodifiable set when no name blacklist has been stored.
     */
    public Set<String> getBundleBlacklistNames(Player player) { return bundleBlacklistNames.get(player); }

    /**
     * Adds a display name to the player's bundle name blacklist.
     *
     * @return {@link PreferenceResult#APPLIED} if newly added, {@link PreferenceResult#UNCHANGED}
     *         if already present, or a cancelled result if a listener vetoed the change
     */
    public PreferenceResult addToBundleBlacklistName(Player player, String name) {
        return mutateBlacklist(player, bundleBlacklistNames, Preference.BUNDLE_BLACKLIST_NAME, name, true);
    }

    /**
     * Removes a display name from the player's bundle name blacklist.
     *
     * @return {@link PreferenceResult#APPLIED} if removed, {@link PreferenceResult#UNCHANGED}
     *         if not present, or a cancelled result if a listener vetoed the change
     */
    public PreferenceResult removeFromBundleBlacklistName(Player player, String name) {
        return mutateBlacklist(player, bundleBlacklistNames, Preference.BUNDLE_BLACKLIST_NAME, name, false);
    }

    /**
     * Shared add/remove for the set-valued blacklist preferences: membership check, event fire,
     * then a read-modify-write that re-reads PDC <em>after</em> listeners ran, so a re-entrant
     * blacklist mutation made by a listener during the event is not clobbered by a stale snapshot.
     */
    private <T> PreferenceResult mutateBlacklist(Player player, PdcStringSet<T> set,
                                                 Preference<T> pref, T value, boolean add) {
        boolean present = set.get(player).contains(value);
        if (present == add) return PreferenceResult.UNCHANGED;
        PreferenceResult result = fire(player, pref, add ? null : value, add ? value : null);
        if (result.applied()) {
            if (add) set.add(player, value); else set.remove(player, value);
        }
        return result;
    }

    /** A PDC-backed set of {@code T} serialized as a delimited string under one {@link NamespacedKey}. */
    private static final class PdcStringSet<T> {
        private final NamespacedKey key;
        private final String delimiter;
        private final Function<String, T> parse;   // returns null to skip an unparseable token
        private final Function<T, String> format;
        private final Supplier<Set<T>> newSet;     // factory for the mutable working set

        PdcStringSet(NamespacedKey key, String delimiter,
                     Function<String, T> parse, Function<T, String> format,
                     Supplier<Set<T>> newSet) {
            this.key = key;
            this.delimiter = delimiter;
            this.parse = parse;
            this.format = format;
            this.newSet = newSet;
        }

        Set<T> get(Player player) {
            String stored = player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (stored == null || stored.isBlank()) return Set.of();
            Set<T> result = newSet.get();
            for (String token : stored.split(delimiter)) {
                T v = parse.apply(token);
                if (v != null) result.add(v);
            }
            return result.isEmpty() ? Set.of() : Set.copyOf(result);
        }

        /** Atomic read-modify-write add; reads current PDC state at call time. */
        void add(Player player, T value) {
            Set<T> current = newSet.get();
            current.addAll(get(player));
            current.add(value);
            store(player, current);
        }

        /** Atomic read-modify-write remove; reads current PDC state at call time. */
        void remove(Player player, T value) {
            Set<T> current = newSet.get();
            current.addAll(get(player));
            current.remove(value);
            store(player, current);
        }

        void clear(Player player) {
            player.getPersistentDataContainer().remove(key);
        }

        private void store(Player player, Set<T> values) {
            if (values.isEmpty()) {
                player.getPersistentDataContainer().remove(key);
            } else {
                String joined = values.stream().map(format).collect(Collectors.joining(delimiter));
                player.getPersistentDataContainer().set(key, PersistentDataType.STRING, joined);
            }
        }
    }

    public Set<Integer> getLockedSlots(Player player) {
        int[] stored = player.getPersistentDataContainer().get(lockedSlotsKey, PersistentDataType.INTEGER_ARRAY);
        if (stored == null || stored.length == 0) {
            return Set.of();
        }
        return Arrays.stream(stored).boxed().collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Toggles whether {@code slot} is locked for the player. On a cancelled result, the slot's
     * locked state is left unchanged — callers should re-read {@link #getLockedSlots(Player)}
     * (or the toggle's known prior state) rather than assume the toggle applied.
     */
    public PreferenceResult toggleSlotLocked(Player player, int slot) {
        boolean currentlyLocked = getLockedSlots(player).contains(slot);
        boolean nowLocked = !currentlyLocked;
        PreferenceResult result = fire(player, new LockedSlotChange(slot, currentlyLocked, nowLocked));
        if (result.applied()) {
            // Re-read after the event so a re-entrant lock change made by a listener isn't clobbered.
            Set<Integer> slots = new HashSet<>(getLockedSlots(player));
            if (nowLocked) slots.add(slot); else slots.remove(slot);
            setLockedSlots(player, slots);
        }
        return result;
    }

    public void setLockedSlots(Player player, Set<Integer> slots) {
        if (slots.isEmpty()) {
            player.getPersistentDataContainer().remove(lockedSlotsKey);
        } else {
            player.getPersistentDataContainer().set(lockedSlotsKey, PersistentDataType.INTEGER_ARRAY,
                    slots.stream().mapToInt(Integer::intValue).toArray());
        }
    }

    /**
     * Fires a {@link PlayerPreferenceChangeEvent} for a preference change and reports whether the
     * caller should proceed. Callers are responsible for their own no-op detection — a no-op
     * (equal old/new values) must not be fired, so idempotent setter calls don't spuriously
     * invoke listeners.
     */
    private <T> PreferenceResult fire(Player player, Preference<T> pref, T oldValue, T newValue) {
        return fire(player, new ValueChange<>(pref, oldValue, newValue));
    }

    /**
     * Fires a {@link PlayerPreferenceChangeEvent} for the given change payload and reports whether
     * the caller should proceed. No equality guard — callers with their own no-op logic (a toggle,
     * or a clear that's a null/null change) call this directly.
     */
    private PreferenceResult fire(Player player, Change<?> change) {
        PlayerPreferenceChangeEvent event = new PlayerPreferenceChangeEvent(player, change);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return PreferenceResult.cancelled(event.getCancelReason());
        }
        return PreferenceResult.APPLIED;
    }

    /**
     * Fires an enum-valued preference change and, if applied, stores {@code newValue} under
     * {@code key} as a string. A no-op (equal old/new) skips the event but still writes: the
     * effective old value may come from the config default, and an explicit choice must be
     * pinned in PDC so a later default change can't silently flip it.
     */
    private <E extends Enum<E>> PreferenceResult setEnumPref(Player player, Preference<E> pref,
                                                              NamespacedKey key, E oldValue, E newValue) {
        if (oldValue == newValue) {
            player.getPersistentDataContainer().set(key, PersistentDataType.STRING, newValue.name());
            return PreferenceResult.UNCHANGED;
        }
        PreferenceResult result = fire(player, pref, oldValue, newValue);
        if (result.applied()) {
            player.getPersistentDataContainer().set(key, PersistentDataType.STRING, newValue.name());
        }
        return result;
    }

    /**
     * Fires a boolean-valued preference change and, if applied, stores {@code newValue} under
     * {@code key}. A no-op (equal old/new) skips the event but still writes, pinning the value
     * against later config-default changes (see {@link #setEnumPref}).
     */
    private PreferenceResult setBoolPref(Player player, Preference<Boolean> pref,
                                          NamespacedKey key, boolean oldValue, boolean newValue) {
        if (oldValue == newValue) {
            setBool(player, key, newValue);
            return PreferenceResult.UNCHANGED;
        }
        PreferenceResult result = fire(player, pref, oldValue, newValue);
        if (result.applied()) {
            setBool(player, key, newValue);
        }
        return result;
    }

}
