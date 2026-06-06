package net.kccricket.clicksorted;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LangConfigTest extends AbstractClickSortedTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void tipToReEnable_containsCommandInWhite() {
        Component c = plugin.getConfigManager().lang().getColoredMessage("tipToReEnable");
        // The <white> tag should produce a child with NamedTextColor.WHITE
        boolean foundWhite = c.children().stream()
                .anyMatch(child -> NamedTextColor.WHITE.equals(child.color()));
        assertTrue(foundWhite, "Expected a white-colored child component in tipToReEnable");
        // Plain text should contain the command name
        assertTrue(PLAIN.serialize(c).contains("/clicksorted shiftclick"));
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
}
