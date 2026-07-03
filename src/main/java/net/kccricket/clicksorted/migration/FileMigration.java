package net.kccricket.clicksorted.migration;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * A composable, store-neutral file migration rule: {@link #apply} returns {@code true} if it
 * changed anything on disk. Mirrors {@link Migration}'s shape (a functional interface plus static
 * factories) but operates on files in the data folder rather than key/value stores, via
 * {@link FileMigrationContext}.
 *
 * <p>Every factory is idempotent — safe to run on every startup — and a no-op when its
 * precondition (source present / target absent) doesn't hold, so a growing
 * {@code List<FileMigration>} catalog (see {@code Migrations#FILE_MIGRATIONS}) can be replayed
 * indefinitely without side effects after the first successful run.
 */
@FunctionalInterface
public interface FileMigration {
    boolean apply(FileMigrationContext ctx) throws IOException;

    /**
     * Moves {@code from} (relative to the data folder) to {@code to}. No-op when {@code from} is
     * absent or {@code to} already exists.
     */
    static FileMigration renameFile(String from, String to) {
        return ctx -> {
            Path source = ctx.dataFolder().resolve(from);
            Path target = ctx.dataFolder().resolve(to);
            if (!Files.exists(source) || Files.exists(target)) {
                return false;
            }
            Files.createDirectories(target.getParent());
            Files.move(source, target);
            return true;
        };
    }

    /** Deletes {@code path} (relative to the data folder). No-op when absent. */
    static FileMigration deleteFile(String path) {
        return ctx -> Files.deleteIfExists(ctx.dataFolder().resolve(path));
    }

    /**
     * Relocates a YAML config file, keeping only the keys an admin actually changed.
     *
     * <p>Reads {@code sourceRel} (relative to the data folder) and diffs every leaf key against
     * the bundled {@code defaultResource} (a jar resource path); only keys whose value differs
     * from that default — or that don't exist in the default at all — are written to
     * {@code targetRel}. {@code sourceRel} is then renamed to {@code sourceRel + archiveSuffix}
     * so the admin's original file is preserved for reference.
     *
     * <p>No-op when {@code sourceRel} is absent or {@code targetRel} already exists (so it never
     * clobbers a target that a later run — or the admin — has already created).
     *
     * @param sourceRel      legacy file to relocate, relative to the data folder
     * @param defaultResource bundled jar resource to diff against (e.g. {@code "lang/en_us.yml"})
     * @param targetRel      new sparse-override file to write, relative to the data folder
     * @param archiveSuffix  suffix appended to {@code sourceRel} once migrated (e.g. {@code ".bak"})
     */
    static FileMigration extractChangedKeys(String sourceRel, String defaultResource, String targetRel, String archiveSuffix) {
        return ctx -> {
            Path source = ctx.dataFolder().resolve(sourceRel);
            Path target = ctx.dataFolder().resolve(targetRel);
            if (!Files.exists(source) || Files.exists(target)) {
                return false;
            }

            YamlConfiguration sourceConfig = YamlConfiguration.loadConfiguration(source.toFile());

            YamlConfiguration defaults = new YamlConfiguration();
            try (InputStream in = ctx.resource(defaultResource)) {
                if (in != null) {
                    defaults.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
            } catch (org.bukkit.configuration.InvalidConfigurationException e) {
                throw new IOException("Invalid bundled default " + defaultResource, e);
            }

            YamlConfiguration changed = new YamlConfiguration();
            for (String key : sourceConfig.getKeys(true)) {
                if (sourceConfig.isConfigurationSection(key)) {
                    continue;
                }
                Object sourceValue = sourceConfig.get(key);
                Object defaultValue = defaults.get(key);
                if (!Objects.equals(sourceValue, defaultValue)) {
                    changed.set(key, sourceValue);
                }
            }

            Files.createDirectories(target.getParent());
            changed.save(target.toFile());

            Path archive = ctx.dataFolder().resolve(sourceRel + archiveSuffix);
            Files.move(source, archive, StandardCopyOption.REPLACE_EXISTING);
            return true;
        };
    }
}
