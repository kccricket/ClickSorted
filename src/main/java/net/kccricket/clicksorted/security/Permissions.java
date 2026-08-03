package net.kccricket.clicksorted.security;

import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permissible;

/**
 * ClickSorted's permission-node constants. The generic checks ({@link #isAllowedTo},
 * {@link #isExplicitlyGranted}) delegate to the store-neutral, plugin-agnostic helpers in
 * {@link net.kccricket.kcmclib.security.Permissions}.
 */
public class Permissions {

    // -------------------------------------------------------------------------
    // Master kill-switch
    // -------------------------------------------------------------------------

    /**
     * Top-level master kill-switch for all player-facing ClickSorted features (click-triggered
     * sorting, bundle packing, and player commands). Default {@code true}. Denying this node
     * disables everything for the player in one move; it has no effect on admin access, which
     * is gated independently by {@code clicksorted.admin.commands.*}.
     */
    public static final String PERM_MASTER = "clicksorted";

    // -------------------------------------------------------------------------
    // /clicksorted admin subcommand nodes
    // -------------------------------------------------------------------------

    /** Allow use of {@code /clicksorted admin reload}. */
    public static final String PERM_ADMIN_RELOAD = "clicksorted.admin.commands.reload";
    /** Allow use of {@code /clicksorted admin config}. */
    public static final String PERM_ADMIN_CONFIG = "clicksorted.admin.commands.config";
    /** Allow use of {@code /clicksorted admin debug}. */
    public static final String PERM_ADMIN_DEBUG = "clicksorted.admin.commands.debug";
    /** Allow use of {@code /clicksorted admin benchmark} (also requires {@code enable_benchmark} in config.yml). */
    public static final String PERM_ADMIN_BENCHMARK = "clicksorted.admin.commands.benchmark";
    /** Allow use of {@code /clicksorted admin selftest} (also requires {@code enable_selftest} in config.yml). */
    public static final String PERM_ADMIN_SELFTEST = "clicksorted.admin.commands.selftest";

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
     * Returns {@code true} only when {@code node} is explicitly attached to {@code who} AND
     * grants the permission. Uses {@code isPermissionSet} before {@code hasPermission} to avoid
     * Bukkit's {@link org.bukkit.permissions.PermissionDefault#OP} trap: undeclared nodes return
     * {@code true} for OPs via {@code hasPermission} alone, so we guard with {@code isPermissionSet}
     * first to ensure only explicitly-granted nodes trigger the check.
     */
    public static boolean isExplicitlyGranted(Permissible who, String node) {
        return net.kccricket.kcmclib.security.Permissions.isExplicitlyGranted(who, node);
    }

    /**
     * Check if the player has the specified permission node.
     *
     * @param sender Command sender (player or console) to check
     * @param node   Node to check for
     * @return true if the player has the permission node, false otherwise
     */
    public static boolean isAllowedTo(CommandSender sender, String node) {
        return net.kccricket.kcmclib.security.Permissions.isAllowedTo(sender, node);
    }
}
