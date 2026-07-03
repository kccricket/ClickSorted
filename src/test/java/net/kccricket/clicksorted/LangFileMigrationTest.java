package net.kccricket.clicksorted;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end coverage of the legacy {@code lang.yml} → {@code lang/en_us.yml} file migration,
 * driven through the real catalog entry in {@code Migrations#FILE_MIGRATIONS} (not a
 * reimplementation) — proving the declarative one-line catalog append actually relocates an
 * upgrader's customised file while keeping only the keys they changed.
 */
class LangFileMigrationTest extends AbstractClickSortedTest {

    @Test
    void legacyLangFile_migratesOnlyChangedKeysAndArchivesTheRest() throws IOException {
        File dataFolder = plugin.getDataFolder();
        File langDir = new File(dataFolder, "lang");

        // Start from a clean slate: no lang/ directory at all, as if this were a pre-i18n install.
        deleteRecursively(langDir);
        assertFalse(langDir.exists());

        // A legacy lang.yml as copyDefaults would have produced it: every key present, one of them
        // (actionTooFast) hand-edited by the admin, the rest left at their shipped values.
        File legacy = new File(dataFolder, "lang.yml");
        Files.writeString(legacy.toPath(),
                "prefix: \"<gray>[<aqua>ClickSorted<gray>] \"\n" // matches the bundled default verbatim
                        + "actionTooFast: \"<red>WOAH THERE, SLOW DOWN\"\n", // admin's customisation
                StandardCharsets.UTF_8);

        plugin.getMigrations().migrateFiles();

        assertFalse(legacy.exists(), "legacy lang.yml must be moved away");
        assertTrue(new File(dataFolder, "lang.yml.bak").exists(), "legacy lang.yml must be archived as .bak");

        File override = new File(langDir, "en_us.yml");
        assertTrue(override.exists());
        String overrideContent = Files.readString(override.toPath());
        assertTrue(overrideContent.contains("WOAH THERE"), "the admin's changed key must survive the migration");
        assertFalse(overrideContent.contains("ClickSorted<gray>"),
                "a key matching the bundled default must NOT be carried forward — it should track future updates");

        // Reload LangConfig so it picks up the migrated override file, then verify resolution.
        plugin.getConfigManager().lang().load();
        Component msg = plugin.getConfigManager().lang().getColoredMessage("actionTooFast");
        assertTrue(PlainTextComponentSerializer.plainText().serialize(msg).contains("WOAH THERE"));
    }

    @Test
    void migrateFiles_isIdempotent() throws IOException {
        // Running migrateFiles() again (e.g. a second /clicksorted admin reload, or just a normal
        // subsequent enable) must not error or reprocess an already-migrated install.
        assertDoesNotThrow(() -> plugin.getMigrations().migrateFiles());
        assertDoesNotThrow(() -> plugin.getMigrations().migrateFiles());
    }

    private static void deleteRecursively(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        f.delete();
    }
}
