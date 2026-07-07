package net.kccricket.clicksorted.migration;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * A composable, store-neutral file migration rule — the file-dimension counterpart to
 * {@link Migration} (which migrates values within a {@link Store}). {@link #apply} returns
 * {@code true} if it changed disk.
 *
 * <p>Static factory methods mirror {@link Migration}'s style:
 * <ul>
 *   <li>{@link #renameFile} — move a data-folder file when present and the target absent.</li>
 *   <li>{@link #deleteFile} — drop a deprecated data-folder file.</li>
 *   <li>{@link #extractChangedKeys} — the legacy {@code lang.yml} → sparse override migration:
 *       diff a flat YAML file against a bundled default, write only the differing keys to a new
 *       location, then archive the source.</li>
 * </ul>
 */
@FunctionalInterface
public interface FileMigration {

    boolean apply(FileMigrationContext ctx) throws IOException;

    /** Moves {@code from} to {@code to} within the data folder. No-op when {@code from} is absent or {@code to} already exists. */
    static FileMigration renameFile(String from, String to) {
        return ctx -> {
            Path fromPath = ctx.dataFolder().resolve(from);
            Path toPath = ctx.dataFolder().resolve(to);
            if (!Files.exists(fromPath) || Files.exists(toPath)) {
                return false;
            }
            Files.createDirectories(toPath.getParent());
            Files.move(fromPath, toPath);
            return true;
        };
    }

    /** Deletes {@code path} within the data folder. No-op when absent. */
    static FileMigration deleteFile(String path) {
        return ctx -> {
            Path target = ctx.dataFolder().resolve(path);
            if (!Files.exists(target)) {
                return false;
            }
            Files.delete(target);
            return true;
        };
    }

    /**
     * Loads the flat YAML file at {@code sourceRel}, diffs each top-level key against the bundled
     * {@code defaultResource} YAML, and writes only the keys whose value differs from the default
     * to {@code targetRel}. The source is then archived by appending {@code archiveSuffix} to its
     * name. No-op (returns {@code false}, touches nothing) when {@code sourceRel} is absent or
     * {@code targetRel} already exists — so re-running after a successful migration is a no-op.
     */
    static FileMigration extractChangedKeys(String sourceRel, String defaultResource, String targetRel, String archiveSuffix) {
        return ctx -> {
            Path source = ctx.dataFolder().resolve(sourceRel);
            Path target = ctx.dataFolder().resolve(targetRel);
            if (!Files.exists(source) || Files.exists(target)) {
                return false;
            }

            YamlConfiguration userConfig = YamlConfiguration.loadConfiguration(source.toFile());

            YamlConfiguration defaultConfig = new YamlConfiguration();
            try (var stream = ctx.resource(defaultResource)) {
                if (stream != null) {
                    defaultConfig.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
                }
            } catch (org.bukkit.configuration.InvalidConfigurationException e) {
                throw new IOException("Invalid bundled default resource: " + defaultResource, e);
            }

            YamlConfiguration diff = new YamlConfiguration();
            for (String key : userConfig.getKeys(false)) {
                Object userValue = userConfig.get(key);
                Object defaultValue = defaultConfig.get(key);
                if (!java.util.Objects.equals(userValue, defaultValue)) {
                    diff.set(key, userValue);
                }
            }

            // Only materialize the sparse override when it actually overrides something. If the
            // legacy file matched every default (empty diff), writing an empty target would exist
            // on disk and suppress the fully-commented documentation template LangConfig writes
            // when the target is absent — leaving the admin with an empty, undocumented file. Skip
            // the write in that case so the template is regenerated, matching a fresh install.
            if (!diff.getKeys(false).isEmpty()) {
                Files.createDirectories(target.getParent());
                diff.save(target.toFile());
            }

            Path archived = source.resolveSibling(source.getFileName() + archiveSuffix);
            Files.move(source, archived, StandardCopyOption.REPLACE_EXISTING);
            return true;
        };
    }
}
