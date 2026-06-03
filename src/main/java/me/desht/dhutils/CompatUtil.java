package me.desht.dhutils;

import org.bukkit.Bukkit;

public class CompatUtil {
    public static int GetMinecraftSubVersion() {
        return parseSubVersion(Bukkit.getServer().getBukkitVersion());
    }

    public static int parseSubVersion(String versionString) {
        String minecraftVersion = versionString.split("-")[0];
        String[] parts = minecraftVersion.split("\\.");
        // Old format: 1.X.Y → use X. New format: YEAR.MINOR.PATCH → use YEAR.
        if (parts[0].equals("1")) {
            if (parts.length < 2) {
                return 0;
            }
            return Integer.parseInt(parts[1]);
        }
        return Integer.parseInt(parts[0]);
    }

    public static boolean isMaterialIdAllowed() {
        return GetMinecraftSubVersion() <= 12;
    }

    public static boolean isMiddleClickAllowed() {
        return GetMinecraftSubVersion() <= 17;
    }

    public static boolean isSwapKeyAvailable() {
        return GetMinecraftSubVersion() >= 16;
    }
}
