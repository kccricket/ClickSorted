package net.kccricket.clicksorted;

import net.kccricket.clicksorted.gui.BlacklistGuiHolder;
import net.kccricket.clicksorted.gui.BlacklistGuiHolder.Entry;
import net.kccricket.clicksorted.gui.BlacklistGuiHolder.MaterialEntry;
import net.kccricket.clicksorted.gui.BlacklistGuiHolder.NameEntry;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-logic unit tests for {@link BlacklistGuiHolder}'s static helpers.
 * No MockBukkit required — these cover only the pagination and sorting computations.
 */
class BlacklistGuiHolderTest {

    // --- totalPages ---

    @Test
    void totalPagesIsOneWhenEmpty() {
        assertEquals(1, BlacklistGuiHolder.totalPages(0));
    }

    @Test
    void totalPagesIsOneForSingleEntry() {
        assertEquals(1, BlacklistGuiHolder.totalPages(1));
    }

    @Test
    void totalPagesIsOneForExactlyAFullPage() {
        assertEquals(1, BlacklistGuiHolder.totalPages(BlacklistGuiHolder.PAGE_SIZE));
    }

    @Test
    void totalPagesIsTwoForOneMoreThanAFullPage() {
        assertEquals(2, BlacklistGuiHolder.totalPages(BlacklistGuiHolder.PAGE_SIZE + 1));
    }

    @Test
    void totalPagesIsTwoForExactlyTwoFullPages() {
        assertEquals(2, BlacklistGuiHolder.totalPages(BlacklistGuiHolder.PAGE_SIZE * 2));
    }

    @Test
    void totalPagesIsThreeForTwoFullPagesPlusOne() {
        assertEquals(3, BlacklistGuiHolder.totalPages(BlacklistGuiHolder.PAGE_SIZE * 2 + 1));
    }

    // --- entryLabel ---

    @Test
    void entryLabelReturnsMaterialNameForMaterialEntry() {
        Entry entry = new MaterialEntry(Material.DIAMOND);
        assertEquals("DIAMOND", BlacklistGuiHolder.entryLabel(entry));
    }

    @Test
    void entryLabelReturnsStoredStringForNameEntry() {
        Entry entry = new NameEntry("My Custom Item");
        assertEquals("My Custom Item", BlacklistGuiHolder.entryLabel(entry));
    }

    // --- sort order (alphabetically descending, case-insensitive) ---

    @Test
    void sortOrderIsDescendingCaseInsensitive() {
        // Mix of material and name entries; sort should be Z→A, case-insensitive.
        List<Entry> entries = java.util.Arrays.asList(
                new MaterialEntry(Material.APPLE),       // label "APPLE"
                new NameEntry("zombie sword"),           // label "zombie sword"
                new MaterialEntry(Material.DIAMOND),     // label "DIAMOND"
                new NameEntry("Blaze Rod")               // label "Blaze Rod"
        );
        entries.sort(Comparator.comparing(BlacklistGuiHolder::entryLabel, String.CASE_INSENSITIVE_ORDER).reversed());

        // Expected Z→A order: "zombie sword", "DIAMOND", "Blaze Rod", "APPLE"
        assertEquals("zombie sword", BlacklistGuiHolder.entryLabel(entries.get(0)));
        assertEquals("DIAMOND",      BlacklistGuiHolder.entryLabel(entries.get(1)));
        assertEquals("Blaze Rod",    BlacklistGuiHolder.entryLabel(entries.get(2)));
        assertEquals("APPLE",        BlacklistGuiHolder.entryLabel(entries.get(3)));
    }
}
