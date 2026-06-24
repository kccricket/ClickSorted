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
