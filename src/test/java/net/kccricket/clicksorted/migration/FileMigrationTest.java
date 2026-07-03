package net.kccricket.clicksorted.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit coverage for the {@link FileMigration} factories, driven through a bare-bones
 * {@link FileMigrationContext} over a temp directory rather than a running plugin — proving the
 * factories are genuinely decoupled from any ClickSorted/plugin type (the whole point of pulling
 * this out as its own reusable layer alongside {@link Migration}/{@link Store}).
 */
class FileMigrationTest {

    /** A {@link FileMigrationContext} with no bundled resources — {@link #resource} always returns null. */
    private record EmptyResourceContext(Path dataFolder) implements FileMigrationContext {
        @Override
        public InputStream resource(String path) {
            return null;
        }
    }

    /** A {@link FileMigrationContext} whose "bundled resources" are files under a second temp root. */
    private record MapResourceContext(Path dataFolder, Path resourceRoot) implements FileMigrationContext {
        @Override
        public InputStream resource(String path) throws IOException {
            Path p = resourceRoot.resolve(path);
            return Files.exists(p) ? Files.newInputStream(p) : null;
        }
    }

    @Test
    void renameFile_movesWhenSourcePresentAndTargetAbsent(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("old.yml"), "k: v");
        FileMigrationContext ctx = new EmptyResourceContext(dir);

        boolean changed = FileMigration.renameFile("old.yml", "new.yml").apply(ctx);

        assertTrue(changed);
        assertFalse(Files.exists(dir.resolve("old.yml")));
        assertTrue(Files.exists(dir.resolve("new.yml")));
    }

    @Test
    void renameFile_noOpWhenSourceAbsent(@TempDir Path dir) throws IOException {
        FileMigrationContext ctx = new EmptyResourceContext(dir);
        boolean changed = FileMigration.renameFile("missing.yml", "new.yml").apply(ctx);
        assertFalse(changed);
        assertFalse(Files.exists(dir.resolve("new.yml")));
    }

    @Test
    void renameFile_noOpWhenTargetAlreadyExists(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("old.yml"), "k: v");
        Files.writeString(dir.resolve("new.yml"), "existing: true");
        FileMigrationContext ctx = new EmptyResourceContext(dir);

        boolean changed = FileMigration.renameFile("old.yml", "new.yml").apply(ctx);

        assertFalse(changed, "Must never clobber an existing target");
        assertTrue(Files.exists(dir.resolve("old.yml")), "Source must be untouched when the target already exists");
        assertEquals("existing: true", Files.readString(dir.resolve("new.yml")));
    }

    @Test
    void deleteFile_removesWhenPresent(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("gone.yml"), "k: v");
        FileMigrationContext ctx = new EmptyResourceContext(dir);

        assertTrue(FileMigration.deleteFile("gone.yml").apply(ctx));
        assertFalse(Files.exists(dir.resolve("gone.yml")));
    }

    @Test
    void deleteFile_noOpWhenAbsent(@TempDir Path dir) throws IOException {
        FileMigrationContext ctx = new EmptyResourceContext(dir);
        assertFalse(FileMigration.deleteFile("gone.yml").apply(ctx));
    }

    @Test
    void extractChangedKeys_keepsOnlyDifferingKeysAndArchivesSource(@TempDir Path dataDir, @TempDir Path resDir)
            throws IOException {
        Files.writeString(resDir.resolve("default.yml"),
                "unchanged: \"same value\"\nchanged: \"default value\"\n", StandardCharsets.UTF_8);
        Files.writeString(dataDir.resolve("legacy.yml"),
                "unchanged: \"same value\"\nchanged: \"custom value\"\n", StandardCharsets.UTF_8);
        FileMigrationContext ctx = new MapResourceContext(dataDir, resDir);

        boolean changed = FileMigration.extractChangedKeys("legacy.yml", "default.yml", "override.yml", ".bak")
                .apply(ctx);

        assertTrue(changed);
        assertFalse(Files.exists(dataDir.resolve("legacy.yml")), "Source must be archived away");
        assertTrue(Files.exists(dataDir.resolve("legacy.yml.bak")), "Source must be preserved under the archive suffix");

        org.bukkit.configuration.file.YamlConfiguration result =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dataDir.resolve("override.yml").toFile());
        assertEquals("custom value", result.getString("changed"), "A key that differs from the default must survive");
        assertNull(result.getString("unchanged"), "A key matching the default must be dropped, not carried forward");
    }

    @Test
    void extractChangedKeys_noOpWhenSourceAbsent(@TempDir Path dataDir, @TempDir Path resDir) throws IOException {
        FileMigrationContext ctx = new MapResourceContext(dataDir, resDir);
        boolean changed = FileMigration.extractChangedKeys("legacy.yml", "default.yml", "override.yml", ".bak").apply(ctx);
        assertFalse(changed);
    }

    @Test
    void extractChangedKeys_noOpWhenTargetAlreadyExists(@TempDir Path dataDir, @TempDir Path resDir) throws IOException {
        Files.writeString(resDir.resolve("default.yml"), "k: \"default\"\n", StandardCharsets.UTF_8);
        Files.writeString(dataDir.resolve("legacy.yml"), "k: \"custom\"\n", StandardCharsets.UTF_8);
        Files.writeString(dataDir.resolve("override.yml"), "already: \"here\"\n", StandardCharsets.UTF_8);
        FileMigrationContext ctx = new MapResourceContext(dataDir, resDir);

        boolean changed = FileMigration.extractChangedKeys("legacy.yml", "default.yml", "override.yml", ".bak").apply(ctx);

        assertFalse(changed, "Must never clobber an existing target");
        assertTrue(Files.exists(dataDir.resolve("legacy.yml")), "Source must be untouched when the target already exists");
    }
}
