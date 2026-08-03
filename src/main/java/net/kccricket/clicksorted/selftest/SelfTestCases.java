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

import net.kccricket.clicksorted.events.InventorySortEvent;
import net.kccricket.clicksorted.model.ClickMethod;
import net.kccricket.clicksorted.model.FillAxis;
import net.kccricket.clicksorted.model.SortKey;
import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.model.StartCorner;
import net.kccricket.clicksorted.security.Permissions;
import net.kccricket.clicksorted.sort.BundleBlacklist;
import net.kccricket.clicksorted.sort.BundlePacker;
import net.kccricket.clicksorted.sort.InPlacePacker;
import net.kccricket.clicksorted.sort.SlotOrder;
import net.kccricket.clicksorted.sort.SortEngine;
import net.kccricket.clicksorted.sort.TreemapPacker;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The self-test catalog: {@link #AUTO} cases run the pure sort/pack algorithms directly with no
 * Bukkit server interaction; {@link #SIM} cases stage a real {@link Inventory} and dispatch a
 * synthetic {@link InventoryClickEvent} through the live plugin's own listeners, exercising the
 * exact code path a real client click would (permission gates, {@code InventorySortEvent}, locks,
 * blacklists, write-back).
 *
 * <p>Every golden layout below is authored by hand from the documented sort/placement rules — never
 * computed by calling {@link SortEngine}/{@link SlotOrder}/{@link TreemapPacker} and treating the
 * result as "expected". Cases whose correct output is impractical to safely hand-verify positionally
 * (TREEMAP placement, bundle bin selection) are checked instead via conservation, structural
 * invariants, and idempotence, which need no such oracle.
 */
public final class SelfTestCases {

    private SelfTestCases() {
    }

    public static final List<SelfTestCase> AUTO = buildAuto();
    public static final List<SelfTestCase> SIM = buildSim();

    // -------------------------------------------------------------------------
    // Shared fixtures
    // -------------------------------------------------------------------------

    private static final Set<Integer> REGION_27 = InventorySortEvent.rangeSet(0, 27);
    private static final Set<Integer> REGION_HOPPER = InventorySortEvent.rangeSet(0, 5);
    private static final Set<Integer> REGION_DROPPER = InventorySortEvent.rangeSet(0, 9);

    private static ItemStack named(Material mat, String name, int amount) {
        ItemStack is = new ItemStack(mat, amount);
        ItemMeta meta = is.getItemMeta();
        meta.displayName(Component.text(name));
        is.setItemMeta(meta);
        return is;
    }

    private static ItemStack bundleOf(List<ItemStack> contents) {
        ItemStack b = new ItemStack(Material.BUNDLE, 1);
        if (!(b.getItemMeta() instanceof BundleMeta meta)) {
            throw new SelfTestSkip("BUNDLE has no BundleMeta on this server");
        }
        meta.setItems(contents);
        b.setItemMeta(meta);
        return b;
    }

    /** Four distinct items sharing one material (STONE) so they're mergeable-by-key but not by material alone. */
    private static ItemStack[] fourItemFixture() {
        ItemStack[] staged = new ItemStack[27];
        staged[0] = named(Material.STONE, "a", 40);
        staged[1] = named(Material.STONE, "a", 30);
        staged[2] = named(Material.STONE, "b", 5);
        staged[3] = named(Material.STONE, "c", 64);
        return staged;
    }

    private static SelfTestCase of(String id, SelfTestCase.Phase phase, Function<SelfTestContext, Optional<String>> body) {
        return new SelfTestCase() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public SelfTestCase.Phase phase() {
                return phase;
            }

            @Override
            public Optional<String> run(SelfTestContext ctx) {
                return body.apply(ctx);
            }
        };
    }

    // -------------------------------------------------------------------------
    // AUTO catalog
    // -------------------------------------------------------------------------

    private static List<SelfTestCase> buildAuto() {
        List<SelfTestCase> cases = new ArrayList<>();

        // Positional goldens: hand-derived from SlotOrder's documented corner/axis rules, not from
        // calling SlotOrder itself. See the two derivations in the class javadoc history / PR.
        cases.add(of("auto-linear-top_left-horizontal-golden", SelfTestCase.Phase.AUTO,
                ctx -> linearGolden(StartCorner.TOP_LEFT, FillAxis.HORIZONTAL, goldenTopLeftHorizontal())));
        cases.add(of("auto-linear-bottom_right-horizontal-golden", SelfTestCase.Phase.AUTO,
                ctx -> linearGolden(StartCorner.BOTTOM_RIGHT, FillAxis.HORIZONTAL, goldenBottomRightHorizontal())));

        // Remaining corner/axis combinations: conservation + no-gap + no-duplicate-partial + idempotence.
        for (StartCorner corner : StartCorner.values()) {
            for (FillAxis axis : FillAxis.values()) {
                if (corner == StartCorner.TOP_LEFT && axis == FillAxis.HORIZONTAL) continue;
                if (corner == StartCorner.BOTTOM_RIGHT && axis == FillAxis.HORIZONTAL) continue;
                String id = "auto-linear-" + corner.name().toLowerCase(Locale.ROOT) + "-"
                        + axis.name().toLowerCase(Locale.ROOT) + "-conservation";
                cases.add(of(id, SelfTestCase.Phase.AUTO,
                        ctx -> linearConservation(corner, axis, fourItemFixture(), REGION_27, 0, 9)));
            }
        }

        cases.add(of("auto-linear-hopper-geometry-conservation", SelfTestCase.Phase.AUTO,
                ctx -> linearConservation(StartCorner.TOP_LEFT, FillAxis.HORIZONTAL,
                        threeItemFixture(5), REGION_HOPPER, 0, 5)));
        cases.add(of("auto-linear-dropper-geometry-conservation", SelfTestCase.Phase.AUTO,
                ctx -> linearConservation(StartCorner.TOP_LEFT, FillAxis.HORIZONTAL,
                        threeItemFixture(9), REGION_DROPPER, 0, 3)));

        cases.add(of("auto-treemap-top_left-horizontal-conservation", SelfTestCase.Phase.AUTO,
                ctx -> treemapConservation(StartCorner.TOP_LEFT, FillAxis.HORIZONTAL, fourItemFixture(), REGION_27, 0, 9, 3)));
        cases.add(of("auto-treemap-bottom_right-vertical-conservation", SelfTestCase.Phase.AUTO,
                ctx -> treemapConservation(StartCorner.BOTTOM_RIGHT, FillAxis.VERTICAL, fourItemFixture(), REGION_27, 0, 9, 3)));

        cases.add(of("auto-merge-fungible-meta-aware", SelfTestCase.Phase.AUTO, ctx -> mergeFungibleMetaAware()));
        cases.add(of("auto-nonfungible-passthrough", SelfTestCase.Phase.AUTO, ctx -> nonfungiblePassthrough()));

        cases.add(of("auto-bundle-pack-conservation", SelfTestCase.Phase.AUTO, ctx -> bundlePackConservation()));
        cases.add(of("auto-bundle-pack-idempotent", SelfTestCase.Phase.AUTO, ctx -> bundlePackIdempotent()));
        cases.add(of("auto-inplace-pack-conservation", SelfTestCase.Phase.AUTO, ctx -> inplacePackConservation()));
        cases.add(of("auto-inplace-pack-idempotent", SelfTestCase.Phase.AUTO, ctx -> inplacePackIdempotent()));

        return List.copyOf(cases);
    }

    private static ItemStack[] threeItemFixture(int size) {
        ItemStack[] staged = new ItemStack[size];
        staged[0] = named(Material.STONE, "a", 40);
        staged[1] = named(Material.STONE, "b", 5);
        staged[2] = named(Material.STONE, "a", 10);
        return staged;
    }

    /**
     * TOP_LEFT + HORIZONTAL over a 9-wide/3-row chest is the identity ordering (ascending slot
     * order), so the golden is simply the NAME-sorted, merged sequence written from slot 0: keys
     * "a" (40+30=70 -&gt; 64 full stack + 6 remainder), "b" (5), "c" (64) in that order.
     */
    private static ItemStack[] goldenTopLeftHorizontal() {
        ItemStack[] expected = new ItemStack[27];
        expected[0] = named(Material.STONE, "a", 64);
        expected[1] = named(Material.STONE, "a", 6);
        expected[2] = named(Material.STONE, "b", 5);
        expected[3] = named(Material.STONE, "c", 64);
        return expected;
    }

    /**
     * BOTTOM_RIGHT + HORIZONTAL fills the bottom row right-to-left first, then the row above,
     * etc.: fill order for a 9x3 grid is slot 26, 25, 24, ..., 18, 17, ..., 0. The same four
     * merged stacks as {@link #goldenTopLeftHorizontal()} therefore land at slots 26, 25, 24, 23
     * respectively (first-placed item goes to the first fill slot).
     */
    private static ItemStack[] goldenBottomRightHorizontal() {
        ItemStack[] expected = new ItemStack[27];
        expected[26] = named(Material.STONE, "a", 64);
        expected[25] = named(Material.STONE, "a", 6);
        expected[24] = named(Material.STONE, "b", 5);
        expected[23] = named(Material.STONE, "c", 64);
        return expected;
    }

    private static Optional<String> linearGolden(StartCorner corner, FillAxis axis, ItemStack[] golden) {
        ItemStack[] staged = fourItemFixture();
        List<ItemStack> sorted = SortEngine.sortAndMerge(staged, REGION_27, SortingMethod.NAME);
        List<Integer> fillOrder = SlotOrder.order(REGION_27, 0, 9, corner, axis);
        ItemStack[] actual = new ItemStack[27];
        int next = 0;
        for (int slot : fillOrder) {
            actual[slot] = next < sorted.size() ? sorted.get(next++) : null;
        }
        return SelfTestEvaluator.compareLayout(actual, golden);
    }

    private static Optional<String> linearConservation(StartCorner corner, FillAxis axis, ItemStack[] staged,
                                                        Set<Integer> slots, int base, int width) {
        ItemCensus before = ItemCensus.of(staged);
        List<ItemStack> sorted = SortEngine.sortAndMerge(staged, slots, SortingMethod.NAME);
        List<Integer> fillOrder = SlotOrder.order(slots, base, width, corner, axis);
        ItemStack[] actual = staged.clone();
        for (int s : slots) actual[s] = null;
        int next = 0;
        for (int slot : fillOrder) {
            if (next < sorted.size()) actual[slot] = sorted.get(next++);
        }
        List<ItemStack> overflow = next < sorted.size() ? sorted.subList(next, sorted.size()) : List.of();

        Optional<String> cons = SelfTestEvaluator.compareCensus(before, ItemCensus.of(actual), overflow);
        if (cons.isPresent()) return cons;
        Optional<String> gap = SelfTestEvaluator.noGapBeforeNonEmpty(actual, fillOrder);
        if (gap.isPresent()) return gap;
        Optional<String> dup = SelfTestEvaluator.noDuplicatePartialStacks(actual, slots);
        if (dup.isPresent()) return dup;

        // Idempotence: sorting the already-sorted layout again must not change anything.
        List<ItemStack> sorted2 = SortEngine.sortAndMerge(actual, slots, SortingMethod.NAME);
        ItemStack[] actual2 = actual.clone();
        for (int s : slots) actual2[s] = null;
        int next2 = 0;
        for (int slot : fillOrder) {
            if (next2 < sorted2.size()) actual2[slot] = sorted2.get(next2++);
        }
        return SelfTestEvaluator.idempotent(actual, actual2);
    }

    private static Optional<String> treemapConservation(StartCorner corner, FillAxis axis, ItemStack[] staged,
                                                         Set<Integer> slots, int base, int width, int rows) {
        ItemCensus before = ItemCensus.of(staged);
        List<ItemStack> sorted = SortEngine.sortAndMerge(staged, slots, SortingMethod.TREEMAP);
        Map<Integer, ItemStack> placement = TreemapPacker.pack(sorted, slots, base, width, rows, corner, axis);
        ItemStack[] actual = staged.clone();
        for (int s : slots) actual[s] = placement.get(s);

        Set<ItemStack> placed = Collections.newSetFromMap(new IdentityHashMap<>());
        placed.addAll(placement.values());
        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack it : sorted) {
            if (!placed.contains(it)) overflow.add(it);
        }

        Optional<String> cons = SelfTestEvaluator.compareCensus(before, ItemCensus.of(actual), overflow);
        if (cons.isPresent()) return cons;

        List<ItemStack> sorted2 = SortEngine.sortAndMerge(actual, slots, SortingMethod.TREEMAP);
        Map<Integer, ItemStack> placement2 = TreemapPacker.pack(sorted2, slots, base, width, rows, corner, axis);
        ItemStack[] actual2 = actual.clone();
        for (int s : slots) actual2[s] = placement2.get(s);
        return SelfTestEvaluator.idempotent(actual, actual2);
    }

    /** Two same-named STONE stacks with different lore (meta) must never merge — SortKey.equals is meta-aware. */
    private static Optional<String> mergeFungibleMetaAware() {
        ItemStack plain = named(Material.STONE, "x", 10);
        ItemStack loreVariant = named(Material.STONE, "x", 5);
        ItemMeta meta = loreVariant.getItemMeta();
        meta.lore(List.of(Component.text("special")));
        loreVariant.setItemMeta(meta);

        List<ItemStack> sorted = SortEngine.sortAndMerge(List.of(plain, loreVariant), SortingMethod.NAME);
        if (sorted.size() != 2) {
            return Optional.of("expected 2 distinct output stacks (different meta must not merge), got "
                    + sorted.size() + ": " + sorted);
        }
        int total = sorted.stream().mapToInt(ItemStack::getAmount).sum();
        if (total != 15) {
            return Optional.of("expected total amount 15, got " + total);
        }
        boolean sawTen = sorted.stream().anyMatch(is -> is.getAmount() == 10);
        boolean sawFive = sorted.stream().anyMatch(is -> is.getAmount() == 5);
        if (!sawTen || !sawFive) {
            return Optional.of("expected one stack of 10 and one of 5, got amounts "
                    + sorted.stream().map(ItemStack::getAmount).toList());
        }
        return Optional.empty();
    }

    /** Non-stackable items (maxStackSize 1) must never merge even when fully identical. */
    private static Optional<String> nonfungiblePassthrough() {
        ItemStack sword1 = named(Material.DIAMOND_SWORD, "s", 1);
        ItemStack sword2 = named(Material.DIAMOND_SWORD, "s", 1);
        List<ItemStack> sorted = SortEngine.sortAndMerge(List.of(sword1, sword2), SortingMethod.NAME);
        if (sorted.size() != 2) {
            return Optional.of("expected 2 discrete non-stackable stacks, got " + sorted.size() + ": " + sorted);
        }
        return Optional.empty();
    }

    private record PackPass(List<ItemStack> leftover, List<ItemStack> bundles) {
    }

    private static PackPass runBundlePackPass(List<ItemStack> loose, List<ItemStack> bundlesIn, int cap) {
        Map<SortKey, Long> pool = new LinkedHashMap<>();
        Map<SortKey, ItemStack> samples = new LinkedHashMap<>();
        for (ItemStack is : loose) {
            if (is == null) continue;
            SortKey key = SortKey.poolKey(is);
            pool.merge(key, (long) is.getAmount(), Long::sum);
            samples.putIfAbsent(key, is);
        }
        List<ItemStack> bundles = new ArrayList<>();
        for (ItemStack b : bundlesIn) bundles.add(b.clone());
        List<ItemStack> leftover = BundlePacker.packIntoBundles(pool, samples, bundles, cap);
        return new PackPass(leftover, bundles);
    }

    private static ItemStack[] concat(List<ItemStack> a, List<ItemStack> b) {
        List<ItemStack> all = new ArrayList<>(a);
        all.addAll(b);
        return all.toArray(new ItemStack[0]);
    }

    private static Optional<String> bundlePackConservation() {
        ItemStack loose = named(Material.STONE, "a", 40);
        ItemStack bundleContents = named(Material.STONE, "a", 10);
        ItemStack bundle = bundleOf(new ArrayList<>(List.of(bundleContents)));

        ItemCensus before = ItemCensus.of(new ItemStack[]{loose, bundle});
        PackPass pass = runBundlePackPass(List.of(loose), List.of(bundle), 12);
        ItemCensus after = ItemCensus.of(concat(pass.leftover(), pass.bundles()));

        Optional<String> cons = SelfTestEvaluator.compareCensus(before, after, List.of());
        if (cons.isPresent()) return cons;
        return SelfTestEvaluator.bundleCapacityValid(pass.bundles().toArray(new ItemStack[0]), 12);
    }

    private static Optional<String> bundlePackIdempotent() {
        ItemStack loose = named(Material.STONE, "a", 40);
        ItemStack bundleContents = named(Material.STONE, "a", 10);
        ItemStack bundle = bundleOf(new ArrayList<>(List.of(bundleContents)));

        PackPass pass1 = runBundlePackPass(List.of(loose), List.of(bundle), 12);
        PackPass pass2 = runBundlePackPass(pass1.leftover(), pass1.bundles(), 12);

        ItemCensus c1 = ItemCensus.of(concat(pass1.leftover(), pass1.bundles()));
        ItemCensus c2 = ItemCensus.of(concat(pass2.leftover(), pass2.bundles()));
        if (!c1.matches(c2)) {
            return Optional.of("bundle pack not idempotent:\n" + c1.diff(c2));
        }
        return Optional.empty();
    }

    private static Optional<String> inplacePackConservation() {
        ItemStack[] staged = new ItemStack[27];
        staged[0] = named(Material.STONE, "a", 20);
        staged[1] = named(Material.STONE, "a", 10);
        staged[2] = bundleOf(new ArrayList<>());

        ItemCensus before = ItemCensus.of(staged);
        InPlacePacker.Result result = InPlacePacker.consolidate(staged, REGION_27, true, 12, BundleBlacklist.EMPTY);
        ItemStack[] actual = new ItemStack[27];
        for (int s : REGION_27) actual[s] = result.placement().get(s);

        Optional<String> cons = SelfTestEvaluator.compareCensus(before, ItemCensus.of(actual), result.overflow());
        if (cons.isPresent()) return cons;
        return SelfTestEvaluator.bundleCapacityValid(actual, 12);
    }

    private static Optional<String> inplacePackIdempotent() {
        ItemStack[] staged = new ItemStack[27];
        staged[0] = named(Material.STONE, "a", 20);
        staged[1] = named(Material.STONE, "a", 10);
        staged[2] = bundleOf(new ArrayList<>());

        InPlacePacker.Result r1 = InPlacePacker.consolidate(staged, REGION_27, true, 12, BundleBlacklist.EMPTY);
        ItemStack[] pass1 = new ItemStack[27];
        for (int s : REGION_27) pass1[s] = r1.placement().get(s);

        InPlacePacker.Result r2 = InPlacePacker.consolidate(pass1, REGION_27, true, 12, BundleBlacklist.EMPTY);
        ItemStack[] pass2 = new ItemStack[27];
        for (int s : REGION_27) pass2[s] = r2.placement().get(s);

        return SelfTestEvaluator.idempotent(pass1, pass2);
    }

    // -------------------------------------------------------------------------
    // SIM catalog
    // -------------------------------------------------------------------------

    private static final int DUMMY_CHEST_SIZE = 27;
    private static final int MAIN_STORAGE_RAW_BASE = DUMMY_CHEST_SIZE;
    private static final int HOTBAR_RAW_BASE = DUMMY_CHEST_SIZE + 27;

    private static List<SelfTestCase> buildSim() {
        return List.of(
                of("sim-chest-sort-basic-golden", SelfTestCase.Phase.SIM, SelfTestCases::simChestSortBasicGolden),
                of("sim-hotbar-no-pack", SelfTestCase.Phase.SIM, SelfTestCases::simHotbarNoPack),
                of("sim-container-pack-conservation", SelfTestCase.Phase.SIM, SelfTestCases::simContainerPackConservation),
                of("sim-user-locked-slot-untouched", SelfTestCase.Phase.SIM, SelfTestCases::simUserLockedSlotUntouched),
                of("sim-admin-blacklist-item-untouched", SelfTestCase.Phase.SIM, SelfTestCases::simAdminBlacklistItemUntouched),
                of("sim-double-click-cursor-repair", SelfTestCase.Phase.SIM, SelfTestCases::simDoubleClickCursorRepair),
                of("sim-gate-master-perm-denied-noop", SelfTestCase.Phase.SIM, SelfTestCases::simGateMasterPermDeniedNoop),
                of("sim-mode-both-off-noop", SelfTestCase.Phase.SIM, SelfTestCases::simModeBothOffNoop)
        );
    }

    private static InventoryClickEvent swapClick(InventoryView view, int rawSlot) {
        return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, rawSlot,
                ClickType.SWAP_OFFHAND, InventoryAction.UNKNOWN);
    }

    private static InventoryClickEvent doubleClick(InventoryView view, int rawSlot) {
        return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, rawSlot,
                ClickType.DOUBLE_CLICK, InventoryAction.UNKNOWN);
    }

    private static void forceDefaultPrefs(SelfTestContext ctx) {
        var prefs = ctx.plugin().getSortingPrefs();
        Player p = ctx.player();
        prefs.setClickMethod(p, ClickMethod.SWAP);
        prefs.setEnabled(p, true);
        prefs.setSortOverItems(p, true);
        prefs.setSortingMethod(p, SortingMethod.NAME);
        prefs.setStartCorner(p, StartCorner.TOP_LEFT);
        prefs.setFillAxis(p, FillAxis.HORIZONTAL);
        prefs.setBundlePackInInventory(p, false);
        prefs.setBundlePackInContainers(p, false);
        prefs.setLockedSlots(p, Set.of());
    }

    private static Optional<String> simChestSortBasicGolden(SelfTestContext ctx) {
        forceDefaultPrefs(ctx);
        Player player = ctx.player();

        Inventory chest = Bukkit.createInventory(null, InventoryType.CHEST);
        ItemStack[] staged = fourItemFixture();
        for (int i = 0; i < staged.length; i++) chest.setItem(i, staged[i]);

        InventoryView view = player.openInventory(chest);
        InventoryClickEvent event = swapClick(view, 0);
        Bukkit.getPluginManager().callEvent(event);

        if (!event.isCancelled()) {
            return Optional.of("SWAP click should have been cancelled");
        }
        return SelfTestEvaluator.compareLayout(chest.getContents(), goldenTopLeftHorizontal());
    }

    private static Optional<String> simHotbarNoPack(SelfTestContext ctx) {
        var prefs = ctx.plugin().getSortingPrefs();
        Player player = ctx.player();
        prefs.setClickMethod(player, ClickMethod.SWAP);
        prefs.setEnabled(player, false); // sorting off — only packing is "wanted"
        prefs.setBundlePackInInventory(player, true); // must still never apply to the hotbar
        prefs.setSortOverItems(player, true);
        prefs.setLockedSlots(player, Set.of());

        ItemStack bundle = bundleOf(new ArrayList<>(List.of(named(Material.STONE, "a", 5))));
        ItemStack loose = named(Material.STONE, "a", 3);
        player.getInventory().setItem(0, bundle);
        player.getInventory().setItem(1, loose);
        ItemStack[] before = player.getInventory().getContents().clone();

        Inventory dummy = Bukkit.createInventory(null, InventoryType.CHEST);
        InventoryView view = player.openInventory(dummy);
        Bukkit.getPluginManager().callEvent(swapClick(view, HOTBAR_RAW_BASE));

        ItemStack[] after = player.getInventory().getContents();
        return SelfTestEvaluator.compareLayout(after, before)
                .map(s -> "hotbar changed even though only packing was requested (packing must never apply to the hotbar): " + s);
    }

    private static Optional<String> simContainerPackConservation(SelfTestContext ctx) {
        var prefs = ctx.plugin().getSortingPrefs();
        Player player = ctx.player();
        prefs.setClickMethod(player, ClickMethod.SWAP);
        prefs.setEnabled(player, true);
        prefs.setBundlePackInContainers(player, true);
        prefs.setSortOverItems(player, true);
        prefs.setBundleStackLimit(player, 12);
        prefs.setLockedSlots(player, Set.of());

        Inventory chest = Bukkit.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, named(Material.STONE, "a", 40));
        chest.setItem(1, bundleOf(new ArrayList<>(List.of(named(Material.STONE, "a", 10)))));
        ItemCensus before = ItemCensus.of(chest.getContents());

        InventoryView view = player.openInventory(chest);
        Bukkit.getPluginManager().callEvent(swapClick(view, 0));

        Optional<String> cons = SelfTestEvaluator.compareCensus(before, ItemCensus.of(chest.getContents()), List.of());
        if (cons.isPresent()) return cons;
        return SelfTestEvaluator.bundleCapacityValid(chest.getContents(), 12);
    }

    private static Optional<String> simUserLockedSlotUntouched(SelfTestContext ctx) {
        forceDefaultPrefs(ctx);
        Player player = ctx.player();
        prefs(ctx).setLockedSlots(player, Set.of(9));

        player.getInventory().setItem(9, named(Material.STONE, "locked", 7));
        player.getInventory().setItem(10, named(Material.STONE, "m", 5));
        player.getInventory().setItem(11, named(Material.STONE, "m", 3));

        Inventory dummy = Bukkit.createInventory(null, InventoryType.CHEST);
        InventoryView view = player.openInventory(dummy);
        Bukkit.getPluginManager().callEvent(swapClick(view, MAIN_STORAGE_RAW_BASE));

        ItemStack lockedSlot = player.getInventory().getItem(9);
        if (lockedSlot == null || lockedSlot.getAmount() != 7) {
            return Optional.of("locked slot 9 changed: now " + lockedSlot);
        }
        ItemStack merged = player.getInventory().getItem(10);
        if (merged == null || merged.getAmount() != 8) {
            return Optional.of("expected merged 'm' stack of 8 at slot 10, got " + merged);
        }
        return Optional.empty();
    }

    private static Optional<String> simAdminBlacklistItemUntouched(SelfTestContext ctx) {
        forceDefaultPrefs(ctx);
        Player player = ctx.player();

        Inventory chest = Bukkit.createInventory(null, InventoryType.CHEST);
        ItemStack blacklisted = named(Material.STONE, "blacklisted", 9);
        chest.setItem(0, blacklisted);
        chest.setItem(1, named(Material.STONE, "m", 5));
        chest.setItem(2, named(Material.STONE, "m", 3));

        PermissionAttachment attachment = player.addAttachment(ctx.plugin());
        attachment.setPermission(Permissions.PERM_BLACKLIST_MATERIAL + "stone", true);
        try {
            InventoryView view = player.openInventory(chest);
            Bukkit.getPluginManager().callEvent(swapClick(view, 0));
        } finally {
            player.removeAttachment(attachment);
        }

        ItemStack stillThere = chest.getItem(0);
        if (stillThere == null || stillThere.getAmount() != 9) {
            return Optional.of("blacklisted item at slot 0 was moved/changed: now " + stillThere);
        }
        return Optional.empty();
    }

    private static Optional<String> simDoubleClickCursorRepair(SelfTestContext ctx) {
        var prefs = ctx.plugin().getSortingPrefs();
        Player player = ctx.player();
        prefs.setClickMethod(player, ClickMethod.DOUBLE_CLICK);
        prefs.setEnabled(player, true);
        prefs.setSortOverItems(player, true);
        prefs.setBundlePackInContainers(player, false);
        prefs.setLockedSlots(player, Set.of());

        Inventory chest = Bukkit.createInventory(null, InventoryType.CHEST);
        chest.setItem(1, named(Material.STONE, "d", 3));
        // Slot 0 starts empty, as if the first click of the double-click already lifted its stack.
        InventoryView view = player.openInventory(chest);
        player.setItemOnCursor(named(Material.STONE, "d", 5));

        Bukkit.getPluginManager().callEvent(doubleClick(view, 0));

        ItemStack cursor = player.getItemOnCursor();
        boolean cursorEmpty = cursor == null || cursor.getType() == Material.AIR;
        if (!cursorEmpty) {
            return Optional.of("cursor should be empty after the DOUBLE_CLICK repair/sort, but holds " + cursor);
        }
        int total = 0;
        for (ItemStack is : chest.getContents()) {
            if (is != null && is.getType() == Material.STONE) total += is.getAmount();
        }
        if (total != 8) {
            return Optional.of("expected 8 total STONE 'd' in the chest after repair+sort, got " + total);
        }
        return Optional.empty();
    }

    private static Optional<String> simGateMasterPermDeniedNoop(SelfTestContext ctx) {
        forceDefaultPrefs(ctx);
        Player player = ctx.player();

        Inventory chest = Bukkit.createInventory(null, InventoryType.CHEST);
        chest.setItem(2, named(Material.STONE, "b", 5));
        chest.setItem(0, named(Material.STONE, "a", 5));
        ItemStack[] before = chest.getContents().clone();

        PermissionAttachment attachment = player.addAttachment(ctx.plugin());
        attachment.setPermission(Permissions.PERM_MASTER, false);
        try {
            InventoryView view = player.openInventory(chest);
            Bukkit.getPluginManager().callEvent(swapClick(view, 0));
        } finally {
            player.removeAttachment(attachment);
        }

        return SelfTestEvaluator.compareLayout(chest.getContents(), before)
                .map(s -> "inventory changed despite the master permission being denied: " + s);
    }

    private static Optional<String> simModeBothOffNoop(SelfTestContext ctx) {
        var prefs = ctx.plugin().getSortingPrefs();
        Player player = ctx.player();
        prefs.setClickMethod(player, ClickMethod.SWAP);
        prefs.setEnabled(player, false);
        prefs.setBundlePackInContainers(player, false);
        prefs.setSortOverItems(player, true);

        Inventory chest = Bukkit.createInventory(null, InventoryType.CHEST);
        chest.setItem(2, named(Material.STONE, "b", 5));
        chest.setItem(0, named(Material.STONE, "a", 5));
        ItemStack[] before = chest.getContents().clone();

        InventoryView view = player.openInventory(chest);
        Bukkit.getPluginManager().callEvent(swapClick(view, 0));

        return SelfTestEvaluator.compareLayout(chest.getContents(), before)
                .map(s -> "inventory changed even though sorting and packing are both off: " + s);
    }

    private static net.kccricket.clicksorted.model.PlayerSortingPrefs prefs(SelfTestContext ctx) {
        return ctx.plugin().getSortingPrefs();
    }
}
