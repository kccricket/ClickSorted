package net.kccricket.clicksort.text;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;

public class CooldownMessenger {
    private final Map<String, Long> cooldowns = new HashMap<>();

    public void message(CommandSender sender, String cooldown, int secs, Component message) {
        long last = getLast(sender, cooldown);
        if (System.currentTimeMillis() - last > secs * 1000L) {
            MessageUtil.statusMessage(sender, message);
            cooldowns.put(sender.getName() + "." + cooldown, System.currentTimeMillis());
        }
    }

    public void message(CommandSender sender, String cooldown, int secs, String message) {
        message(sender, cooldown, secs, Component.text(message));
    }

    private long getLast(CommandSender sender, String cooldown) {
        String key = sender.getName() + "." + cooldown;
        if (cooldowns.containsKey(key)) {
            return cooldowns.get(key);
        } else {
            return 0;
        }
    }
}
