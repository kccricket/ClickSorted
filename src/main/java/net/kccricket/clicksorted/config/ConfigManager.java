package net.kccricket.clicksorted.config;

import net.kccricket.clicksorted.ClickSortedPlugin;
import net.kccricket.kcmclib.text.lang.Localized;

import java.util.Locale;

/**
 * Owns all four plugin configuration files and provides a single unified lifecycle:
 * {@link #loadAll()}, {@link #reloadAll()}, and {@link #saveAll()}.
 *
 * <p>{@link GroupsConfig} and {@link ItemsConfig} route through
 * {@link net.kccricket.kcmclib.config.ResourceUpdater#update} on load/reload, so their files are
 * recreated from bundled defaults if missing — including after a mid-session delete followed by
 * {@code /clicksorted reload}.
 */
public class ConfigManager {

    private final MainConfig main;
    private final GroupsConfig groups;
    private final LangConfig lang;
    private final ItemsConfig items;

    public ConfigManager(ClickSortedPlugin plugin) {
        this.main   = new MainConfig(plugin);
        this.groups = new GroupsConfig(plugin);
        this.lang   = new LangConfig(plugin);
        this.items  = new ItemsConfig(plugin);
    }

    /** Load all four config files. Called once from {@code onEnable}. */
    public void loadAll() {
        main.load();
        lang.load();
        groups.load();
        items.load();
    }

    /** Reload all four config files. Called by {@code /clicksorted reload}. */
    public void reloadAll() {
        main.reload();
        lang.reload();
        groups.reload();
        items.reload();
    }

    /** Persist any in-memory mutations. Called from {@code onDisable}. */
    public void saveAll() {
        main.save();
        groups.save();
        lang.save();
        items.save();
    }

    public MainConfig main()   { return main; }
    public GroupsConfig groups() { return groups; }
    public ItemsConfig items() { return items; }

    /** The default-locale-bound message handle — console output and any non-player sender. */
    public Localized lang() { return lang.defaultLocale(); }

    /** The message handle bound to {@code locale} — used for player-facing call sites. */
    public Localized lang(Locale locale) { return lang.forLocale(locale); }
}
