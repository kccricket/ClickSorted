package net.kccricket.clicksorted.config;

import net.kccricket.clicksorted.ClickSortedPlugin;
import net.kccricket.kcmclib.config.ManagedConfig;
import net.kccricket.kcmclib.logging.Log;
import net.kccricket.kcmclib.text.lang.Localized;
import net.kccricket.kcmclib.text.lang.LocaleMessages;
import net.kccricket.kcmclib.text.lang.MessageSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads and swaps the plugin's {@link LocaleMessages} snapshot: internal (jar-bundled) defaults
 * under {@code lang/<locale>.yml} plus sparse on-disk overrides under
 * {@code plugins/ClickSorted/lang/<locale>.yml}. Unlike {@code groups.yml}/{@code items.yml}, lang
 * loading deliberately does <em>not</em> go through {@link net.kccricket.kcmclib.config.ResourceUpdater}'s
 * add-only merge — that
 * merge bakes every default onto disk, which would shadow future default changes forever. Instead
 * on-disk files stay sparse and missing keys always fall through to the internal default.
 *
 * <p>Follows the {@link GroupsConfig} volatile-swap pattern: a fresh {@link LocaleMessages} is
 * built locally on each {@link #load()} and published to the {@code volatile} field in one
 * assignment, so Folia region threads never observe a partially-built snapshot.
 */
public class LangConfig implements ManagedConfig, MessageSource {

    /** Locale tokens bundled inside the jar under {@code lang/<token>.yml}. Jars can't cheaply list a resource directory, so this is a maintained constant list. */
    private static final List<String> INTERNAL_LOCALES = List.of("en_us");

    private static final String DIR = "lang";

    private final ClickSortedPlugin plugin;
    // Reassigned on load/reload; read on Folia region threads via raw/render,
    // so publish via volatile (matches GroupsConfig/ItemsConfig).
    private volatile LocaleMessages messages = new LocaleMessages(Map.of(), Map.of(), Locale.US);

    public LangConfig(ClickSortedPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String fileName() {
        return DIR + "/en_us.yml";
    }

    @Override
    public void load() {
        File langDir = new File(plugin.getDataFolder(), DIR);
        if (!langDir.isDirectory() && !langDir.mkdirs()) {
            Log.warning("Failed to create lang directory: " + langDir);
        }

        writeCommentedTemplateIfAbsent(langDir);

        Map<String, FileConfiguration> internal = loadInternalDefaults();
        Map<String, FileConfiguration> overrides = loadOverrides(langDir);
        Locale defaultLocale = plugin.getConfigManager().main().getDefaultLocale();

        messages = new LocaleMessages(internal, overrides, defaultLocale);
    }

    private Map<String, FileConfiguration> loadInternalDefaults() {
        Map<String, FileConfiguration> internal = new HashMap<>();
        for (String token : INTERNAL_LOCALES) {
            String resourceName = DIR + "/" + token + ".yml";
            try (InputStream stream = plugin.getResource(resourceName)) {
                if (stream == null) {
                    Log.warning("Bundled internal lang resource missing: " + resourceName);
                    continue;
                }
                internal.put(token, YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)));
            } catch (IOException e) {
                Log.warning("Failed to close internal lang resource stream: " + resourceName, e);
            }
        }
        return internal;
    }

    private Map<String, FileConfiguration> loadOverrides(File langDir) {
        Map<String, FileConfiguration> overrides = new HashMap<>();
        File[] files = langDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return overrides;
        }
        for (File file : files) {
            String name = file.getName();
            String token = name.substring(0, name.length() - ".yml".length()).toLowerCase(Locale.ROOT);
            overrides.put(token, YamlConfiguration.loadConfiguration(file));
        }
        return overrides;
    }

    /**
     * Writes a fully-commented copy of the internal {@code en_us.yml} to the data folder, as
     * documentation of every available key. Every non-blank, non-comment line is prefixed with
     * {@code "# "} so the file parses to an empty config and overrides nothing by default. Never
     * clobbers an existing file (an admin override, or a migrated legacy file).
     */
    private void writeCommentedTemplateIfAbsent(File langDir) {
        File target = new File(langDir, "en_us.yml");
        if (target.exists()) {
            return;
        }
        try (InputStream in = plugin.getResource(DIR + "/en_us.yml")) {
            if (in == null) {
                Log.warning("Bundled internal lang resource missing: " + DIR + "/en_us.yml");
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                 BufferedWriter writer = Files.newBufferedWriter(target.toPath(), StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        writer.write(line);
                    } else {
                        writer.write("# " + line);
                    }
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            Log.warning("Failed to write commented lang template: " + target, e);
        }
    }

    @Override
    public String raw(Locale locale, String path) {
        return messages.raw(locale, path);
    }

    @Override
    public Component render(Locale locale, String path, TagResolver... resolvers) {
        return messages.render(locale, path, resolvers);
    }

    @Override
    public Localized forLocale(Locale locale) {
        return messages.forLocale(locale);
    }

    /** The default-locale-bound handle, for console output and any non-player sender. */
    public Localized defaultLocale() {
        return messages.defaultLocale();
    }
}
