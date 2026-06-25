package net.kccricket.clicksorted.sort;

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

import net.kccricket.clicksorted.config.MainConfig;
import net.kccricket.clicksorted.security.Permissions;
import net.kccricket.clicksorted.text.ItemNames;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permissible;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Immutable snapshot of the admin-enforced "do not touch" list: any slot whose item matches
 * this list is excluded from the sortable slot set entirely — it is never sorted, moved, or
 * packed into/unpacked from a bundle during a sort.
 *
 * <p>Two admin-controlled channels contribute to the list (unioned together):
 * <ol>
 *   <li><b>Config</b> ({@code config.yml → blacklist}) — materials matched exactly, display
 *       names matched case-insensitively against the item's resolved plain-text name.</li>
 *   <li><b>Permission</b> — {@code clicksorted.blacklist.material.<material>} and
 *       {@code clicksorted.blacklist.name.<slug>} are checked against the sorting player so
 *       that admins can scope protection to specific players or groups via any permissions
 *       plugin.</li>
 * </ol>
 *
 * <p>The permission name channel uses a <em>slug</em> token: lowercase the plain-text display
 * name, collapse every run of non-{@code [a-z0-9]} characters to a single {@code _}, then strip
 * leading/trailing underscores. Example: {@code "Creative Menu"} → {@code "creative_menu"} →
 * node {@code clicksorted.blacklist.name.creative_menu}.
 *
 * <p>This list is <strong>admin-only</strong> and is not exposed to players via any command or
 * GUI. For player-controlled bundle exclusions see {@link BundleBlacklist}.
 *
 * @see #nameToken(String)
 * @see #forSort(Permissible, MainConfig)
 */
public record ProtectedItems(Set<Material> materials, Set<String> namesLower, Permissible permissible) {

    private static final Pattern STRIP_COLOR = Pattern.compile("§.");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern TRIM_UNDERSCORE = Pattern.compile("^_+|_+$");

    /** A list that blocks nothing; use when the feature is unconfigured. */
    public static final ProtectedItems EMPTY = new ProtectedItems(Set.of(), Set.of(), null);

    /**
     * Returns {@code true} when all channels are empty/absent, allowing the sort service to skip
     * the per-slot exclusion scan entirely.
     */
    public boolean isEmpty() {
        return materials.isEmpty() && namesLower.isEmpty() && permissible == null;
    }

    /**
     * Returns {@code true} if {@code is} is protected and its slot should be excluded from the
     * sort.
     *
     * @param is the item to test; must not be null
     */
    public boolean blocks(ItemStack is) {
        if (materials.contains(is.getType())) {
            return true;
        }

        // Resolve the plain-text display name lazily — only when a name-based check is needed.
        String name = null;
        if (!namesLower.isEmpty()) {
            name = ItemNames.lookup(is);
            if (name != null && namesLower.contains(name.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        if (permissible != null) {
            // Use isPermissionSet + hasPermission rather than hasPermission alone: undeclared
            // permission nodes fall back to PermissionDefault.OP in Bukkit, so a plain
            // hasPermission call would return true for every OP player even when no blacklist
            // node has been granted. isPermissionSet is true only when the node was explicitly
            // attached to the player, avoiding the OP-default false-positive.
            String materialNode = Permissions.PERM_BLACKLIST_MATERIAL + is.getType().name().toLowerCase(Locale.ROOT);
            if (Permissions.isExplicitlyGranted(permissible, materialNode)) {
                return true;
            }
            if (name == null) {
                name = ItemNames.lookup(is);
            }
            if (name != null) {
                String token = nameToken(name);
                if (!token.isEmpty()) {
                    String nameNode = Permissions.PERM_BLACKLIST_NAME + token;
                    if (Permissions.isExplicitlyGranted(permissible, nameNode)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Normalizes a display-name string into a permission-node-safe slug:
     * <ol>
     *   <li>Lowercase ({@link Locale#ROOT}).</li>
     *   <li>Collapse every run of non-{@code [a-z0-9]} characters to a single {@code _}.</li>
     *   <li>Strip leading and trailing underscores.</li>
     * </ol>
     *
     * <p>Example: {@code "§6Creative Menu!"} → {@code "creative_menu"}.
     *
     * <p>An all-symbol name (e.g. {@code "!!!"}) returns an empty string. Callers must skip the
     * permission check in that case to avoid constructing a bare {@code ...name.} node.
     */
    public static String nameToken(String name) {
        return TRIM_UNDERSCORE.matcher(
                        NON_ALNUM.matcher(
                                STRIP_COLOR.matcher(name).replaceAll("").toLowerCase(Locale.ROOT))
                        .replaceAll("_"))
                .replaceAll("");
    }

    /**
     * Factory: builds a {@code ProtectedItems} snapshot from the cached config sets and the
     * sorting player (for the permission channel).
     *
     * @param who the sorting player (checked for permission nodes)
     * @param cfg the loaded main config (provides the cached material and name sets)
     */
    public static ProtectedItems forSort(Permissible who, MainConfig cfg) {
        return new ProtectedItems(cfg.getBlacklistMaterials(), cfg.getBlacklistNamesLower(), who);
    }
}
