package me.desht.dhutils;

import org.bukkit.command.CommandSender;

public class Permissions {
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
