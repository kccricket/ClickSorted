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
import net.kccricket.clicksorted.model.ClickMethod;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.Locale;
import java.util.Optional;

/**
 * One LIVE-phase scenario: a real client gesture the tester performs, staged by the plugin and
 * evaluated once the production listeners have processed it. Unlike a {@link SelfTestCase}, a
 * LiveCycle is driven by an actual {@link InventoryClickEvent} the server itself built from a real
 * client click — the one thing {@code SelfTestCase.Phase#SIM} cannot prove by construction (its
 * synthetic event's raw-slot constants are the plugin's own assumptions, restated).
 */
public interface LiveCycle {

    String id();

    /** The click gesture this cycle stages (drives the pref set before staging and the tester-facing instruction). */
    ClickMethod clickMethod();

    /**
     * The tester-facing action-bar prompt: which inventory area to click in (the test chest, the
     * player's main storage, or their hotbar) and, via the underlying {@link ClickMethod} instruction,
     * whether the target slot must be empty or occupied. Plain text (no MiniMessage tags) — it's
     * substituted into {@code selfTestActionBarInstruction}'s own styled template.
     */
    String instruction(ClickSortedPlugin plugin, Locale locale);

    /** Non-empty to skip this cycle before staging — e.g. a required capability came back non-PRESENT. */
    default Optional<String> skipReason(CompatibilityReport probes) {
        return Optional.empty();
    }

    /** Stages fixtures (inventory contents, prefs, locks, blacklist) and returns the inventory to open for the tester. */
    Inventory stage(ClickSortedPlugin plugin, Player player, SelfTestSession session);

    /** Whether {@code event} is the click this cycle is waiting for. */
    boolean matches(InventoryClickEvent event, Player player, SelfTestSession session);

    /**
     * Evaluates the outcome once {@link #matches} fired and the production listeners have already run
     * (this is invoked from a {@code MONITOR}-priority handler, strictly after them).
     *
     * @return {@link Optional#empty()} on pass, or a human-readable failure description
     */
    Optional<String> evaluate(InventoryClickEvent event, ClickSortedPlugin plugin, Player player, SelfTestSession session);
}
