package net.kccricket.clicksorted.text.lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Locale;

/**
 * A {@link MessageSource} with a {@link Locale} bound to it, so call sites resolve messages for a
 * specific player (or the server default) without threading the locale through every call.
 * Binding the locale here — rather than passing it per-call — means a scope that already captures
 * {@code var lang = configManager.lang();} only needs to change that one line (to
 * {@code configManager.lang(player.locale())}) to become locale-aware; every {@code
 * lang.getColoredMessage(...)} call in the scope is untouched.
 */
public record Localized(MessageSource source, Locale locale) {

    public Component getColoredMessage(String path, TagResolver... resolvers) {
        return source.getColoredMessage(locale, path, resolvers);
    }

    public String getMessage(String path) {
        return source.getMessage(locale, path);
    }

    public String getMessage(String path, String def) {
        String msg = source.getMessage(locale, path);
        return msg != null ? msg : def;
    }
}
