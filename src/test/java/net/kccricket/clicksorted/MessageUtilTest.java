package net.kccricket.clicksorted;

/*
 * This file is part of ClickSorted
 *
 * ClickSorted is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * ClickSorted is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with ClickSorted. If not, see <http://www.gnu.org/licenses/>.
 */

import net.kccricket.clicksorted.text.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for {@link MessageUtil#splitLines} and {@link MessageUtil#toLore}.
 * No MockBukkit required — these cover only Adventure component logic.
 */
class MessageUtilTest {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    // --- splitLines ---

    @Test
    void splitLines_singleLine_returnsOneComponent() {
        Component c = MM.deserialize("<!italic><gray>no newline here");
        List<Component> lines = MessageUtil.splitLines(c);
        assertEquals(1, lines.size(), "Single-line input should yield one component");
        assertEquals("no newline here", PLAIN.serialize(lines.get(0)));
    }

    @Test
    void splitLines_withNewlineTag_returnsTwoComponents() {
        Component c = MM.deserialize("<!italic><gray>line one<newline>line two");
        List<Component> lines = MessageUtil.splitLines(c);
        assertEquals(2, lines.size(), "One <newline> should yield two components");
        assertEquals("line one", PLAIN.serialize(lines.get(0)));
        assertEquals("line two", PLAIN.serialize(lines.get(1)));
    }

    @Test
    void splitLines_styleInheritedOnContinuationLine() {
        // Both lines should carry gray color and non-italic decoration from the outer style.
        Component c = MM.deserialize("<!italic><gray>line one<newline>line two");
        List<Component> lines = MessageUtil.splitLines(c);

        assertEquals(2, lines.size());
        Component second = lines.get(1);

        // Flatten to plain text first to confirm content
        assertEquals("line two", PLAIN.serialize(second));

        // Walk the component tree to find the text segment and verify its style
        assertLineHasStyle(second, NamedTextColor.GRAY, TextDecoration.State.FALSE,
                "Continuation line should inherit gray color and non-italic decoration");
    }

    @Test
    void splitLines_trailingNewline_preservesBlankFinalLine() {
        Component c = MM.deserialize("line one<newline>");
        List<Component> lines = MessageUtil.splitLines(c);
        // "line one\n" splits to ["line one", ""] — blank last entry preserved
        assertEquals(2, lines.size(), "Trailing <newline> should yield a blank final component");
        assertEquals("line one", PLAIN.serialize(lines.get(0)));
        assertEquals("", PLAIN.serialize(lines.get(1)));
    }

    @Test
    void splitLines_emptyComponent_returnsOneEmptyComponent() {
        List<Component> lines = MessageUtil.splitLines(Component.empty());
        assertEquals(1, lines.size());
        assertEquals("", PLAIN.serialize(lines.get(0)));
    }

    // --- toLore ---

    @Test
    void toLore_singleComponentNoNewline_returnsOneElement() {
        Component c = MM.deserialize("<!italic>Click to remove.");
        List<Component> lore = MessageUtil.toLore(c);
        assertEquals(1, lore.size());
        assertEquals("Click to remove.", PLAIN.serialize(lore.get(0)));
    }

    @Test
    void toLore_multipleComponents_concatenatesLines() {
        // a has one newline → 2 lines; b is single → 1 line; total = 3
        Component a = MM.deserialize("<!italic><gray>line one<newline>line two");
        Component b = MM.deserialize("<!italic><gray>line three");
        List<Component> lore = MessageUtil.toLore(a, b);
        assertEquals(3, lore.size(), "toLore should flatten newlines across all input components");
        assertEquals("line one",   PLAIN.serialize(lore.get(0)));
        assertEquals("line two",   PLAIN.serialize(lore.get(1)));
        assertEquals("line three", PLAIN.serialize(lore.get(2)));
    }

    @Test
    void toLore_noArgs_returnsEmptyList() {
        List<Component> lore = MessageUtil.toLore();
        assertTrue(lore.isEmpty());
    }

    // --- helpers ---

    /**
     * Recursively walks {@code component}'s subtree to find a text segment whose plain
     * text is non-empty and asserts that it carries the expected color and italic state.
     */
    private static void assertLineHasStyle(Component component, NamedTextColor expectedColor,
                                           TextDecoration.State expectedItalic, String message) {
        boolean found = findStyleInTree(component, expectedColor, expectedItalic);
        assertTrue(found, message + " — no matching segment found in tree: "
                + PLAIN.serialize(component));
    }

    private static boolean findStyleInTree(Component component, NamedTextColor expectedColor,
                                           TextDecoration.State expectedItalic) {
        String text = PLAIN.serialize(component.children(List.of())); // direct content only
        if (!text.isEmpty()) {
            if (component.style().color() == expectedColor
                    && component.style().decoration(TextDecoration.ITALIC) == expectedItalic) {
                return true;
            }
        }
        for (Component child : component.children()) {
            if (findStyleInTree(child, expectedColor, expectedItalic)) return true;
        }
        return false;
    }
}
