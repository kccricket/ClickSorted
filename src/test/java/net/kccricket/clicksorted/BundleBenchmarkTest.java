package net.kccricket.clicksorted;

import net.kccricket.clicksorted.sort.BundleBenchmark;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Correctness tests for the {@link BundleBenchmark} harness itself — that it runs both paths over the
 * requested iteration count and returns a coherent timing distribution. Deliberately asserts no
 * absolute performance threshold (that would be flaky in CI); only the harness's internal invariants.
 */
class BundleBenchmarkTest extends AbstractClickSortedTest {

    @Test
    void runProducesCoherentStatsForBothPaths() {
        int iterations = 200;
        BundleBenchmark.Result result = BundleBenchmark.run(iterations);

        assertStatsSane(result.sort(), iterations);
        assertStatsSane(result.repack(), iterations);
    }

    private void assertStatsSane(BundleBenchmark.Stats s, int iterations) {
        assertEquals(iterations, s.iterations(), "iteration count should be reported as run");
        assertTrue(s.minUs() > 0, "min should be positive");
        assertTrue(s.totalNanos() > 0, "total time should be positive");
        // Distribution ordering: min ≤ median ≤ p95 ≤ max.
        assertTrue(s.minUs() <= s.medianUs(), "min ≤ median");
        assertTrue(s.medianUs() <= s.p95Us(), "median ≤ p95");
        assertTrue(s.p95Us() <= s.maxUs(), "p95 ≤ max");
    }
}
