package net.kccricket.clicksorted.migration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Store-neutral handle for file-level migrations — moving, deleting, or partially relocating
 * files in a plugin's data folder against the plugin's own bundled resources.
 *
 * <p>Deliberately free of any ClickSorted-specific type (no {@code Plugin}, no
 * {@code ClickSortedPlugin}) so {@link FileMigration} and its factories can be lifted into a
 * shared library later without carrying any plugin dependency along.
 */
public interface FileMigrationContext {

    /** The plugin's data folder — the root that all relative paths in {@link FileMigration} resolve against. */
    Path dataFolder();

    /**
     * Opens a bundled resource (from the plugin jar) by path, or {@code null} if no such resource
     * exists. Caller is responsible for closing the returned stream.
     */
    InputStream resource(String path) throws IOException;
}
