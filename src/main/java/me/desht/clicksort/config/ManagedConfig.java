package me.desht.clicksort.config;

/**
 * Lifecycle contract for a single plugin configuration file.
 * All implementations route through {@link xyz.chengzi.clicksort.util.ResourceUpdater#update}
 * so the file is recreated from bundled defaults when missing (including mid-session deletes
 * followed by {@code /clicksort reload}).
 */
public interface ManagedConfig {

    /** The file name relative to the plugin data folder (e.g. {@code "config.yml"}). */
    String fileName();

    /** Load (or recreate-then-load) the file from disk. */
    void load();

    /** Reload the file. Delegates to {@link #load()} by default. */
    default void reload() {
        load();
    }

    /** Persist any in-memory mutations back to disk. No-op by default. */
    default void save() {}
}
