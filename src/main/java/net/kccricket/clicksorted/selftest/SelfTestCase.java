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

import java.util.Optional;

/**
 * One self-test case: a small, self-contained unit of work plus its own assertion. Case bodies are
 * plain Java (see {@link SelfTestCases}) rather than a declarative/YAML schema — there is no golden
 * data external to the case, and every AUTO-phase golden is authored independently of the
 * production sort algorithms (never computed by calling them), so a failure here is never "the
 * engine disagreeing with itself".
 */
public interface SelfTestCase {

    /**
     * Which units of work a case needs. {@link #AUTO} cases must not touch {@link SelfTestContext#player()}.
     *
     * <p>Exposed to the admin as {@code /clicksorted admin selftest algo} (not {@code auto}) — pure Java
     * has no Paper-API dependency in its logic, so passing {@link #AUTO} is close to meaningless as
     * version-compatibility evidence and it is excluded from {@link SelfTestReport}'s compatibility
     * verdict ({@link SelfTestReport.Category#SANITY}). It still constructs real {@code ItemStack}s and
     * {@code BundleMeta} through {@code getItemMeta()}/{@code SortKey} — real item-identity surface,
     * exactly where the Minecraft 1.20.5 data-component rewrite caused the biggest behavioral break in
     * the plugin's supported range — so combined with catching {@link LinkageError} it remains a
     * legitimate, if weak, linkage-and-item-identity smoke test at zero tester cost. {@link #SIM} carries
     * more signal ({@link SelfTestReport.Category#INTEGRATION}) but only proves the plugin reacts
     * correctly to a well-formed event, not that this server still constructs one the way we assume —
     * only a real client gesture ({@link LiveCycle}, {@link SelfTestReport.Category#ENDTOEND}) can prove
     * that.
     */
    enum Phase {
        /** Pure algorithm units ({@code SortEngine}, {@code InPlacePacker}, {@code BundlePacker}, …) — no Bukkit server interaction beyond {@link org.bukkit.inventory.ItemStack} construction. Runnable from console. */
        AUTO,
        /** A real {@link org.bukkit.inventory.Inventory} and a synthetic {@link org.bukkit.event.inventory.InventoryClickEvent} dispatched through the live plugin's listeners. Requires an online {@link org.bukkit.entity.Player}. */
        SIM
    }

    String id();

    Phase phase();

    /**
     * Runs the case.
     *
     * @return {@link Optional#empty()} on pass, or a human-readable failure description
     * @throws SelfTestSkip if a required capability (e.g. a dyed bundle material) is unavailable on
     *                       this server version — reported as a skip, not a failure
     */
    Optional<String> run(SelfTestContext ctx);
}
