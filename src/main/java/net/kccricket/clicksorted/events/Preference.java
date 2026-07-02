package net.kccricket.clicksorted.events;

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

import net.kccricket.clicksorted.model.ClickMethod;
import net.kccricket.clicksorted.model.FillAxis;
import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.model.StartCorner;
import org.bukkit.Material;

/**
 * Typed key identifying a per-player ClickSorted preference. The type parameter {@code T} is the
 * value type carried by a {@link PlayerPreferenceChangeEvent.Change} for that preference; since
 * the constants declared here are the only instances of this class, reference identity
 * ({@code preference() == Preference.CLICK_MODE}) proves {@code T} at each call site, which is
 * what lets {@link PlayerPreferenceChangeEvent#getChange(Preference)} return a safely typed value.
 */
public final class Preference<T> {
    public static final Preference<Boolean> ENABLED = new Preference<>("enabled");
    public static final Preference<ClickMethod> CLICK_MODE = new Preference<>("click_mode");
    public static final Preference<SortingMethod> SORT_MODE = new Preference<>("sort_mode");
    public static final Preference<StartCorner> START_CORNER = new Preference<>("start_corner");
    public static final Preference<FillAxis> FILL_AXIS = new Preference<>("fill_axis");
    public static final Preference<Boolean> SORT_OVER_ITEMS = new Preference<>("sort_over_items");
    public static final Preference<Boolean> BUNDLE_IN_INVENTORY = new Preference<>("bundle_in_inventory");
    public static final Preference<Boolean> BUNDLE_IN_CONTAINERS = new Preference<>("bundle_in_containers");
    public static final Preference<Integer> BUNDLE_STACK_LIMIT = new Preference<>("bundle_stack_limit");
    public static final Preference<Material> BUNDLE_BLACKLIST_MATERIAL = new Preference<>("bundle_blacklist_material");
    public static final Preference<String> BUNDLE_BLACKLIST_NAME = new Preference<>("bundle_blacklist_name");
    public static final Preference<Void> BUNDLE_BLACKLIST_CLEAR = new Preference<>("bundle_blacklist_clear");
    public static final Preference<Boolean> LOCKED_SLOT = new Preference<>("locked_slot");

    private final String id;

    private Preference(String id) {
        this.id = id;
    }

    /** The PDC/config leaf name this preference corresponds to, where one exists. */
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return id;
    }
}
