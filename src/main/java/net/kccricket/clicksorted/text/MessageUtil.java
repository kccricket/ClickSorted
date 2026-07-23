package net.kccricket.clicksorted.text;

import net.kccricket.clicksorted.config.ConfigManager;
import net.kccricket.kcmclib.logging.Log;
import net.kccricket.clicksorted.model.PreferenceResult;
import net.kccricket.kcmclib.text.Components;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.logging.Level;

public class MessageUtil {

    private static ConfigManager configManager;

    public static void init(ConfigManager cm) {
        configManager = cm;
    }

    private static String toPlain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static void errorMessage(Audience sender, Component component) {
        message(sender, component.colorIfAbsent(NamedTextColor.RED), Level.WARNING);
    }

    public static void errorMessage(Audience sender, String string) {
        errorMessage(sender, Component.text(string));
    }

    public static void statusMessage(Audience sender, Component component) {
        message(sender, component.colorIfAbsent(NamedTextColor.WHITE), Level.INFO);
    }

    public static void statusMessage(Audience sender, String string) {
        statusMessage(sender, Component.text(string));
    }

    public static void alertMessage(Audience sender, Component component) {
        message(sender, component.colorIfAbsent(NamedTextColor.YELLOW), Level.INFO);
    }

    /**
     * Reports a cancelled {@link PreferenceResult} to the player: the cancelling listener's
     * {@link PreferenceResult#cancelReason()} if it set one, otherwise the generic
     * {@code preferenceChangeBlocked} lang key. Callers should only invoke this when
     * {@link PreferenceResult#cancelled()} is {@code true}.
     */
    public static void preferenceBlocked(Audience sender, PreferenceResult result) {
        Component reason = result.cancelReason();
        var lang = sender instanceof Player p ? configManager.lang(p.locale()) : configManager.lang();
        errorMessage(sender, reason != null ? reason : lang.getColoredMessage("preferenceChangeBlocked"));
    }

    public static void rawMessage(Audience sender, Component component) {
        message(sender, component, null);
    }

    public static void rawMessage(Audience sender, String string) {
        rawMessage(sender, Component.text(string));
    }

    /**
     * Converts one or more lore {@link Component}s into a lore list suitable for
     * {@link org.bukkit.inventory.meta.ItemMeta#lore(List)}. Delegates to
     * {@link Components#toLore}.
     */
    public static List<Component> toLore(Component... lines) {
        return Components.toLore(lines);
    }

    /**
     * Splits a single {@link Component} on embedded {@code \n} characters and returns
     * one {@link Component} per resulting line, preserving inherited styles on each
     * segment. Delegates to {@link Components#splitLines}.
     */
    public static List<Component> splitLines(Component component) {
        return Components.splitLines(component);
    }

    /**
     * Builds the exact {@link Component} {@link #statusMessage} would send to {@code player} — WHITE
     * default color plus the locale-appropriate {@code prefix} lang key prepended. Used by call sites
     * that route the result through {@link net.kccricket.kcmclib.text.CooldownMessenger} instead of
     * sending directly, since that library messenger knows nothing of ClickSorted's lang system and
     * applies only its own WHITE default.
     */
    public static Component withPrefix(Player player, Component component) {
        Component colored = component.colorIfAbsent(NamedTextColor.WHITE);
        return configManager.lang(player.locale()).getColoredMessage("prefix").append(colored);
    }

    private static void message(Audience sender, Component component, Level level) {
        if (sender instanceof ConsoleCommandSender) {
            Log.log(level != null ? level : Level.INFO, toPlain(component));
        } else {
            Component out = component;
            if (level != null && configManager != null) {
                var lang = sender instanceof Player p ? configManager.lang(p.locale()) : configManager.lang();
                out = lang.getColoredMessage("prefix").append(component);
            }
            sender.sendMessage(out);
        }
    }
}
