package net.kccricket.clicksorted;

import net.kccricket.clicksorted.migration.FileMigration;
import net.kccricket.clicksorted.migration.FileMigrationContext;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link FileMigration} factories, driven through a test
 * {@link FileMigrationContext} over a temp directory — proving the file-migration layer is
 * decoupled from any real plugin/{@code Plugin} instance.
 */
class FileMigrationTest {

    /** A {@link FileMigrationContext} backed by a temp dir and an in-memory resource map. */
    private record TestContext(Path dataFolder, Map<String, String> resources) implements FileMigrationContext {
        @Override
        public InputStream resource(String name) {
            String content = resources.get(name);
            return content == null ? null : new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void renameFile_movesWhenSourcePresentAndTargetAbsent(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("old.yml"), "key: value");
        FileMigrationContext ctx = new TestContext(dir, Map.of());

        boolean changed = FileMigration.renameFile("old.yml", "new.yml").apply(ctx);

        assertTrue(changed);
        assertFalse(Files.exists(dir.resolve("old.yml")));
        assertTrue(Files.exists(dir.resolve("new.yml")));
    }

    @Test
    void renameFile_noOpWhenSourceAbsent(@TempDir Path dir) throws IOException {
        FileMigrationContext ctx = new TestContext(dir, Map.of());
        assertFalse(FileMigration.renameFile("old.yml", "new.yml").apply(ctx));
    }

    @Test
    void renameFile_noOpWhenTargetAlreadyExists(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("old.yml"), "key: value");
        Files.writeString(dir.resolve("new.yml"), "key: existing");
        FileMigrationContext ctx = new TestContext(dir, Map.of());

        assertFalse(FileMigration.renameFile("old.yml", "new.yml").apply(ctx));
        assertEquals("key: existing", Files.readString(dir.resolve("new.yml")));
    }

    @Test
    void deleteFile_removesWhenPresent(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("stale.yml"), "key: value");
        FileMigrationContext ctx = new TestContext(dir, Map.of());

        assertTrue(FileMigration.deleteFile("stale.yml").apply(ctx));
        assertFalse(Files.exists(dir.resolve("stale.yml")));
    }

    @Test
    void deleteFile_noOpWhenAbsent(@TempDir Path dir) throws IOException {
        FileMigrationContext ctx = new TestContext(dir, Map.of());
        assertFalse(FileMigration.deleteFile("stale.yml").apply(ctx));
    }

    @Test
    void extractChangedKeys_keepsOnlyDifferingKeysAndArchivesSource(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("lang.yml"),
                "prefix: \"CUSTOM PREFIX\"\nsetEnabledStatus: \"Same as default\"\n");
        String bundledDefault = "prefix: \"DEFAULT PREFIX\"\nsetEnabledStatus: \"Same as default\"\n";

        FileMigrationContext ctx = new TestContext(dir, Map.of("lang/en_us.yml", bundledDefault));

        boolean changed = FileMigration.extractChangedKeys("lang.yml", "lang/en_us.yml", "lang/en_us.yml", ".bak")
                .apply(ctx);

        assertTrue(changed);
        assertTrue(Files.exists(dir.resolve("lang.yml.bak")), "Source must be archived");
        assertFalse(Files.exists(dir.resolve("lang.yml")), "Source must no longer exist under its original name");

        YamlConfiguration result = YamlConfiguration.loadConfiguration(dir.resolve("lang/en_us.yml").toFile());
        assertEquals("CUSTOM PREFIX", result.getString("prefix"), "The edited key must be carried forward");
        assertNull(result.getString("setEnabledStatus"),
                "The unedited (default-matching) key must be dropped, not carried forward");
    }

    @Test
    void extractChangedKeys_noOpWhenSourceAbsent(@TempDir Path dir) throws IOException {
        FileMigrationContext ctx = new TestContext(dir, Map.of("lang/en_us.yml", "prefix: \"x\"\n"));
        assertFalse(FileMigration.extractChangedKeys("lang.yml", "lang/en_us.yml", "lang/en_us.yml", ".bak").apply(ctx));
    }

    @Test
    void extractChangedKeys_noOpWhenTargetAlreadyExists(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("lang.yml"), "prefix: \"CUSTOM\"\n");
        Files.createDirectories(dir.resolve("lang"));
        Files.writeString(dir.resolve("lang/en_us.yml"), "prefix: \"ALREADY MIGRATED\"\n");
        FileMigrationContext ctx = new TestContext(dir, Map.of("lang/en_us.yml", "prefix: \"x\"\n"));

        assertFalse(FileMigration.extractChangedKeys("lang.yml", "lang/en_us.yml", "lang/en_us.yml", ".bak").apply(ctx));
        assertTrue(Files.exists(dir.resolve("lang.yml")), "Source must be left untouched on no-op");
    }
}
