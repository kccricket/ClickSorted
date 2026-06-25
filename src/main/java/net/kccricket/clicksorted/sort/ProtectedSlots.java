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
import org.bukkit.permissions.Permissible;

import java.util.Set;

/**
 * Immutable snapshot of the admin-enforced "do not sort" slot list: any player inventory slot whose
 * index appears in this list is excluded from the sortable slot set entirely — it is never sorted,
 * moved, or packed into/unpacked from a bundle during a sort, and cannot be toggled by the player
 * in the lock GUI.
 *
 * <p>Two admin-controlled channels contribute to the list (unioned together):
 * <ol>
 *   <li><b>Config</b> ({@code config.yml → locked_slots.player}) — server-wide. Slot indices
 *       0–35 (0–8 hotbar, 9–35 main storage); out-of-range values are warned and skipped on
 *       load/reload.</li>
 *   <li><b>Permission</b> — {@code clicksorted.lock.player.slot.<n>} (e.g.
 *       {@code clicksorted.lock.player.slot.9}) is checked against the player, allowing
 *       per-player or per-group slot locks via any permissions plugin.</li>
 * </ol>
 *
 * <p>This class is scoped to <strong>player inventories only</strong> (slots 0–35). Container
 * sorts are not checked against it.
 *
 * <p>The permission channel uses {@code isPermissionSet} before {@code hasPermission} to avoid
 * Bukkit's OP default (undeclared nodes return {@code true} for OPs via
 * {@link org.bukkit.permissions.PermissionDefault#OP}). Only explicitly-attached permission nodes
 * trigger the slot lock — OPs without explicit node grants are unaffected.
 *
 * <p>The namespace {@code clicksorted.lock.player.*} is intentionally hierarchical to leave room
 * for future lock categories (e.g. {@code clicksorted.lock.container.*} keyed by container title).
 * Likewise, {@code locked_slots.player} in config has sibling capacity for future categories.
 */
public record ProtectedSlots(Set<Integer> configSlots, Permissible permissible) {

    /** A lock that blocks nothing; use when no admin slot locks are configured. */
    public static final ProtectedSlots EMPTY = new ProtectedSlots(Set.of(), null);

    /**
     * Returns {@code true} when all channels are empty/absent, allowing the sort service and GUI
     * to skip the per-slot exclusion check entirely.
     */
    public boolean isEmpty() {
        return configSlots.isEmpty() && permissible == null;
    }

    /**
     * Returns {@code true} if the given player inventory {@code slot} is admin-locked and its
     * slot should be excluded from the sort.
     *
     * @param slot the absolute player inventory slot index to test
     */
    public boolean blocks(int slot) {
        if (configSlots.contains(slot)) {
            return true;
        }

        if (permissible != null) {
            // Use isPermissionSet + hasPermission rather than hasPermission alone: undeclared
            // permission nodes fall back to PermissionDefault.OP in Bukkit, so a plain
            // hasPermission call would return true for every OP player even when no lock
            // node has been granted. isPermissionSet is true only when the node was explicitly
            // attached to the player, avoiding the OP-default false-positive.
            String node = Permissions.PERM_LOCK_PLAYER_SLOT + slot;
            if (Permissions.isExplicitlyGranted(permissible, node)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Factory: builds a {@code ProtectedSlots} snapshot from the cached config set and the
     * player (for the permission channel). Used by both the sort service and the lock GUI.
     *
     * @param who the player to check permission nodes against
     * @param cfg the loaded main config (provides the cached slot set)
     */
    public static ProtectedSlots forSort(Permissible who, MainConfig cfg) {
        Set<Integer> slots = cfg.getLockedPlayerSlots();
        if (slots.isEmpty() && who == null) {
            return EMPTY;
        }
        return new ProtectedSlots(slots, who);
    }
}
