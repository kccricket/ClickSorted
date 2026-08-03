package net.kccricket.clicksorted.selftest;

import net.kccricket.clicksorted.ClickSortedPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural coverage only — MockBukkit carries no real version-compatibility signal, so unlike
 * every other self-test suite this deliberately asserts nothing about a probe's {@link CapabilityProbe.Status}.
 * The actual PRESENT/ABSENT/UNEXPECTED matrix per Minecraft version is validated manually on real
 * Paper builds (see CLAUDE.md's self-test verification matrix).
 */
class CapabilityProbeTest {

    private ClickSortedPlugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        InputStream configStream = getClass().getClassLoader().getResourceAsStream("test-config.yml");
        plugin = MockBukkit.loadWithConfig(ClickSortedPlugin.class, configStream);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void ids_areUnique() {
        Set<String> ids = new HashSet<>();
        for (CapabilityProbe probe : CapabilityProbes.ALL) {
            assertTrue(ids.add(probe.id()), "duplicate probe id: " + probe.id());
        }
    }

    @Test
    void describe_isNonEmptyForEveryProbe() {
        for (CapabilityProbe probe : CapabilityProbes.ALL) {
            assertNotNull(probe.describe());
            assertFalse(probe.describe().isBlank(), "blank describe() for probe " + probe.id());
        }
    }

    @Test
    void probe_neverThrows() {
        for (CapabilityProbe probe : CapabilityProbes.ALL) {
            assertDoesNotThrow(() -> probe.probe(plugin), "probe " + probe.id() + " threw");
        }
    }

    @Test
    void capture_capturesEveryProbeAndAVersionFingerprint() {
        CompatibilityReport report = CompatibilityReport.capture(plugin);
        assertEquals(CapabilityProbes.ALL.size(), report.probes().size());
        assertNotNull(report.minecraftVersion());
        assertNotNull(report.summarize());
    }
}
