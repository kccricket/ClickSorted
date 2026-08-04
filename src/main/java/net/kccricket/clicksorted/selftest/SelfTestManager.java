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
import net.kccricket.kcmclib.logging.Log;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates the self-test: one-shot {@link #runAlgo()}/{@link #runSim(Player)} reports, one-shot
 * {@link #runProbes()} version fingerprinting, and the multi-tick interactive LIVE phase (a
 * {@link LiveCycle} catalog — {@link LiveCycles#quick()} or {@link LiveCycles#full()} — driven by real
 * player clicks and evaluated by {@link SelfTestListener}). Constructed once in {@code onEnable}
 * (never per-reload, mirroring {@code ActionThrottle}/{@code PreferencesDialogService}) so a reload
 * can't orphan a running session; {@link #abortAll} is called from reload and disable.
 */
public final class SelfTestManager {

    private static final long WATCHDOG_TICKS = 20L * 60 * 5; // 5 minutes

    private final ClickSortedPlugin plugin;
    private final Map<UUID, SelfTestSession> sessions = new ConcurrentHashMap<>();

    public SelfTestManager(ClickSortedPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // One-shot phases
    // -------------------------------------------------------------------------

    /** ALGO phase: pure algorithm calls, console-runnable. Excluded from {@link SelfTestReport}'s compatibility verdict — see {@code SelfTestCase.Phase} javadoc. */
    public SelfTestReport runAlgo() {
        return SelfTestRunner.runAuto();
    }

    /** Version fingerprint + every {@link CapabilityProbe} result. Console-runnable, needs no player. */
    public CompatibilityReport runProbes() {
        return CompatibilityReport.capture(plugin);
    }

    /** Snapshots the player's inventory/prefs, runs every SIM case, then always restores. */
    public SelfTestReport runSim(Player player) {
        SelfTestSession session;
        try {
            session = new SelfTestSession(plugin, player);
        } catch (RuntimeException | LinkageError e) {
            SelfTestReport report = new SelfTestReport();
            report.fail("sim-session-init", "threw " + e.getClass().getSimpleName() + ": " + e.getMessage(),
                    SelfTestReport.Category.INTEGRATION);
            return report;
        }
        try {
            session.applyTestSettings();
            return SelfTestRunner.runSim(plugin, player);
        } finally {
            session.restoreAndClear();
        }
    }

    // -------------------------------------------------------------------------
    // Interactive LIVE phase
    // -------------------------------------------------------------------------

    public enum StartResult {STARTED, ALREADY_RUNNING, FAILED}

    SelfTestSession sessionFor(Player player) {
        return sessions.get(player.getUniqueId());
    }

    /**
     * Starts a LIVE session for {@code player}: {@code quick} runs only the 6-cycle click-method
     * sweep, otherwise the full 11-cycle catalog ({@link LiveCycles}). Captures a
     * {@link CompatibilityReport} <em>before</em> constructing the session — so even if session
     * construction itself throws (the exact failure mode of the pre-fix serialization bug), the
     * tester still gets a full version fingerprint instead of a bare crash.
     */
    public StartResult startLive(Player player, boolean quick) {
        if (sessions.containsKey(player.getUniqueId())) {
            return StartResult.ALREADY_RUNNING;
        }
        CompatibilityReport probes = CompatibilityReport.capture(plugin);
        SelfTestSession session;
        try {
            session = new SelfTestSession(plugin, player);
        } catch (RuntimeException | LinkageError e) {
            Log.severe("Self-test could not start for " + player.getName() + ": " + e.getClass().getSimpleName()
                    + ": " + e.getMessage() + " — compatibility fingerprint follows.", e);
            plugin.messages().to(player).error().send("selfTestStartFailed",
                    Placeholder.unparsed("reason", e.getClass().getSimpleName() + ": " + e.getMessage()));
            plugin.messages().to(player).raw().send(Component.text(probes.summarize()));
            return StartResult.FAILED;
        }
        session.phase = SelfTestSession.Phase.LIVE;
        session.probes = probes;
        session.cycles = quick ? LiveCycles.quick() : LiveCycles.full();
        sessions.put(player.getUniqueId(), session);
        // Sent before staging the first cycle so the intro banner always precedes its instruction
        // in chat, not after it (stageCurrentCycle's prompt would otherwise land first).
        plugin.messages().to(player).status().send("selfTestStarted");
        session.applyTestSettings();
        startActionBar(session);
        stageCurrentCycle(session);
        return StartResult.STARTED;
    }

    /** Ends {@code player}'s LIVE session (if any), restoring everything and reporting a summary. */
    public void stopLive(Player player) {
        SelfTestSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            plugin.messages().to(player).status().send("selfTestNoneRunning");
            return;
        }
        session.restoreAndClear();
        plugin.messages().to(player).status().send("selfTestStopped");
        reportSummary(player, session);
    }

    public void skipCurrent(Player player) {
        SelfTestSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            plugin.messages().to(player).status().send("selfTestNoneRunning");
            return;
        }
        LiveCycle current = session.currentLiveCycle();
        session.report.skip(current == null ? "live-unknown" : current.id(), "skipped by tester",
                SelfTestReport.Category.ENDTOEND);
        advance(session);
    }

    public void status(Player player) {
        SelfTestSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            plugin.messages().to(player).status().send("selfTestNoneRunning");
            return;
        }
        LiveCycle current = session.currentLiveCycle();
        if (current == null) {
            // Between the last cycle finishing and the session being torn down — practically
            // unreachable (finishLive removes the session first), but guard rather than NPE.
            plugin.messages().to(player).status().send("selfTestNoneRunning");
            return;
        }
        plugin.messages().to(player).status().send("selfTestStatus", cycleResolvers(session, current));
    }

    /** Silently aborts and restores {@code player}'s LIVE session, if any — no chat messages (player is quitting). */
    void abortSilently(Player player) {
        SelfTestSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            session.restoreAndClear();
        }
    }

    /**
     * Called by {@link SelfTestListener} when a live cycle's expected click is observed (or throws —
     * see its catch). The chat line carries only the cycle's id — a stable, greppable code (matches
     * the id used in {@link LiveCycles}/{@link SelfTestCases}) — never the failure detail; that's
     * withheld for the end-of-run summary so a long run's chat doesn't fill with reasons the tester
     * can't act on mid-test.
     */
    void recordLiveResult(SelfTestSession session, boolean pass, String detail) {
        LiveCycle cycle = session.currentLiveCycle();
        String id = cycle == null ? "live-unknown" : cycle.id();
        if (pass) {
            session.report.pass(id, SelfTestReport.Category.ENDTOEND);
            plugin.messages().to(session.player()).status().send("selfTestCyclePass",
                    Placeholder.unparsed("code", id));
        } else {
            session.report.fail(id, detail, SelfTestReport.Category.ENDTOEND);
            plugin.messages().to(session.player()).error().send("selfTestCycleFail",
                    Placeholder.unparsed("code", id));
        }
        advance(session);
    }

    private void advance(SelfTestSession session) {
        session.liveIndex++;
        stageCurrentCycle(session);
    }

    /**
     * Stages the next non-skipped cycle, or finishes the run once the catalog is exhausted. Loops
     * past any cycle whose {@link LiveCycle#skipReason} is non-empty, recording each as a skip.
     */
    private void stageCurrentCycle(SelfTestSession session) {
        Player player = session.player();
        LiveCycle cycle;
        while ((cycle = session.currentLiveCycle()) != null) {
            Optional<String> skip = cycle.skipReason(session.probes);
            if (skip.isEmpty()) {
                break;
            }
            session.report.skip(cycle.id(), skip.get(), SelfTestReport.Category.ENDTOEND);
            session.liveIndex++;
        }
        if (cycle == null) {
            finishLive(session);
            return;
        }

        session.applyTestSettings(); // reset to baseline before every cycle so scenario-specific overrides don't leak forward
        Inventory chest = cycle.stage(plugin, player, session);
        session.testChest = chest;
        InventoryView view = player.openInventory(chest);
        if (view == null) {
            // Could not open (e.g. player disconnected mid-run); the quit handler will clean up.
            return;
        }

        plugin.messages().to(player).status().send("selfTestCyclePrompt", cycleResolvers(session, cycle));
        sendActionBarNow(session);

        armWatchdog(session, player);
    }

    /**
     * The placeholders shared by {@code selfTestCyclePrompt}, {@code selfTestActionBarInstruction},
     * and {@code selfTestStatus} — chat and the action bar must show the exact same instruction text
     * for a given cycle, so they're built from one place rather than re-derived per call site.
     */
    private TagResolver[] cycleResolvers(SelfTestSession session, LiveCycle cycle) {
        Player player = session.player();
        return new TagResolver[]{
                Placeholder.unparsed("cycle", String.valueOf(session.liveIndex + 1)),
                Placeholder.unparsed("total", String.valueOf(session.cycles.size())),
                Placeholder.unparsed("code", cycle.id()),
                Placeholder.unparsed("instruction", cycle.instruction(plugin, player.locale()))
        };
    }

    /**
     * Starts a single repeating task, for the life of the LIVE session, that keeps the current
     * cycle's instruction visible in the action bar (chat scrolls and gets buried; the action bar
     * doesn't, but vanilla clears it a few seconds after the last send, hence the repeat). Re-reads
     * {@link SelfTestSession#currentLiveCycle()} every tick so it tracks cycle advances without
     * needing to be restarted per cycle.
     */
    private void startActionBar(SelfTestSession session) {
        Player player = session.player();
        session.actionBarTask = player.getScheduler().runAtFixedRate(plugin, task -> sendActionBarNow(session), null, 40L, 40L);
    }

    private void sendActionBarNow(SelfTestSession session) {
        LiveCycle cycle = session.currentLiveCycle();
        if (cycle == null) {
            return;
        }
        Player player = session.player();
        Component msg = plugin.getConfigManager().lang(player.locale())
                .render("selfTestActionBarInstruction", cycleResolvers(session, cycle));
        player.sendActionBar(msg);
    }

    private void armWatchdog(SelfTestSession session, Player player) {
        int expectedIndex = session.liveIndex;
        player.getScheduler().runDelayed(plugin, task -> {
            SelfTestSession current = sessions.get(player.getUniqueId());
            if (current == session && session.phase == SelfTestSession.Phase.LIVE && session.liveIndex == expectedIndex) {
                plugin.messages().to(player).error().send("selfTestTimedOut");
                stopLive(player);
            }
        }, null, WATCHDOG_TICKS);
    }

    private void finishLive(SelfTestSession session) {
        Player player = session.player();
        sessions.remove(player.getUniqueId());
        session.restoreAndClear();
        plugin.messages().to(player).status().send("selfTestFinished",
                Placeholder.unparsed("passed", String.valueOf(session.report.passCount())),
                Placeholder.unparsed("total", String.valueOf(session.report.entries().size())));
        reportSummary(player, session);
    }

    /** The one place failure/skip detail is shown — grouped by category, each line keyed by its id/code, via {@link SelfTestReport#summarize()}. */
    private void reportSummary(Player player, SelfTestSession session) {
        plugin.messages().to(player).raw().send(Component.text(session.report.summarize()));
    }

    /**
     * Aborts every running LIVE session, restoring each player's inventory/preferences. Called from
     * {@code /clicksorted admin reload} and {@code onDisable} so a session can never survive either.
     */
    public void abortAll() {
        for (SelfTestSession session : List.copyOf(sessions.values())) {
            sessions.remove(session.player().getUniqueId());
            try {
                session.restoreAndClear();
            } catch (RuntimeException | LinkageError e) {
                Log.warning("Failed to restore self-test session for " + session.player().getName()
                        + " during abortAll — restore their inventory/preferences manually.", e);
            }
        }
    }
}
