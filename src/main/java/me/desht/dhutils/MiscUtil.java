package me.desht.dhutils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.util.*;
import java.util.logging.Level;

public class MiscUtil {

    public static String toPlain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static void errorMessage(CommandSender sender, Component component) {
        message(sender, component.colorIfAbsent(NamedTextColor.RED), Level.WARNING);
    }

    public static void errorMessage(CommandSender sender, String string) {
        errorMessage(sender, Component.text(string));
    }

    public static void statusMessage(CommandSender sender, Component component) {
        message(sender, component.colorIfAbsent(NamedTextColor.AQUA), Level.INFO);
    }

    public static void statusMessage(CommandSender sender, String string) {
        statusMessage(sender, Component.text(string));
    }

    public static void alertMessage(CommandSender sender, Component component) {
        message(sender, component.colorIfAbsent(NamedTextColor.YELLOW), Level.INFO);
    }

    public static void alertMessage(CommandSender sender, String string) {
        alertMessage(sender, Component.text(string));
    }

    public static void generalMessage(CommandSender sender, Component component) {
        message(sender, component.colorIfAbsent(NamedTextColor.WHITE), Level.INFO);
    }

    public static void generalMessage(CommandSender sender, String string) {
        generalMessage(sender, Component.text(string));
    }

    public static void rawMessage(CommandSender sender, Component component) {
        message(sender, component, null);
    }

    public static void rawMessage(CommandSender sender, String string) {
        rawMessage(sender, Component.text(string));
    }

    private static void message(CommandSender sender, Component component, Level level) {
        if (sender instanceof ConsoleCommandSender) {
            LogUtils.log(level != null ? level : Level.INFO, toPlain(component));
        } else {
            sender.sendMessage(component);
        }
    }

    public static List<String> splitQuotedString(String s) {
        List<String> matchList = new ArrayList<>();
        java.util.regex.Pattern regex = java.util.regex.Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'");
        java.util.regex.Matcher regexMatcher = regex.matcher(s);
        while (regexMatcher.find()) {
            if (regexMatcher.group(1) != null) {
                matchList.add(regexMatcher.group(1));
            } else if (regexMatcher.group(2) != null) {
                matchList.add(regexMatcher.group(2));
            } else {
                matchList.add(regexMatcher.group());
            }
        }
        return matchList;
    }

    public static <T extends Comparable<? super T>> List<T> asSortedList(Collection<T> c) {
        List<T> list = new ArrayList<>(c);
        Collections.sort(list);
        return list;
    }
}
