package net.kccricket.clicksorted.text.lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Locale;

/**
 * Store-neutral, plugin-agnostic contract for resolving a lang key to a message for a given
 * {@link Locale}. Knows nothing about files, {@code Plugin}, or any ClickSorted-specific type, so
 * it can be lifted into a shared library with no lifecycle coupling.
 */
public interface MessageSource {

    /** The raw (unrendered) string stored at {@code path} for {@code locale}, or {@code null} if absent. */
    String getMessage(Locale locale, String path);

    /** Renders {@code path} for {@code locale} as MiniMessage, substituting {@code resolvers}. */
    Component getColoredMessage(Locale locale, String path, TagResolver... resolvers);

    /** A handle with {@code locale} pre-bound, so call sites don't repeat it on every call. */
    Localized forLocale(Locale locale);
}
