package net.kccricket.clicksorted;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the two core i18n behaviors of {@code LangConfig}: an on-disk override wins for the keys
 * it sets while every other key still tracks the plugin's internal default (the fix for changed
 * defaults never reaching admins), and per-player locale resolution falls through
 * override → internal defaults → {@code default_locale} → built-in {@code en_us}.
 */
class LangConfigLocaleTest extends AbstractClickSortedTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private File langDir() {
        return new File(plugin.getDataFolder(), "lang");
    }

    @Test
    void onDiskOverride_winsOnlyForKeysItSets() throws IOException {
        // AbstractClickSortedTest already installed test-lang.yml (a full sentinel override) as
        // lang/en_us.yml. Replace it with a sparse override that touches only one key.
        Files.writeString(new File(langDir(), "en_us.yml").toPath(),
                "actionTooFast: \"CUSTOM OVERRIDE TEXT\"\n", StandardCharsets.UTF_8);
        plugin.getConfigManager().lang().load();

        Component overridden = plugin.getConfigManager().lang().getColoredMessage("actionTooFast");
        assertTrue(PLAIN.serialize(overridden).contains("CUSTOM OVERRIDE TEXT"),
                "A key present in the on-disk override must win");

        // A key the override never touches must still resolve to the plugin's internal default —
        // proving the sparse file no longer shadows it (the bug this feature fixes).
        Component untouched = plugin.getConfigManager().lang().getColoredMessage("noPermission");
        assertTrue(PLAIN.serialize(untouched).contains("don't have permission"),
                "A key absent from the override must fall back to the internal default, not go missing");
    }

    @Test
    void emptyOverride_neverShadowsInternalDefaults() throws IOException {
        // A fully-commented (i.e. empty) on-disk file — like the template LangConfig writes for a
        // fresh install — must contribute zero overrides.
        Files.writeString(new File(langDir(), "en_us.yml").toPath(),
                "# nothing uncommented\n", StandardCharsets.UTF_8);
        plugin.getConfigManager().lang().load();

        Component msg = plugin.getConfigManager().lang().getColoredMessage("noPermission");
        assertTrue(PLAIN.serialize(msg).contains("don't have permission"),
                "An empty override file must resolve every key from the internal default");
    }

    @Test
    void missingLocaleFile_fallsBackToDefaultLocale() throws IOException {
        // No lang/de_de.yml exists (neither override nor bundled) — a German-locale player must
        // still get a real message via the default_locale fallback, not the missing-key placeholder.
        Component msg = plugin.getConfigManager().lang().getColoredMessage(Locale.GERMANY, "noPermission");
        String plain = PLAIN.serialize(msg);
        assertFalse(plain.contains("missing lang key"), "Unknown locale must fall back, not report missing");
    }

    @Test
    void localeOverride_resolvesBeforeDefaultLocale() throws IOException {
        // A locale-specific override (here reusing en_us's country-only form as a stand-in locale
        // token to avoid needing a second bundled default) must win over the default-locale tier
        // for a player whose client reports that exact locale.
        File deDir = langDir();
        Files.writeString(new File(deDir, "de_de.yml").toPath(),
                "noPermission: \"DE OVERRIDE\"\n", StandardCharsets.UTF_8);
        plugin.getConfigManager().lang().load();

        Component forGerman = plugin.getConfigManager().lang().getColoredMessage(Locale.GERMANY, "noPermission");
        assertTrue(PLAIN.serialize(forGerman).contains("DE OVERRIDE"),
                "A locale-specific override file must be preferred for a matching player locale");

        Component forEnglish = plugin.getConfigManager().lang().getColoredMessage(Locale.US, "noPermission");
        assertFalse(PLAIN.serialize(forEnglish).contains("DE OVERRIDE"),
                "The de_de override must not leak into an en_us player's messages");
    }

    @Test
    void missingKey_logsAndReturnsPlaceholder() {
        Component msg = plugin.getConfigManager().lang().getColoredMessage("this.key.does.not.exist");
        assertTrue(PLAIN.serialize(msg).contains("missing lang key"));
    }
}
