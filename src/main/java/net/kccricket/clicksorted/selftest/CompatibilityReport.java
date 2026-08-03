package net.kccricket.clicksorted.selftest;

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

import net.kccricket.clicksorted.ClickSortedPlugin;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A version fingerprint plus every {@link CapabilityProbe} result, captured in one shot via
 * {@link #capture}. Deliberately separate from {@link SelfTestReport}: a probe reports a
 * shape/behavior the plugin depends on, not a pass/fail outcome, and folding the two together would
 * blur exactly the distinction this self-test redesign exists to draw — see {@link CapabilityProbe}.
 */
public record CompatibilityReport(String minecraftVersion, String bukkitVersion, String serverVersion,
                                   String javaVersion, boolean folia, List<ProbeOutcome> probes) {

    public record ProbeOutcome(String id, String description, CapabilityProbe.Status status, String detail) {
    }

    /** Runs every probe in {@link CapabilityProbes#ALL} and captures the current server's version facts. */
    public static CompatibilityReport capture(ClickSortedPlugin plugin) {
        List<ProbeOutcome> outcomes = new ArrayList<>();
        for (CapabilityProbe probe : CapabilityProbes.ALL) {
            CapabilityProbe.Result result = probe.probe(plugin);
            outcomes.add(new ProbeOutcome(probe.id(), probe.describe(), result.status(), result.detail()));
        }
        return new CompatibilityReport(Bukkit.getMinecraftVersion(), Bukkit.getBukkitVersion(), Bukkit.getVersion(),
                System.getProperty("java.version"), detectFolia(), List.copyOf(outcomes));
    }

    public Optional<ProbeOutcome> find(String id) {
        return probes.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    /** A multi-line plain-text fingerprint: version facts, then one line per probe. */
    public String summarize() {
        StringBuilder sb = new StringBuilder();
        sb.append("Minecraft ").append(minecraftVersion)
                .append(" / Bukkit ").append(bukkitVersion)
                .append(" / Java ").append(javaVersion)
                .append(folia ? " / Folia" : " / Paper").append('\n');
        sb.append(serverVersion).append('\n');
        for (ProbeOutcome p : probes) {
            sb.append(String.format("%-10s %-28s %s%n", p.status(), p.id(), p.detail()));
        }
        return sb.toString();
    }

    // Same one-time Class.forName probe idiom as InventorySortService.detectFolia() — duplicated
    // rather than shared because that method is a package-private implementation detail of the sort
    // package, not a public capability check.
    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
