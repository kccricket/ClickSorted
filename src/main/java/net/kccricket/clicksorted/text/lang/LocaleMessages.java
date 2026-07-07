package net.kccricket.clicksorted.text.lang;

import net.kccricket.clicksorted.logging.Log;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable snapshot resolving a lang key across two layers (on-disk overrides, internal bundled
 * defaults) and a locale fallback chain, with no file I/O or plugin-lifecycle coupling of its own —
 * constructed directly from maps so it is unit-testable with no Bukkit server running.
 *
 * <h3>Resolution order</h3>
 * For a target locale token (e.g. {@code en_us}), its language-only token (e.g. {@code en}), and
 * the configured default-locale token {@code D}:
 * <pre>
 * override[token] -&gt; override[lang] -&gt; override[D]
 *   -&gt; internal[token] -&gt; internal[lang] -&gt; internal[D] -&gt; internal[en_us]
 *   -&gt; missing: logs severe and returns a visible placeholder
 * </pre>
 */
public final class LocaleMessages implements MessageSource {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String ULTIMATE_FALLBACK = "en_us";

    private final Map<String, FileConfiguration> internal;
    private final Map<String, FileConfiguration> overrides;
    private final Locale defaultLocale;
    private final String defaultToken;

    public LocaleMessages(Map<String, FileConfiguration> internal, Map<String, FileConfiguration> overrides,
                           Locale defaultLocale) {
        this.internal = Map.copyOf(internal);
        this.overrides = Map.copyOf(overrides);
        this.defaultLocale = defaultLocale;
        this.defaultToken = localeToken(defaultLocale);
    }

    /** {@code lang} or {@code lang_country}, lowercased ({@link Locale#ROOT}). */
    public static String localeToken(Locale locale) {
        String language = locale.getLanguage().toLowerCase(Locale.ROOT);
        String country = locale.getCountry().toLowerCase(Locale.ROOT);
        return country.isEmpty() ? language : language + "_" + country;
    }

    /** The language-only token (e.g. {@code en} from {@code en_us}). */
    private static String languageToken(String token) {
        int idx = token.indexOf('_');
        return idx < 0 ? token : token.substring(0, idx);
    }

    @Override
    public String getMessage(Locale locale, String path) {
        String token = localeToken(locale);
        String lang = languageToken(token);

        String value = fromMap(overrides, token, path);
        if (value == null && !lang.equals(token)) value = fromMap(overrides, lang, path);
        if (value == null) value = fromMap(overrides, defaultToken, path);

        if (value == null) value = fromMap(internal, token, path);
        if (value == null && !lang.equals(token)) value = fromMap(internal, lang, path);
        if (value == null) value = fromMap(internal, defaultToken, path);
        if (value == null && !ULTIMATE_FALLBACK.equals(defaultToken)) value = fromMap(internal, ULTIMATE_FALLBACK, path);

        return value;
    }

    private static String fromMap(Map<String, FileConfiguration> map, String token, String path) {
        FileConfiguration cfg = map.get(token);
        return cfg != null ? cfg.getString(path) : null;
    }

    @Override
    public Component getColoredMessage(Locale locale, String path, TagResolver... resolvers) {
        String raw = getMessage(locale, path);
        if (raw == null) {
            Log.severe("Missing lang key: " + path);
            raw = "<red>[missing lang key: " + path + "]";
        }
        return MM.deserialize(raw, resolvers);
    }

    @Override
    public Localized forLocale(Locale locale) {
        return new Localized(this, locale);
    }

    /** The configured default-locale-bound handle (used for console output and non-player senders). */
    public Localized defaultLocale() {
        return forLocale(defaultLocale);
    }

    /** Locale tokens with at least one internal (bundled) file — the set of "shippable" locales. */
    public List<String> internalTokens() {
        return List.copyOf(internal.keySet());
    }
}
