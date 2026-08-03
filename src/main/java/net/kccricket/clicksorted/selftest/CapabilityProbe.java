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

/**
 * A single fact or round-trip the plugin depends on, checked directly against the running server
 * instead of inferred from a version number. This is the mechanism this self-test redesign leans on
 * for "will this still work on a version we haven't tested, including a future one": a
 * {@link SelfTestCase} asserts an outcome someone predicted, while a probe asserts a shape/behavior
 * the plugin actually relies on and catches drift in it regardless of whether anyone anticipated
 * <em>how</em> it would break — as long as the probe touches the real symbol or round-trip.
 *
 * <p>Prefer a round-trip over a bare presence check where practical (see {@link CapabilityProbes}):
 * a method signature can stay stable while its behavior changes underneath it, and only exercising it
 * end-to-end catches that.
 *
 * <p>A probe never fails a self-test run. {@link Status#UNEXPECTED} is the loud signal — a dependency
 * drifted from what was true when the probe was written; {@link Status#ABSENT} is informational and
 * drives downstream skips (e.g. a bundle-dependent {@link SelfTestCase} or {@link LiveCycle}).
 *
 * <p><b>Limits</b>: probes see nothing about real click-delivery semantics (only {@link LiveCycle} does),
 * nothing about drift in a surface nobody thought to probe, and a presence probe goes stale silently
 * if production stops depending on that symbol.
 */
public interface CapabilityProbe {

    enum Status {PRESENT, ABSENT, UNEXPECTED}

    record Result(Status status, String detail) {
        public static Result present(String detail) {
            return new Result(Status.PRESENT, detail);
        }

        public static Result absent(String detail) {
            return new Result(Status.ABSENT, detail);
        }

        public static Result unexpected(String detail) {
            return new Result(Status.UNEXPECTED, detail);
        }
    }

    String id();

    String describe();

    /**
     * Runs the probe. Instances built via {@link CapabilityProbes#of} already wrap this body in its
     * own {@code catch (RuntimeException | LinkageError)} — reported as {@link Status#UNEXPECTED} —
     * so one broken probe can never take the rest of the catalog down with it.
     */
    Result probe(ClickSortedPlugin plugin);
}
