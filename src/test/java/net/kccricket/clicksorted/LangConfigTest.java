package net.kccricket.clicksorted;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

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
}
