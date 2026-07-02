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
import java.util.Objects;
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

    public void setSortingMethod(Player player, SortingMethod sortMethod) {
        if (!fire(player, Preference.SORT_MODE, getSortingMethod(player), sortMethod)) return;
        player.getPersistentDataContainer().set(sortKey, PersistentDataType.STRING, sortMethod.name());
    }

    /**
     * Returns true if click-sorting is enabled for this player.
     * Falls back to the server default when the player has no stored preference.
     */
    public boolean getEnabled(Player player) {
        return getBool(player, enabledKey, plugin.getConfigManager().main()::getDefaultEnabled);
    }

    public void setEnabled(Player player, boolean enabled) {
        if (!fire(player, Preference.ENABLED, getEnabled(player), enabled)) return;
        setBool(player, enabledKey, enabled);
    }

    public ClickMethod getClickMethod(Player player) {
        String stored = player.getPersistentDataContainer().get(clickKey, PersistentDataType.STRING);
        return ClickMethod.parse(stored, plugin.getConfigManager().main().getDefaultClickMethod());
    }

    public void setClickMethod(Player player, ClickMethod clickMethod) {
        if (!fire(player, Preference.CLICK_MODE, getClickMethod(player), clickMethod)) return;
        player.getPersistentDataContainer().set(clickKey, PersistentDataType.STRING, clickMethod.name());
    }

    public StartCorner getStartCorner(Player player) {
        String stored = player.getPersistentDataContainer().get(startCornerKey, PersistentDataType.STRING);
        return StartCorner.parse(stored, plugin.getConfigManager().main().getDefaultStartCorner());
    }

    public void setStartCorner(Player player, StartCorner corner) {
        if (!fire(player, Preference.START_CORNER, getStartCorner(player), corner)) return;
        player.getPersistentDataContainer().set(startCornerKey, PersistentDataType.STRING, corner.name());
    }

    public FillAxis getFillAxis(Player player) {
        String stored = player.getPersistentDataContainer().get(fillAxisKey, PersistentDataType.STRING);
        return FillAxis.parse(stored, plugin.getConfigManager().main().getDefaultFillAxis());
    }

    public void setFillAxis(Player player, FillAxis axis) {
        if (!fire(player, Preference.FILL_AXIS, getFillAxis(player), axis)) return;
        player.getPersistentDataContainer().set(fillAxisKey, PersistentDataType.STRING, axis.name());
    }

    /**
     * Returns true if sorting fires even while the player is hovering an occupied slot.
     * Falls back to the server default when the player has no stored preference.
     */
    public boolean getSortOverItems(Player player) {
        return getBool(player, sortOverItemsKey, plugin.getConfigManager().main()::getDefaultSortOverItems);
    }

    public void setSortOverItems(Player player, boolean enabled) {
        if (!fire(player, Preference.SORT_OVER_ITEMS, getSortOverItems(player), enabled)) return;
        setBool(player, sortOverItemsKey, enabled);
    }

    /**
     * Returns true if bundle packing is enabled for the player's own inventory.
     * Falls back to the server default when the player has no stored preference.
     */
    public boolean getBundlePackInInventory(Player player) {
        return getBool(player, bundleInInventoryKey, plugin.getConfigManager().main()::getDefaultBundlePackInInventory);
    }

    public void setBundlePackInInventory(Player player, boolean enabled) {
        if (!fire(player, Preference.BUNDLE_IN_INVENTORY, getBundlePackInInventory(player), enabled)) return;
        setBool(player, bundleInInventoryKey, enabled);
    }

    /**
     * Returns true if bundle packing is enabled for other (container) inventories the player sorts.
     * Falls back to the server default when the player has no stored preference.
     */
    public boolean getBundlePackInContainers(Player player) {
        return getBool(player, bundleInContainersKey, plugin.getConfigManager().main()::getDefaultBundlePackInContainers);
    }

    public void setBundlePackInContainers(Player player, boolean enabled) {
        if (!fire(player, Preference.BUNDLE_IN_CONTAINERS, getBundlePackInContainers(player), enabled)) return;
        setBool(player, bundleInContainersKey, enabled);
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

    public void setBundleStackLimit(Player player, int limit) {
        int clamped = Math.max(0, limit);
        if (!fire(player, Preference.BUNDLE_STACK_LIMIT, getBundleStackLimit(player), clamped)) return;
        player.getPersistentDataContainer().set(bundleStackLimitKey, PersistentDataType.INTEGER, clamped);
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
     * @return {@code true} if the material was newly added, {@code false} if it was already present
     *         or the change was cancelled by a {@link PlayerPreferenceChangeEvent} listener
     */
    public boolean addToBundleBlacklist(Player player, Material material) {
        Set<Material> current = bundleBlacklist.mutableGet(player);
        if (current.contains(material)) return false;
        if (!fire(player, Preference.BUNDLE_BLACKLIST_MATERIAL, null, material)) return false;
        return bundleBlacklist.addTo(player, current, material);
    }

    /**
     * Removes a material from the player's bundle blacklist.
     *
     * @return {@code true} if the material was removed, {@code false} if it was not present
     *         or the change was cancelled by a {@link PlayerPreferenceChangeEvent} listener
     */
    public boolean removeFromBundleBlacklist(Player player, Material material) {
        Set<Material> current = bundleBlacklist.mutableGet(player);
        if (!current.contains(material)) return false;
        if (!fire(player, Preference.BUNDLE_BLACKLIST_MATERIAL, material, null)) return false;
        return bundleBlacklist.removeFrom(player, current, material);
    }

    /** Clears all entries (materials and display names) from the player's bundle blacklist. */
    public void clearBundleBlacklist(Player player) {
        if (bundleBlacklist.get(player).isEmpty() && bundleBlacklistNames.get(player).isEmpty()) return;
        if (!fire(player, new ValueChange<>(Preference.BUNDLE_BLACKLIST_CLEAR, null, null))) return;
        bundleBlacklist.clear(player);
        bundleBlacklistNames.clear(player);
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
     * @return {@code true} if the name was newly added, {@code false} if it was already present
     *         or the change was cancelled by a {@link PlayerPreferenceChangeEvent} listener
     */
    public boolean addToBundleBlacklistName(Player player, String name) {
        Set<String> current = bundleBlacklistNames.mutableGet(player);
        if (current.contains(name)) return false;
        if (!fire(player, Preference.BUNDLE_BLACKLIST_NAME, null, name)) return false;
        return bundleBlacklistNames.addTo(player, current, name);
    }

    /**
     * Removes a display name from the player's bundle name blacklist.
     *
     * @return {@code true} if the name was removed, {@code false} if it was not present
     *         or the change was cancelled by a {@link PlayerPreferenceChangeEvent} listener
     */
    public boolean removeFromBundleBlacklistName(Player player, String name) {
        Set<String> current = bundleBlacklistNames.mutableGet(player);
        if (!current.contains(name)) return false;
        if (!fire(player, Preference.BUNDLE_BLACKLIST_NAME, name, null)) return false;
        return bundleBlacklistNames.removeFrom(player, current, name);
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

        /** Fetches a mutable working copy, for callers that need to inspect membership before mutating. */
        Set<T> mutableGet(Player player) {
            Set<T> current = newSet.get();
            current.addAll(get(player));
            return current;
        }

        /** Adds to an already-fetched working set (see {@link #mutableGet}), avoiding a redundant PDC re-read. */
        boolean addTo(Player player, Set<T> current, T value) {
            if (!current.add(value)) return false;
            store(player, current);
            return true;
        }

        /** Removes from an already-fetched working set (see {@link #mutableGet}), avoiding a redundant PDC re-read. */
        boolean removeFrom(Player player, Set<T> current, T value) {
            if (!current.remove(value)) return false;
            store(player, current);
            return true;
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
     * Toggles whether {@code slot} is locked for the player.
     *
     * @return the slot's locked state after the toggle; if a {@link PlayerPreferenceChangeEvent}
     *         listener cancels the change, the slot's prior (unchanged) locked state is returned
     */
    public boolean toggleSlotLocked(Player player, int slot) {
        Set<Integer> slots = new HashSet<>(getLockedSlots(player));
        boolean currentlyLocked = slots.contains(slot);
        boolean nowLocked = !currentlyLocked;
        if (!fire(player, new LockedSlotChange(slot, currentlyLocked, nowLocked))) {
            return currentlyLocked;
        }
        if (nowLocked) slots.add(slot); else slots.remove(slot);
        setLockedSlots(player, slots);
        return nowLocked;
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
     * Fires a {@link PlayerPreferenceChangeEvent} for a would-be preference change and reports
     * whether the caller should proceed. A no-op (equal old/new values) is never fired and always
     * proceeds, so idempotent setter calls don't spuriously invoke listeners.
     *
     * @return {@code true} if the change should be applied, {@code false} if a listener cancelled it
     */
    private <T> boolean fire(Player player, Preference<T> pref, T oldValue, T newValue) {
        if (Objects.equals(oldValue, newValue)) return true;
        return fire(player, new ValueChange<>(pref, oldValue, newValue));
    }

    /**
     * Fires a {@link PlayerPreferenceChangeEvent} for the given change payload and reports whether
     * the caller should proceed. No equality guard — callers with their own no-op logic (a toggle,
     * or a clear that's a null/null change) call this directly.
     *
     * @return {@code true} if the change should be applied, {@code false} if a listener cancelled it
     */
    private boolean fire(Player player, Change<?> change) {
        PlayerPreferenceChangeEvent event = new PlayerPreferenceChangeEvent(player, change);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

}
