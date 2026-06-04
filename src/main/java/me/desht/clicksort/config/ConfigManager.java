package me.desht.clicksort.config;

import me.desht.clicksort.ClickSortPlugin;

/**
 * Owns all four plugin configuration files and provides a single unified lifecycle:
 * {@link #loadAll()}, {@link #reloadAll()}, and {@link #saveAll()}.
 *
 * <p>Every file routes through {@link xyz.chengzi.clicksort.util.ResourceUpdater#update}
 * on load/reload, so missing files are recreated from bundled defaults — including after
 * a mid-session delete followed by {@code /clicksort reload}.
 */
public class ConfigManager {

    private final MainConfig main;
    private final GroupsConfig groups;
    private final LangConfig lang;
    private final ItemsConfig items;

    public ConfigManager(ClickSortPlugin plugin) {
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

    /** Reload all four config files. Called by {@code /clicksort reload}. */
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
    public LangConfig lang()   { return lang; }
    public ItemsConfig items() { return items; }
}
