package net.kccricket.clicksorted.text;

import net.kccricket.clicksorted.logging.Log;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.ConsoleCommandSender;

import java.util.logging.Level;

public class MessageUtil {

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
        message(sender, component.colorIfAbsent(NamedTextColor.AQUA), Level.INFO);
    }

    public static void statusMessage(Audience sender, String string) {
        statusMessage(sender, Component.text(string));
    }

    public static void alertMessage(Audience sender, Component component) {
        message(sender, component.colorIfAbsent(NamedTextColor.YELLOW), Level.INFO);
    }

    public static void rawMessage(Audience sender, Component component) {
        message(sender, component, null);
    }

    public static void rawMessage(Audience sender, String string) {
        rawMessage(sender, Component.text(string));
    }

    private static void message(Audience sender, Component component, Level level) {
        if (sender instanceof ConsoleCommandSender) {
            Log.log(level != null ? level : Level.INFO, toPlain(component));
        } else {
            sender.sendMessage(component);
        }
    }
}
