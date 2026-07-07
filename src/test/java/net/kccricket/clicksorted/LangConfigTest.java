package net.kccricket.clicksorted;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class LangConfigTest extends AbstractClickSortedTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void setSortOverItemsStatus_substitutesStatus() {
        Component c = plugin.getConfigManager().lang().getColoredMessage("setSortOverItemsStatus",
                Placeholder.unparsed("status", "ENABLED"));
        assertTrue(PLAIN.serialize(c).contains("ENABLED"),
                "Expected <status> placeholder to be substituted");
    }

    @Test
    void setClickMethodTo_substitutesMethodAndInstruction() {
        Component c = plugin.getConfigManager().lang().getColoredMessage("setClickMethodTo",
                Placeholder.unparsed("method", "SWAP"),
                Placeholder.unparsed("instruction", "Press the offhand-swap key to sort."));
        String plain = PLAIN.serialize(c);
        assertTrue(plain.contains("SWAP"), "Expected <method> placeholder to be substituted");
        assertTrue(plain.contains("Press the offhand-swap key to sort."), "Expected <instruction> placeholder to be substituted");
    }

    private File langOverrideFile(String token) {
        return new File(new File(plugin.getDataFolder(), "lang"), token + ".yml");
    }

    @Test
    void overridePrecedence_overriddenKeyWinsButOthersFallThroughToInternalDefault() throws Exception {
        // test-lang.yml (installed by AbstractClickSortedTest as the lang/en_us.yml override) already
        // overrides every key with a sentinel "MSG.xxx" value, so overriding just one key here and
        // clearing the rest proves both halves of precedence in one file.
        YamlConfiguration override = new YamlConfiguration();
        override.set("prefix", "OVERRIDDEN-PREFIX");
        override.save(langOverrideFile("en_us"));
        plugin.getConfigManager().reloadAll();

        assertEquals("OVERRIDDEN-PREFIX",
                plugin.getConfigManager().lang().getMessage("prefix"),
                "The overridden key must resolve to the override value");
        assertTrue(plugin.getConfigManager().lang().getMessage("notFromConsole")
                        .contains("player"),
                "A key absent from the override must fall through to the internal default");
    }

    @Test
    void emptyOnDiskOverride_selfUpdatesToInternalDefault() throws Exception {
        // A fully-commented (or simply empty) on-disk file parses to zero keys, so nothing shadows
        // the internal default — proving a changed default reaches admins with no action required.
        new YamlConfiguration().save(langOverrideFile("en_us"));
        plugin.getConfigManager().reloadAll();

        assertTrue(plugin.getConfigManager().lang().getMessage("notFromConsole").contains("player"),
                "With an empty on-disk override, every key must resolve to the internal default");
    }

    @Test
    void localeResolution_perPlayerOverrideAndUnknownLocaleFallsBackToDefault() throws Exception {
        YamlConfiguration german = new YamlConfiguration();
        german.set("prefix", "DE-PREFIX");
        german.save(langOverrideFile("de_de"));
        plugin.getConfigManager().reloadAll();

        PlayerMock germanPlayer = addOpPlayer("Hans");
        germanPlayer.setLocale(Locale.GERMANY);
        assertEquals("DE-PREFIX",
                plugin.getConfigManager().lang(germanPlayer.locale()).getMessage("prefix"),
                "A player with a matching override locale must see that override");

        PlayerMock spanishPlayer = addOpPlayer("Pablo");
        spanishPlayer.setLocale(Locale.of("es", "ES"));
        assertNotEquals("DE-PREFIX",
                plugin.getConfigManager().lang(spanishPlayer.locale()).getMessage("prefix"),
                "A player whose locale has no matching file must fall back to default_locale, not another locale's override");
    }
}
