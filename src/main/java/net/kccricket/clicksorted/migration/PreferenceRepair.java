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
import net.kccricket.clicksorted.model.ClickMethod;
import net.kccricket.clicksorted.model.FillAxis;
import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.model.StartCorner;
import net.kccricket.clicksorted.text.MessageUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Join-time repair of a player's stored preferences, run <em>after</em> {@link Migrations#migrate(Player)}
 * so renamed values (e.g. {@code DOUBLE} → {@code DOUBLE_CLICK}) have already converged to canonical form.
 * <p>
 * Two responsibilities, both surfaced to the <em>player</em> (not the console):
 * <ul>
 *   <li><b>Invalid enum-value reset.</b> A stored enum value that is no longer a valid constant is removed
 *       so the read path falls back to the server default; the player is told. Validity is checked directly
 *       (not via the enum's parse helper) so no console warning fires for PDC reads.</li>
 *   <li><b>Hover coupling enforcement.</b> Click methods that govern the hover preference
 *       ({@link ClickMethod#requiredSortOverItems()}) have their required value applied here.</li>
 * </ul>
 */
public final class PreferenceRepair {

    /** Enum-backed PDC key name → enum class, with the human-readable label used in the reset message. */
    private record EnumPref(String key, Class<? extends Enum<?>> type, String label) {
    }

    private static final Map<String, EnumPref> ENUM_PREFS = buildEnumPrefs();

    private static Map<String, EnumPref> buildEnumPrefs() {
        Map<String, EnumPref> map = new LinkedHashMap<>();
        map.put("click", new EnumPref("click", ClickMethod.class, "click method"));
        map.put("sort", new EnumPref("sort", SortingMethod.class, "sort method"));
        map.put("start_corner", new EnumPref("start_corner", StartCorner.class, "start corner"));
        map.put("fill_axis", new EnumPref("fill_axis", FillAxis.class, "fill axis"));
        return map;
    }

    private PreferenceRepair() {
    }

    /** Resets any invalid stored enum value to its default, then enforces hover coupling. */
    public static void repair(ClickSortedPlugin plugin, Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        for (EnumPref pref : ENUM_PREFS.values()) {
            resetIfInvalid(plugin, player, pdc, pref);
        }
        enforceHover(plugin, player, plugin.getSortingPrefs().getClickMethod(player));
    }

    /** Removes a non-null stored value under {@code pref} that is not a valid constant, messaging the player. */
    private static void resetIfInvalid(ClickSortedPlugin plugin, Player player,
                                       PersistentDataContainer pdc, EnumPref pref) {
        NamespacedKey key = new NamespacedKey(plugin, pref.key());
        String stored = pdc.get(key, PersistentDataType.STRING);
        if (stored == null || isValidConstant(pref.type(), stored)) {
            return;
        }
        pdc.remove(key);
        var lang = plugin.getConfigManager().lang();
        MessageUtil.statusMessage(player, lang.getColoredMessage("prefResetInvalid",
                Placeholder.unparsed("pref", pref.label()),
                Placeholder.unparsed("value", stored),
                Placeholder.unparsed("default", currentDefault(plugin, player, pref))));
    }

    /** The name of the value the read path resolves to now that {@code pref}'s key has been removed. */
    private static String currentDefault(ClickSortedPlugin plugin, Player player, EnumPref pref) {
        var prefs = plugin.getSortingPrefs();
        return switch (pref.key()) {
            case "click" -> prefs.getClickMethod(player).name();
            case "sort" -> prefs.getSortingMethod(player).name();
            case "start_corner" -> prefs.getStartCorner(player).name();
            case "fill_axis" -> prefs.getFillAxis(player).name();
            default -> "";
        };
    }

    /** True if {@code raw} names a constant of {@code type} (exact match, no warning side-effect). */
    private static boolean isValidConstant(Class<? extends Enum<?>> type, String raw) {
        for (Enum<?> constant : type.getEnumConstants()) {
            if (constant.name().equals(raw)) {
                return true;
            }
        }
        return false;
    }

    /** Forces hover to the value the click method requires, messaging the player on change. */
    public static void enforceHover(ClickSortedPlugin plugin, Player player, ClickMethod method) {
        method.requiredSortOverItems().ifPresent(required -> {
            if (plugin.getSortingPrefs().getSortOverItems(player) != required) {
                plugin.getSortingPrefs().setSortOverItems(player, required);
                var lang = plugin.getConfigManager().lang();
                MessageUtil.statusMessage(player, lang.getColoredMessage("hoverForcedByClickMethod",
                        Placeholder.unparsed("status", required ? "ENABLED" : "DISABLED"),
                        Placeholder.unparsed("method", method.name())));
            }
        });
    }
}
