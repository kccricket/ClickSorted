package net.kccricket.clicksorted.config;

import net.kccricket.clicksorted.ClickSortedPlugin;
import net.kccricket.clicksorted.logging.Log;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages ClickSorted's localised messages, rendered via MiniMessage.
 *
 * <p>Messages resolve from two layers:
 * <ul>
 *   <li><b>Internal defaults</b> — bundled in the jar under {@code lang/*.yml}. This is the
 *       single source of truth; a value changed here reaches every server on the next release,
 *       no admin action required.</li>
 *   <li><b>On-disk overrides</b> — sparse {@code plugins/ClickSorted/lang/*.yml} files. Only keys
 *       present there override the internal default; any key an admin never touches keeps
 *       tracking future plugin updates.</li>
 * </ul>
 *
 * <p>Per-player localisation resolves against the player's Minecraft client locale (see
 * {@link #getColoredMessage(Locale, String, TagResolver...)}), falling back through the bare
 * language, the server's {@code default_locale}, and finally the built-in {@code en_us} default.
 * See {@code docs/admin/lang.md} for the full resolution order.
 */
public class LangConfig implements ManagedConfig {

    private static final String DIR_NAME = "lang";
    private static final String DEFAULT_TOKEN = "en_us";
    /** Locales bundled in the jar. Jars can't cheaply list a resource directory, so this is explicit. */
    private static final List<String> BUNDLED_LOCALES = List.of(DEFAULT_TOKEN);

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ClickSortedPlugin plugin;

    // Both swapped atomically on reload (Folia-safe: readers may run on region threads while
    // load() runs on another thread), keyed by lowercase locale token (e.g. "en_us", "en").
    private volatile Map<String, YamlConfiguration> internalDefaults = Map.of();
    private volatile Map<String, YamlConfiguration> overrides = Map.of();

    public LangConfig(ClickSortedPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String fileName() {
        return DIR_NAME;
    }

    @Override
    public void load() {
        Path dir = plugin.getDataFolder().toPath().resolve(DIR_NAME);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            Log.warning("Failed to create lang/ directory: " + e.getMessage(), e);
        }

        internalDefaults = loadInternalDefaults();
        ensureTemplate(dir);
        overrides = loadOverrides(dir);
    }

    private Map<String, YamlConfiguration> loadInternalDefaults() {
        Map<String, YamlConfiguration> loaded = new HashMap<>();
        for (String token : BUNDLED_LOCALES) {
            String resourcePath = DIR_NAME + "/" + token + ".yml";
            try (InputStream in = plugin.getResource(resourcePath)) {
                if (in == null) {
                    Log.warning("Missing bundled lang resource: " + resourcePath);
                    continue;
                }
                YamlConfiguration cfg = new YamlConfiguration();
                cfg.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                loaded.put(token, cfg);
            } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
                Log.warning("Failed to load bundled lang resource " + resourcePath + ": " + e.getMessage(), e);
            }
        }
        return Map.copyOf(loaded);
    }

    private Map<String, YamlConfiguration> loadOverrides(Path dir) {
        Map<String, YamlConfiguration> loaded = new HashMap<>();
        File[] files = dir.toFile().listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                String token = name.substring(0, name.length() - ".yml".length()).toLowerCase(Locale.ROOT);
                loaded.put(token, YamlConfiguration.loadConfiguration(f));
            }
        }
        return Map.copyOf(loaded);
    }

    /**
     * Writes a fully-commented {@code lang/en_us.yml} template (generated from the bundled
     * default) the first time no on-disk override exists yet, purely as documentation/scaffold —
     * every key starts disabled, so it contributes no overrides until an admin uncomments one.
     * Never overwrites an existing file.
     */
    private void ensureTemplate(Path dir) {
        Path target = dir.resolve(DEFAULT_TOKEN + ".yml");
        if (Files.exists(target)) {
            return;
        }
        String resourcePath = DIR_NAME + "/" + DEFAULT_TOKEN + ".yml";
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                return;
            }
            List<String> lines;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                lines = reader.lines().collect(Collectors.toList());
            }

            StringBuilder sb = new StringBuilder();
            sb.append("# This file overrides ClickSorted's built-in lang defaults for locale: en_us.\n")
              .append("#\n")
              .append("# Every key below is commented out, so nothing here overrides anything yet — the plugin's\n")
              .append("# built-in default is used instead, and keeps tracking future plugin updates. Uncomment and\n")
              .append("# edit only the keys you want to change. See docs/admin/lang.md for details.\n")
              .append("#\n");
            for (String line : lines) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    sb.append(line).append('\n');
                } else {
                    sb.append("# ").append(line).append('\n');
                }
            }
            Files.writeString(target, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.warning("Failed to write lang template " + target + ": " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    /** Lowercases a {@link Locale} to its Minecraft-style token: {@code lang} or {@code lang_country}. */
    static String localeToken(Locale locale) {
        String lang = locale.getLanguage().toLowerCase(Locale.ROOT);
        String country = locale.getCountry().toLowerCase(Locale.ROOT);
        return country.isEmpty() ? lang : lang + "_" + country;
    }

    private Locale defaultLocale() {
        return plugin.getConfigManager().main().getDefaultLocale();
    }

    /**
     * Resolves {@code path} for {@code locale}: override[locale] → override[lang] →
     * override[default] → internal[locale] → internal[lang] → internal[default] →
     * internal[en_us] → {@code null} if nowhere defines it.
     */
    private String lookup(Locale locale, String path) {
        Locale effective = locale != null ? locale : defaultLocale();
        Locale defLocale = defaultLocale();

        String token = localeToken(effective);
        String lang = effective.getLanguage().toLowerCase(Locale.ROOT);
        String defToken = localeToken(defLocale);
        String defLang = defLocale.getLanguage().toLowerCase(Locale.ROOT);

        for (String candidate : orderedTokens(token, lang, defToken)) {
            String v = get(overrides, candidate, path);
            if (v != null) return v;
        }
        for (String candidate : orderedTokens(token, lang, defToken, defLang, DEFAULT_TOKEN)) {
            String v = get(internalDefaults, candidate, path);
            if (v != null) return v;
        }
        return null;
    }

    private static Set<String> orderedTokens(String... tokens) {
        Set<String> ordered = new LinkedHashSet<>();
        for (String t : tokens) {
            if (t != null) ordered.add(t);
        }
        return ordered;
    }

    private static String get(Map<String, YamlConfiguration> map, String token, String path) {
        YamlConfiguration cfg = map.get(token);
        return cfg != null ? cfg.getString(path) : null;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Raw string for {@code path}, resolved for the server's {@code default_locale}. */
    public String getMessage(String path) {
        return getMessage((Locale) null, path);
    }

    /** Raw string for {@code path}, resolved for {@code locale} (or the default locale if {@code null}). */
    public String getMessage(Locale locale, String path) {
        return lookup(locale, path);
    }

    /** Raw string for {@code path} (default locale), or {@code def} if undefined anywhere. */
    public String getMessage(String path, String def) {
        String msg = getMessage(path);
        return msg != null ? msg : def;
    }

    /** Rendered {@link Component} for {@code path}, resolved for the server's {@code default_locale}. */
    public Component getColoredMessage(String path, TagResolver... resolvers) {
        return getColoredMessage((Locale) null, path, resolvers);
    }

    /**
     * Rendered {@link Component} for {@code path}, resolved for {@code locale} (typically a
     * player's {@code player.locale()}), or the server's {@code default_locale} if {@code null}.
     * Logs and substitutes a visible placeholder when the key is undefined in every tier.
     */
    public Component getColoredMessage(Locale locale, String path, TagResolver... resolvers) {
        String raw = lookup(locale, path);
        if (raw == null) {
            Log.severe("Missing lang key: " + path);
            raw = "<red>[missing lang key: " + path + "]";
        }
        return MM.deserialize(raw, resolvers);
    }
}
