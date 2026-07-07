package net.kccricket.clicksorted;

import net.kccricket.clicksorted.text.lang.LocaleMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for {@link LocaleMessages}: constructed directly from in-memory maps, with no
 * MockBukkit bootstrap, proving the resolution core is decoupled from any plugin/file lifecycle.
 */
class LocaleMessagesTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static YamlConfiguration cfg(String key, String value) {
        YamlConfiguration c = new YamlConfiguration();
        c.set(key, value);
        return c;
    }

    @Test
    void localeToken_combinesLanguageAndCountryLowercased() {
        assertEquals("en_us", LocaleMessages.localeToken(Locale.of("en", "US")));
        assertEquals("de_de", LocaleMessages.localeToken(Locale.of("de", "DE")));
        assertEquals("fr", LocaleMessages.localeToken(Locale.of("fr")));
    }

    @Test
    void resolvesFromInternalDefaultLocaleWhenNothingElseMatches() {
        LocaleMessages messages = new LocaleMessages(
                Map.of("en_us", cfg("greeting", "hello")),
                Map.of(),
                Locale.of("en", "US"));

        assertEquals("hello", messages.getMessage(Locale.of("ja", "JP"), "greeting"),
                "An unrelated locale should fall back to the default-locale internal value");
    }

    @Test
    void overrideTakesPrecedenceOverInternal() {
        LocaleMessages messages = new LocaleMessages(
                Map.of("en_us", cfg("greeting", "internal")),
                Map.of("en_us", cfg("greeting", "override")),
                Locale.of("en", "US"));

        assertEquals("override", messages.getMessage(Locale.of("en", "US"), "greeting"));
    }

    @Test
    void emptyOverrideFallsThroughToInternalDefault_provingSelfUpdatingDefaults() {
        // Simulates a fully-commented on-disk file: it parses to an empty config, contributing no keys.
        LocaleMessages messages = new LocaleMessages(
                Map.of("en_us", cfg("greeting", "internal-v2")),
                Map.of("en_us", new YamlConfiguration()),
                Locale.of("en", "US"));

        assertEquals("internal-v2", messages.getMessage(Locale.of("en", "US"), "greeting"),
                "A blank override file must not shadow a changed internal default");
    }

    @Test
    void languageOnlyOverrideAppliesBeforeDefaultLocaleTier() {
        LocaleMessages messages = new LocaleMessages(
                Map.of("en_us", cfg("greeting", "internal")),
                Map.of("de", cfg("greeting", "de-lang-override")),
                Locale.of("en", "US"));

        assertEquals("de-lang-override", messages.getMessage(Locale.of("de", "DE"), "greeting"),
                "de_DE has no de_de-specific override, but should use the de language-only override");
    }

    @Test
    void unknownLocaleFallsBackToDefaultLocaleThenInternalEnUs() {
        LocaleMessages messages = new LocaleMessages(
                Map.of("en_us", cfg("greeting", "hello")),
                Map.of(),
                Locale.of("fr", "FR"));

        // fr_FR has no internal file at all, so resolution falls further to internal en_us.
        assertEquals("hello", messages.getMessage(Locale.of("ja", "JP"), "greeting"));
    }

    @Test
    void localeResolution_perPlayerGermanVsEnglish() {
        LocaleMessages messages = new LocaleMessages(
                Map.of("en_us", cfg("greeting", "hello"), "de_de", cfg("greeting", "hallo")),
                Map.of(),
                Locale.of("en", "US"));

        assertEquals("hallo", messages.getMessage(Locale.GERMANY, "greeting"));
        assertEquals("hello", messages.getMessage(Locale.of("en", "US"), "greeting"));
    }

    @Test
    void missingKey_logsAndReturnsVisiblePlaceholder() {
        LocaleMessages messages = new LocaleMessages(Map.of("en_us", new YamlConfiguration()), Map.of(),
                Locale.of("en", "US"));

        Component c = messages.getColoredMessage(Locale.of("en", "US"), "totally.missing.key");
        String plain = PLAIN.serialize(c);
        assertTrue(plain.contains("missing lang key"), "Expected a visible missing-key placeholder");
        assertTrue(plain.contains("totally.missing.key"), "Expected the missing key path to be named");
    }
}
