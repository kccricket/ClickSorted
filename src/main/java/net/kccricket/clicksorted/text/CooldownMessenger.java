package net.kccricket.clicksorted.text;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownMessenger {
    private final Map<String, Long> cooldowns = new HashMap<>();

    public void message(Player player, String cooldown, int secs, Component message) {
        long last = getLast(player, cooldown);
        if (System.currentTimeMillis() - last > secs * 1000L) {
            MessageUtil.statusMessage(player, message);
            cooldowns.put(key(player, cooldown), System.currentTimeMillis());
        }
    }

    public void message(Player player, String cooldown, int secs, String message) {
        message(player, cooldown, secs, Component.text(message));
    }

    private long getLast(Player player, String cooldown) {
        return cooldowns.getOrDefault(key(player, cooldown), 0L);
    }

    private static String key(Player player, String cooldown) {
        UUID id = player.getUniqueId();
        return id + "." + cooldown;
    }
}
