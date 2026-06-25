package net.kccricket.clicksorted.security;

import net.kccricket.clicksorted.logging.Log;
import org.bukkit.command.CommandSender;

public class Permissions {

    // -------------------------------------------------------------------------
    // Admin-enforced "do not touch" blacklist permission-node prefixes
    // -------------------------------------------------------------------------

    /**
     * Prefix for material-based protection nodes. Append the lowercased {@link org.bukkit.Material}
     * name to form the full node, e.g. {@code clicksorted.blacklist.material.nether_star}.
     */
    public static final String PERM_BLACKLIST_MATERIAL = "clicksorted.blacklist.material.";

    /**
     * Prefix for display-name-based protection nodes. Append the slug returned by
     * {@link net.kccricket.clicksorted.sort.ProtectedItems#nameToken(String)} to form the full
     * node, e.g. {@code clicksorted.blacklist.name.creative_menu}.
     */
    public static final String PERM_BLACKLIST_NAME = "clicksorted.blacklist.name.";

    // -------------------------------------------------------------------------
    // Admin-enforced slot lock permission-node prefix
    // -------------------------------------------------------------------------

    /**
     * Prefix for admin player-slot-lock nodes. Append the slot index to form the full node,
     * e.g. {@code clicksorted.lock.player.slot.9}.
     *
     * <p>These are dynamic, undeclared nodes — intentionally not listed in {@code paper-plugin.yml}
     * so that {@code isPermissionSet} is {@code false} for OPs who have not been explicitly
     * granted the node (avoiding the {@link org.bukkit.permissions.PermissionDefault#OP} trap).
     *
     * <p>The {@code player} segment reserves the namespace for future lock categories
     * (e.g. {@code clicksorted.lock.container.*}).
     */
    public static final String PERM_LOCK_PLAYER_SLOT = "clicksorted.lock.player.slot.";

    // -------------------------------------------------------------------------

    /**
     * Check if the player has the specified permission node.
     *
     * @param sender Command sender (player or console) to check
     * @param node   Node to check for
     * @return true if the player has the permission node, false otherwise
     */
    public static boolean isAllowedTo(CommandSender sender, String node) {
        if (sender == null) {
            return true;
        }
        boolean allowed = sender.hasPermission(node);
        Log.debug("Permission check: player=" + sender.getName() + ", node=" + node
                + ", allowed=" + allowed);
        return allowed;
    }
}
