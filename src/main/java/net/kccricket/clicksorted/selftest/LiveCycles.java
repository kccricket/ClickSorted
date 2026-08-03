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
import net.kccricket.clicksorted.security.Permissions;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The LIVE-phase cycle catalog. {@link #quick()} is the 6-cycle click-method sweep (one per
 * {@link ClickMethod}); {@link #full()} adds five scenario cycles, all fixed to {@link ClickMethod#SWAP}
 * since gesture variety is already covered by the sweep. Mirrors {@link SelfTestCases}' {@code of(...)}
 * factory idiom.
 */
public final class LiveCycles {

    private LiveCycles() {
    }

    /** Combined amount of the two staged "a"/"m" stacks in every sweep cycle and the player-main region cycle. */
    static final int SWEEP_STAGE_TOTAL = 9;

    public static List<LiveCycle> quick() {
        return List.copyOf(sweepCycles());
    }

    public static List<LiveCycle> full() {
        List<LiveCycle> cycles = new ArrayList<>(sweepCycles());
        cycles.add(regionPlayerMain());
        cycles.add(regionHotbarNoPack());
        cycles.add(lockedSlot());
        cycles.add(blacklist());
        cycles.add(bundlePack());
        return List.copyOf(cycles);
    }

    // -------------------------------------------------------------------------
    // Shared fixtures
    // -------------------------------------------------------------------------

    private static ItemStack stagedItem(String name, int amount) {
        ItemStack is = new ItemStack(Material.STONE, amount);
        ItemMeta meta = is.getItemMeta();
        meta.displayName(Component.text(name));
        is.setItemMeta(meta);
        return is;
    }

    private static ItemStack bundleOf(ItemStack... contents) {
        ItemStack b = new ItemStack(Material.BUNDLE, 1);
        if (!(b.getItemMeta() instanceof BundleMeta meta)) {
            throw new SelfTestSkip("BUNDLE has no BundleMeta on this server");
        }
        meta.setItems(List.of(contents));
        b.setItemMeta(meta);
        return b;
    }

    private static boolean isEmpty(ItemStack is) {
        return is == null || is.getType() == Material.AIR;
    }

    /**
     * Voids player main storage (slots 9..35 — see {@code MainConfig.PLAYER_STORAGE_END}) before a
     * region/lock scenario cycle stages its fixtures there. Without this, a tester's real items
     * already occupying that region get swept into the same sort as the fixture — the SortEngine
     * doesn't know or care which items are "ours" — so a fixed-slot assertion like "the merged stack
     * lands at slot 9" only held on a character with an empty main storage, which is never true in
     * practice. Safe to void: {@link SelfTestSession}'s constructor snapshots the whole inventory
     * before any cycle runs, and {@link SelfTestSession#restoreAndClear()} restores it in full.
     */
    private static void clearMainStorage(Player player) {
        for (int slot = 9; slot < 36; slot++) {
            player.getInventory().setItem(slot, null);
        }
    }

    /** Voids the hotbar (slots 0..8) before a hotbar scenario cycle stages its fixtures there — see {@link #clearMainStorage}. */
    private static void clearHotbar(Player player) {
        for (int slot = 0; slot < 9; slot++) {
            player.getInventory().setItem(slot, null);
        }
    }

    /** Raw (unrendered) lang lookup, empty string if the key is absent — mirrors {@link ClickMethod#getInstruction}. */
    private static String lang(ClickSortedPlugin plugin, Locale locale, String key) {
        return plugin.getConfigManager().lang(locale).raw(key, "");
    }

    /** "In chest: <method's own instruction>" — SWAP has no ambiguity about which slot/item, so the generic instruction composes safely; used by the two chest-based scenario cycles (blacklist, bundle-pack). */
    private static Instructor inChest(ClickMethod method) {
        return (plugin, locale) -> lang(plugin, locale, "selfTestAreaChest") + " " + method.getInstruction(locale);
    }

    /** "In main storage (not hotbar): <method's own instruction>" — the region/lock scenario cycles (SWAP only). */
    private static Instructor inMainStorage(ClickMethod method) {
        return (plugin, locale) -> lang(plugin, locale, "selfTestAreaMainStorage") + " " + method.getInstruction(locale);
    }

    /** "In hotbar (packing must not apply): <method's own instruction>" — the hotbar-no-pack scenario cycle (SWAP only). */
    private static Instructor inHotbar(ClickMethod method) {
        return (plugin, locale) -> lang(plugin, locale, "selfTestAreaHotbar") + " " + method.getInstruction(locale);
    }

    /**
     * The sweep's per-method instruction, from a dedicated {@code selfTestInstruction<Method>} lang
     * key rather than composed from the generic {@code instruction*} keys: the sweep's dummy chest
     * holds exactly two staged item stacks and nothing else, so "an occupied slot" or "pick up an
     * item" must name those two stacks explicitly, not read as "anywhere on screen" — which would
     * include the tester's own real inventory below the chest and sort it for real. See lang file
     * comment above {@code selfTestInstructionSingle}.
     */
    private static Instructor sweepInstruction(ClickMethod method) {
        String key = switch (method) {
            case SINGLE_CLICK -> "selfTestInstructionSingle";
            case DOUBLE_CLICK -> "selfTestInstructionDouble";
            case SWAP -> "selfTestInstructionSwap";
            case CONTROL_DROP -> "selfTestInstructionControlDrop";
            case SHIFT_LEFT_CLICK -> "selfTestInstructionShiftLeftClick";
            case SHIFT_RIGHT_CLICK -> "selfTestInstructionShiftRightClick";
        };
        return (plugin, locale) -> lang(plugin, locale, key);
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    @FunctionalInterface
    private interface Stager {
        Inventory stage(ClickSortedPlugin plugin, Player player, SelfTestSession session);
    }

    @FunctionalInterface
    private interface Matcher {
        boolean matches(InventoryClickEvent event, Player player, SelfTestSession session);
    }

    @FunctionalInterface
    private interface Evaluator {
        Optional<String> evaluate(InventoryClickEvent event, ClickSortedPlugin plugin, Player player, SelfTestSession session);
    }

    /** Which inventory area to click in and, via the wrapped {@link ClickMethod} instruction, whether the target slot must be empty or occupied. */
    @FunctionalInterface
    private interface Instructor {
        String instruct(ClickSortedPlugin plugin, Locale locale);
    }

    private static LiveCycle of(String id, ClickMethod method, Instructor instructor, Stager stager, Matcher matcher, Evaluator evaluator) {
        return ofSkippable(id, method, probes -> Optional.empty(), instructor, stager, matcher, evaluator);
    }

    private static LiveCycle ofSkippable(String id, ClickMethod method, Function<CompatibilityReport, Optional<String>> skip,
                                          Instructor instructor, Stager stager, Matcher matcher, Evaluator evaluator) {
        return new LiveCycle() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public ClickMethod clickMethod() {
                return method;
            }

            @Override
            public String instruction(ClickSortedPlugin plugin, Locale locale) {
                return instructor.instruct(plugin, locale);
            }

            @Override
            public Optional<String> skipReason(CompatibilityReport probes) {
                return probes == null ? Optional.empty() : skip.apply(probes);
            }

            @Override
            public Inventory stage(ClickSortedPlugin plugin, Player player, SelfTestSession session) {
                return stager.stage(plugin, player, session);
            }

            @Override
            public boolean matches(InventoryClickEvent event, Player player, SelfTestSession session) {
                return matcher.matches(event, player, session);
            }

            @Override
            public Optional<String> evaluate(InventoryClickEvent event, ClickSortedPlugin plugin, Player player, SelfTestSession session) {
                return evaluator.evaluate(event, plugin, player, session);
            }
        };
    }

    // -------------------------------------------------------------------------
    // Sweep: one cycle per ClickMethod, in a dummy test chest
    // -------------------------------------------------------------------------

    private static List<LiveCycle> sweepCycles() {
        List<LiveCycle> cycles = new ArrayList<>();
        for (ClickMethod method : ClickMethod.values()) {
            cycles.add(of("live-" + method.name(), method, sweepInstruction(method),
                    (plugin, player, session) -> {
                        plugin.getSortingPrefs().setClickMethod(player, method);
                        Inventory chest = Bukkit.createInventory(null, InventoryType.CHEST);
                        chest.setItem(0, stagedItem("a", 5));
                        chest.setItem(1, stagedItem("a", 4));
                        return chest;
                    },
                    (event, player, session) -> session.testChest != null
                            && event.getClickedInventory() != null && event.getClickedInventory().equals(session.testChest)
                            && method.matchesSortTrigger(event),
                    (event, plugin, player, session) -> {
                        Inventory chest = session.testChest;
                        ItemStack[] expected = new ItemStack[chest.getSize()];
                        expected[0] = stagedItem("a", SWEEP_STAGE_TOTAL);
                        Optional<String> layout = SelfTestEvaluator.compareLayout(chest.getContents(), expected);
                        if (layout.isPresent()) {
                            return layout;
                        }
                        boolean expectCancel = method.shouldCancelEvent();
                        if (event.isCancelled() != expectCancel) {
                            return Optional.of("expected event.isCancelled()=" + expectCancel + " for " + method
                                    + ", got " + event.isCancelled());
                        }
                        return Optional.empty();
                    }));
        }
        return cycles;
    }

    // -------------------------------------------------------------------------
    // Scenario cycles
    // -------------------------------------------------------------------------

    /**
     * The only place anywhere that validates the raw-slot -&gt; Region mapping against a real client
     * raw slot: the tester's own main storage, with a chest open. A synthetic SIM event can't prove
     * this by construction — its raw-slot constants are the plugin's own assumptions, restated.
     */
    private static LiveCycle regionPlayerMain() {
        return of("live-region-player-main", ClickMethod.SWAP, inMainStorage(ClickMethod.SWAP),
                (plugin, player, session) -> {
                    plugin.getSortingPrefs().setClickMethod(player, ClickMethod.SWAP);
                    clearMainStorage(player);
                    player.getInventory().setItem(9, stagedItem("m", 5));
                    player.getInventory().setItem(10, stagedItem("m", 4));
                    return Bukkit.createInventory(null, InventoryType.CHEST);
                },
                (event, player, session) -> event.getClickedInventory() != null
                        && event.getClickedInventory().equals(player.getInventory())
                        && event.getSlot() >= 9 && event.getSlot() <= 35
                        && ClickMethod.SWAP.matchesSortTrigger(event),
                (event, plugin, player, session) -> {
                    ItemStack merged = player.getInventory().getItem(9);
                    if (merged == null || merged.getAmount() != SWEEP_STAGE_TOTAL) {
                        return Optional.of("expected merged 'm' stack of " + SWEEP_STAGE_TOTAL
                                + " at main-storage slot 9, got " + merged);
                    }
                    for (int slot = 10; slot <= 35; slot++) {
                        ItemStack is = player.getInventory().getItem(slot);
                        if (!isEmpty(is)) {
                            return Optional.of("expected main-storage slot " + slot + " to be empty after the merge, found " + is);
                        }
                    }
                    return Optional.empty();
                });
    }

    /**
     * Packing must never apply to the hotbar, but <em>sorting</em> is legitimately on throughout
     * this run and is allowed to reposition hotbar items freely (only {@code packAllowed} special-
     * cases {@code Region.HOTBAR} to {@code false} — sorting itself has no such carve-out). So this
     * evaluates by content, not fixed slot position: the bundle must still hold exactly its original
     * 5 "a" stone (not have absorbed the loose 3), and those 3 must still exist loose somewhere in
     * the hotbar — wherever the sort put them.
     */
    private static LiveCycle regionHotbarNoPack() {
        return of("live-region-hotbar-no-pack", ClickMethod.SWAP, inHotbar(ClickMethod.SWAP),
                (plugin, player, session) -> {
                    var prefs = plugin.getSortingPrefs();
                    prefs.setClickMethod(player, ClickMethod.SWAP);
                    prefs.setBundlePackInInventory(player, true);
                    clearHotbar(player);
                    player.getInventory().setItem(0, bundleOf(stagedItem("a", 5)));
                    player.getInventory().setItem(1, stagedItem("a", 3));
                    return Bukkit.createInventory(null, InventoryType.CHEST);
                },
                (event, player, session) -> event.getClickedInventory() != null
                        && event.getClickedInventory().equals(player.getInventory())
                        && event.getSlot() >= 0 && event.getSlot() <= 8
                        && ClickMethod.SWAP.matchesSortTrigger(event),
                (event, plugin, player, session) -> {
                    ItemStack looseProbe = stagedItem("a", 1);
                    int looseAmount = 0;
                    int bundleInnerAmount = -1;
                    int bundleCount = 0;
                    for (int slot = 0; slot < 9; slot++) {
                        ItemStack is = player.getInventory().getItem(slot);
                        if (isEmpty(is)) {
                            continue;
                        }
                        if (is.getType() == Material.BUNDLE) {
                            bundleCount++;
                            if (!(is.getItemMeta() instanceof BundleMeta meta)) {
                                return Optional.of("staged bundle lost its BundleMeta after sort at hotbar slot " + slot);
                            }
                            bundleInnerAmount = meta.getItems().stream().mapToInt(ItemStack::getAmount).sum();
                        } else if (is.isSimilar(looseProbe)) {
                            looseAmount += is.getAmount();
                        } else {
                            return Optional.of("unexpected item in hotbar at slot " + slot + " after sort: " + is);
                        }
                    }
                    if (bundleCount != 1) {
                        return Optional.of("expected exactly 1 bundle in the hotbar after sort, found " + bundleCount);
                    }
                    if (bundleInnerAmount != 5) {
                        return Optional.of("packing must never apply to the hotbar, but the bundle's contents changed: "
                                + "now holds " + bundleInnerAmount + " (expected 5, unpacked)");
                    }
                    if (looseAmount != 3) {
                        return Optional.of("packing must never apply to the hotbar, but the loose 'a' stone amount "
                                + "changed: now " + looseAmount + " (expected 3, still unpacked)");
                    }
                    return Optional.empty();
                });
    }

    private static LiveCycle lockedSlot() {
        return of("live-locked-slot", ClickMethod.SWAP, inMainStorage(ClickMethod.SWAP),
                (plugin, player, session) -> {
                    var prefs = plugin.getSortingPrefs();
                    prefs.setClickMethod(player, ClickMethod.SWAP);
                    prefs.setLockedSlots(player, Set.of(9));
                    clearMainStorage(player);
                    player.getInventory().setItem(9, stagedItem("locked", 7));
                    player.getInventory().setItem(10, stagedItem("m", 5));
                    player.getInventory().setItem(11, stagedItem("m", 3));
                    return Bukkit.createInventory(null, InventoryType.CHEST);
                },
                (event, player, session) -> event.getClickedInventory() != null
                        && event.getClickedInventory().equals(player.getInventory())
                        && event.getSlot() >= 9 && event.getSlot() <= 35
                        && ClickMethod.SWAP.matchesSortTrigger(event),
                (event, plugin, player, session) -> {
                    ItemStack lockedSlot = player.getInventory().getItem(9);
                    if (lockedSlot == null || lockedSlot.getAmount() != 7) {
                        return Optional.of("locked slot 9 changed: now " + lockedSlot);
                    }
                    ItemStack merged = player.getInventory().getItem(10);
                    if (merged == null || merged.getAmount() != 8) {
                        return Optional.of("expected merged 'm' stack of 8 at slot 10, got " + merged);
                    }
                    return Optional.empty();
                });
    }

    /** Grants a temporary blacklist permission via a {@link PermissionAttachment} stored on the session — released in {@link SelfTestSession#restoreAndClear()}. */
    private static LiveCycle blacklist() {
        return of("live-blacklist", ClickMethod.SWAP, inChest(ClickMethod.SWAP),
                (plugin, player, session) -> {
                    plugin.getSortingPrefs().setClickMethod(player, ClickMethod.SWAP);
                    Inventory chest = Bukkit.createInventory(null, InventoryType.CHEST);
                    chest.setItem(0, stagedItem("blacklisted", 9));
                    chest.setItem(1, stagedItem("m", 5));
                    chest.setItem(2, stagedItem("m", 3));
                    PermissionAttachment attachment = player.addAttachment(plugin);
                    attachment.setPermission(Permissions.PERM_BLACKLIST_MATERIAL + "stone", true);
                    session.liveAttachment = attachment;
                    return chest;
                },
                (event, player, session) -> session.testChest != null
                        && event.getClickedInventory() != null && event.getClickedInventory().equals(session.testChest)
                        && ClickMethod.SWAP.matchesSortTrigger(event),
                (event, plugin, player, session) -> {
                    ItemStack stillThere = session.testChest.getItem(0);
                    if (stillThere == null || stillThere.getAmount() != 9) {
                        return Optional.of("blacklisted item at slot 0 was moved/changed: now " + stillThere);
                    }
                    return Optional.empty();
                });
    }

    /** Skips (not fails) when the {@code bundle-material} capability probe came back non-PRESENT. */
    private static LiveCycle bundlePack() {
        return ofSkippable("live-bundle-pack", ClickMethod.SWAP,
                probes -> probes.find("bundle-material")
                        .filter(p -> p.status() != CapabilityProbe.Status.PRESENT)
                        .map(p -> "bundle-material capability probe came back " + p.status() + ": " + p.detail()),
                inChest(ClickMethod.SWAP),
                (plugin, player, session) -> {
                    var prefs = plugin.getSortingPrefs();
                    prefs.setClickMethod(player, ClickMethod.SWAP);
                    prefs.setBundlePackInContainers(player, true);
                    prefs.setBundleStackLimit(player, 12);
                    Inventory chest = Bukkit.createInventory(null, InventoryType.CHEST);
                    chest.setItem(0, stagedItem("a", 40));
                    chest.setItem(1, bundleOf(stagedItem("a", 10)));
                    return chest;
                },
                (event, player, session) -> session.testChest != null
                        && event.getClickedInventory() != null && event.getClickedInventory().equals(session.testChest)
                        && ClickMethod.SWAP.matchesSortTrigger(event),
                (event, plugin, player, session) -> {
                    ItemCensus before = ItemCensus.of(new ItemStack[]{stagedItem("a", 40), bundleOf(stagedItem("a", 10))});
                    ItemCensus after = ItemCensus.of(session.testChest.getContents());
                    Optional<String> cons = SelfTestEvaluator.compareCensus(before, after, List.of());
                    if (cons.isPresent()) {
                        return cons;
                    }
                    return SelfTestEvaluator.bundleCapacityValid(session.testChest.getContents(), 12);
                });
    }
}
