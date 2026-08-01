package net.kccricket.clicksorted.commands;

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

import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.kccricket.kcmclib.commands.Suggest;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the command-suggestion helpers in {@link ClickSortedCommands}, exercised through the
 * same {@link Suggest} calls the {@code bundle blacklist add material} argument actually uses
 * (production stopped calling a local {@code suggestEnum} directly some time ago — this test now
 * follows that same path instead of a dead one).
 *
 * <p>Requires a MockBukkit server because {@link Material#isItem()} (used by
 * {@link ClickSortedCommands#SUGGESTABLE_MATERIAL}) routes through the server's unsafe-values
 * registry. The plugin itself is not needed, so this uses a bare {@code MockBukkit.mock()}.
 */
class ClickSortedCommandsTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static List<String> suggestMaterials(String input) throws Exception {
        SuggestionsBuilder builder = new SuggestionsBuilder(input, 0);
        Suggestions suggestions = Suggest.prefixed(builder,
                Suggest.materialNames(ClickSortedCommands.SUGGESTABLE_MATERIAL)).get();
        return suggestions.getList().stream().map(Suggestion::getText).toList();
    }

    @Test
    void suggestionsAreNamespacedAndIncludeBothBlockItemAndPureItem() throws Exception {
        List<String> suggestions = suggestMaterials("");

        // DIRT is a placeable block but still has an item form, so it must be suggested.
        assertTrue(suggestions.contains("minecraft:dirt"),
                "Block-type material DIRT (which has an item form) should be suggested, namespaced");
        // WOODEN_SWORD is a pure item.
        assertTrue(suggestions.contains("minecraft:wooden_sword"),
                "Item-type material WOODEN_SWORD should be suggested, namespaced");
        assertFalse(suggestions.contains("dirt"), "bare (non-namespaced) names must not be suggested");
    }

    @Test
    void suggestionsExcludeNonItemMaterials() throws Exception {
        // WATER is block-only: it has no item form and would NPE the blacklist GUI, so the
        // predicate must keep it out of completions.
        assertFalse(suggestMaterials("").contains("minecraft:water"),
                "Non-item material WATER should not be suggested");
    }
}
