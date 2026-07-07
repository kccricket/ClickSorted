package net.kccricket.clicksorted.text;

import net.kccricket.clicksorted.config.ConfigManager;
import net.kccricket.clicksorted.logging.Log;
import net.kccricket.clicksorted.model.PreferenceResult;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
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
     * {@link org.bukkit.inventory.meta.ItemMeta#lore(List)}.
     *
     * <p>Each component is split on embedded newline ({@code \n}) boundaries so that
     * multi-line lore strings authored with the {@code <newline>} MiniMessage tag are
     * rendered as separate visual lines by Minecraft. Inherited style (color,
     * decoration, etc.) is carried forward to continuation lines.
     *
     * @param lines one or more lore components to flatten into the result list
     * @return a {@code List<Component>} with one entry per visual lore line
     */
    public static List<Component> toLore(Component... lines) {
        List<Component> result = new ArrayList<>();
        for (Component line : lines) {
            result.addAll(splitLines(line));
        }
        return result;
    }

    /**
     * Splits a single {@link Component} on embedded {@code \n} characters and returns
     * one {@link Component} per resulting line, preserving inherited styles on each
     * segment.
     */
    public static List<Component> splitLines(Component component) {
        List<TextComponent.Builder> builders = new ArrayList<>();
        builders.add(Component.text());
        walk(component, Style.empty(), builders);
        return builders.stream().map(b -> (Component) b.build()).toList();
    }

    private static void walk(Component comp, Style inherited, List<TextComponent.Builder> lines) {
        // Child's explicit style values win; inherited fills any gaps.
        Style merged = comp.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (comp instanceof TextComponent text) {
            String[] parts = text.content().split("\n", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    lines.add(Component.text());
                }
                if (!parts[i].isEmpty()) {
                    lines.get(lines.size() - 1)
                         .append(Component.text(parts[i]).style(merged));
                }
            }
        } else {
            // Non-text leaf (translatable, keybind, etc.): append without children so
            // the recursion below handles them independently.
            lines.get(lines.size() - 1).append(comp.children(List.of()).style(merged));
        }
        for (Component child : comp.children()) {
            walk(child, merged, lines);
        }
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
