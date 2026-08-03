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

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates pass/fail/skip outcomes for one self-test run (a phase, or an entire invocation) and
 * renders a plain-text summary suitable for both chat and console.
 */
public final class SelfTestReport {

    /**
     * How much version-compatibility signal a category's failures carry — see {@link SelfTestCase.Phase}
     * for why {@link #SANITY} failures are demoted out of the compatibility verdict entirely.
     */
    public enum Category {
        /** {@code SelfTestCase.Phase.AUTO} — pure algorithm calls; no Paper-API surface, so a failure here is never a version signal. */
        SANITY,
        /** {@code SelfTestCase.Phase.SIM} — a synthetic event dispatched through real listeners; proves the plugin reacts correctly, not that this server still builds the event the way we assume. */
        INTEGRATION,
        /** A {@link LiveCycle} — a real client gesture. The only category that can catch an actual version-behavior difference. */
        ENDTOEND
    }

    /** One case's outcome. */
    public record Entry(String id, Outcome outcome, String detail, Category category) {
    }

    public enum Outcome {
        PASS, FAIL, SKIP
    }

    private final List<Entry> entries = new ArrayList<>();

    public void pass(String id, Category category) {
        entries.add(new Entry(id, Outcome.PASS, null, category));
    }

    public void fail(String id, String detail, Category category) {
        entries.add(new Entry(id, Outcome.FAIL, detail, category));
    }

    public void skip(String id, String reason, Category category) {
        entries.add(new Entry(id, Outcome.SKIP, reason, category));
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public int passCount() {
        return (int) entries.stream().filter(e -> e.outcome() == Outcome.PASS).count();
    }

    public int failCount() {
        return (int) entries.stream().filter(e -> e.outcome() == Outcome.FAIL).count();
    }

    public int skipCount() {
        return (int) entries.stream().filter(e -> e.outcome() == Outcome.SKIP).count();
    }

    public boolean allPassed() {
        return failCount() == 0;
    }

    /** Failures outside {@link Category#SANITY} — the actual compatibility verdict (see class javadoc). */
    public int compatibilityFailCount() {
        return (int) entries.stream().filter(e -> e.outcome() == Outcome.FAIL && e.category() != Category.SANITY).count();
    }

    /** Merges another report's entries into this one (used to combine phase reports). */
    public void merge(SelfTestReport other) {
        entries.addAll(other.entries);
    }

    /** A multi-line plain-text summary grouped by {@link Category}, then a totals + compatibility-verdict line. */
    public String summarize() {
        StringBuilder sb = new StringBuilder();
        for (Category category : Category.values()) {
            List<Entry> inCategory = entries.stream().filter(e -> e.category() == category).toList();
            if (inCategory.isEmpty()) {
                continue;
            }
            sb.append("== ").append(category).append(" ==\n");
            for (Entry e : inCategory) {
                if (e.outcome() == Outcome.FAIL) {
                    sb.append("FAIL ").append(e.id()).append(": ").append(e.detail()).append('\n');
                } else if (e.outcome() == Outcome.SKIP) {
                    sb.append("SKIP ").append(e.id()).append(": ").append(e.detail()).append('\n');
                }
            }
        }
        sb.append(String.format("%d passed, %d failed, %d skipped (of %d) — compatibility verdict: %d failure(s) outside SANITY",
                passCount(), failCount(), skipCount(), entries.size(), compatibilityFailCount()));
        return sb.toString();
    }
}
