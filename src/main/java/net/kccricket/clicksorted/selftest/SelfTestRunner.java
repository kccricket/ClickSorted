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
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Executes {@link SelfTestCases#AUTO} and {@link SelfTestCases#SIM} against a {@link SelfTestReport}.
 * {@link #runAuto()} needs nothing but the JVM (console-runnable); {@link #runSim} needs an online
 * player to open real inventories against.
 */
public final class SelfTestRunner {

    private SelfTestRunner() {
    }

    public static SelfTestReport runAuto() {
        SelfTestReport report = new SelfTestReport();
        SelfTestContext ctx = new SelfTestContext(null, null);
        for (SelfTestCase c : SelfTestCases.AUTO) {
            runOne(report, c, ctx);
        }
        return report;
    }

    public static SelfTestReport runSim(ClickSortedPlugin plugin, Player player) {
        SelfTestReport report = new SelfTestReport();
        SelfTestContext ctx = new SelfTestContext(plugin, player);
        for (SelfTestCase c : SelfTestCases.SIM) {
            runOne(report, c, ctx);
        }
        return report;
    }

    private static void runOne(SelfTestReport report, SelfTestCase c, SelfTestContext ctx) {
        SelfTestReport.Category category = categoryOf(c.phase());
        try {
            Optional<String> failure = c.run(ctx);
            if (failure.isPresent()) {
                report.fail(c.id(), failure.get(), category);
            } else {
                report.pass(c.id(), category);
            }
        } catch (SelfTestSkip skip) {
            report.skip(c.id(), skip.getMessage(), category);
        } catch (RuntimeException | LinkageError e) {
            // LinkageError (NoSuchMethodError, NoClassDefFoundError, IncompatibleClassChangeError, …) is
            // exactly the version-incompatibility hazard this self-test exists to surface — see
            // CLAUDE.md's InventoryView note. Reporting it as a failed case, not letting it crash the
            // whole run, is what makes the self-test diagnose a version break instead of being one.
            report.fail(c.id(), "threw " + e.getClass().getSimpleName() + ": " + e.getMessage(), category);
        }
    }

    private static SelfTestReport.Category categoryOf(SelfTestCase.Phase phase) {
        return switch (phase) {
            case AUTO -> SelfTestReport.Category.SANITY;
            case SIM -> SelfTestReport.Category.INTEGRATION;
        };
    }
}
