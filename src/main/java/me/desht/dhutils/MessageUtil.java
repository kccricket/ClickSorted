package me.desht.dhutils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.util.logging.Level;

public class MessageUtil {

    private static String toPlain(Component component) {
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

    public static void rawMessage(CommandSender sender, Component component) {
        message(sender, component, null);
    }

    public static void rawMessage(CommandSender sender, String string) {
        rawMessage(sender, Component.text(string));
    }

    private static void message(CommandSender sender, Component component, Level level) {
        if (sender instanceof ConsoleCommandSender) {
            Log.log(level != null ? level : Level.INFO, toPlain(component));
        } else {
            sender.sendMessage(component);
        }
    }
}
