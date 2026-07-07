package net.kccricket.clicksorted.migration;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * Store-neutral handle for a {@link FileMigration}: the plugin data folder to migrate files
 * within, and access to bundled jar resources to diff against. Free of any ClickSorted or plugin
 * type so the file-migration layer stays library-extractable; {@link Migrations} supplies the
 * adapter over the real {@code Plugin}.
 */
public interface FileMigrationContext {

    /** The plugin's data folder — the root that relative paths in a {@link FileMigration} resolve against. */
    Path dataFolder();

    /** A bundled jar resource by path (e.g. {@code "lang/en_us.yml"}), or {@code null} if absent. */
    InputStream resource(String name);
}
