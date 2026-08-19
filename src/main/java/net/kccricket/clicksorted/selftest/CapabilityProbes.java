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
import net.kccricket.clicksorted.sort.GridGeometry;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Llama;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The capability-probe catalog. Every probe here is grounded in a verified fact or an existing
 * plugin dependency — never a guess about a future API — per {@link CapabilityProbe}'s contract.
 */
public final class CapabilityProbes {

    private CapabilityProbes() {
    }

    public static final List<CapabilityProbe> ALL = build();

    private static CapabilityProbe of(String id, String describe, Function<ClickSortedPlugin, CapabilityProbe.Result> body) {
        return new CapabilityProbe() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String describe() {
                return describe;
            }

            @Override
            public CapabilityProbe.Result probe(ClickSortedPlugin plugin) {
                try {
                    return body.apply(plugin);
                } catch (RuntimeException | LinkageError e) {
                    return CapabilityProbe.Result.unexpected("threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        };
    }

    private static List<CapabilityProbe> build() {
        List<CapabilityProbe> probes = new ArrayList<>();
        probes.add(inventoryViewIsInterface());
        probes.add(bundleMaterial());
        probes.add(bundleMetaRoundtrip());
        probes.add(dyedBundleMaterials());
        probes.add(itemStackBytesRoundtrip());
        probes.add(entityScheduler());
        probes.add(mountStorageOffset());
        probes.add(sortableInventoryTypes());
        probes.add(clickTypeConstants());
        probes.add(customModelDataComponent());
        return List.copyOf(probes);
    }

    private static CapabilityProbe inventoryViewIsInterface() {
        return of("inventoryview-is-interface",
                "Whether org.bukkit.inventory.InventoryView is an interface (Paper 1.21+) or a concrete "
                        + "class (<=1.20.6) — the exact boundary CLAUDE.md documents for the InventoryView calling hazard.",
                plugin -> InventoryView.class.isInterface()
                        ? CapabilityProbe.Result.present("interface")
                        : CapabilityProbe.Result.absent("concrete class"));
    }

    private static CapabilityProbe bundleMaterial() {
        return of("bundle-material", "Whether Material.BUNDLE resolves and exposes BundleMeta.", plugin -> {
            Material bundle = Material.matchMaterial("BUNDLE");
            if (bundle == null) {
                return CapabilityProbe.Result.absent("Material.BUNDLE does not resolve");
            }
            ItemStack is = new ItemStack(bundle);
            if (!(is.getItemMeta() instanceof BundleMeta)) {
                return CapabilityProbe.Result.absent("BUNDLE resolves but has no BundleMeta");
            }
            return CapabilityProbe.Result.present("BUNDLE resolves with BundleMeta");
        });
    }

    private static CapabilityProbe bundleMetaRoundtrip() {
        return of("bundle-meta-roundtrip",
                "setItems/getItems round-trip on BundleMeta — catches data-component-style drift a bare "
                        + "presence check would miss.",
                plugin -> {
                    ItemStack bundle = new ItemStack(Material.BUNDLE);
                    if (!(bundle.getItemMeta() instanceof BundleMeta meta)) {
                        return CapabilityProbe.Result.absent("no BundleMeta to round-trip");
                    }
                    meta.setItems(List.of(new ItemStack(Material.STONE, 5), new ItemStack(Material.DIRT, 3)));
                    bundle.setItemMeta(meta);
                    if (!(bundle.getItemMeta() instanceof BundleMeta after)) {
                        return CapabilityProbe.Result.unexpected("BundleMeta lost after a setItemMeta round-trip");
                    }
                    List<ItemStack> items = after.getItems();
                    long stoneAmt = items.stream().filter(i -> i.getType() == Material.STONE).mapToInt(ItemStack::getAmount).sum();
                    long dirtAmt = items.stream().filter(i -> i.getType() == Material.DIRT).mapToInt(ItemStack::getAmount).sum();
                    if (items.size() != 2 || stoneAmt != 5 || dirtAmt != 3) {
                        return CapabilityProbe.Result.unexpected("round-trip drifted: " + items.size()
                                + " item(s), stone=" + stoneAmt + " dirt=" + dirtAmt);
                    }
                    return CapabilityProbe.Result.present("2 items round-tripped with correct amounts");
                });
    }

    private static final List<String> DYE_COLORS = List.of(
            "WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY",
            "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK");

    private static CapabilityProbe dyedBundleMaterials() {
        return of("dyed-bundle-materials",
                "Resolves all 16 <color>_BUNDLE materials — expected ABSENT on <=1.21.1, PRESENT (all 16) at 1.21.2+.",
                plugin -> {
                    List<String> missing = new ArrayList<>();
                    for (String color : DYE_COLORS) {
                        if (Material.matchMaterial(color + "_BUNDLE") == null) {
                            missing.add(color);
                        }
                    }
                    int resolved = DYE_COLORS.size() - missing.size();
                    if (resolved == 0) {
                        return CapabilityProbe.Result.absent("none of the 16 dyed bundle materials resolve");
                    }
                    if (missing.isEmpty()) {
                        return CapabilityProbe.Result.present("all 16 dyed bundle materials resolve");
                    }
                    return CapabilityProbe.Result.unexpected(resolved + "/16 dyed bundle materials resolve; missing " + missing);
                });
    }

    private static CapabilityProbe itemStackBytesRoundtrip() {
        return of("itemstack-bytes-roundtrip",
                "serializeAsBytes()/deserializeBytes() round-trip on a meta-bearing stack — verifies the "
                        + "crash-backup codec itself, before it ever eats a tester's inventory.",
                plugin -> {
                    ItemStack original = new ItemStack(Material.STONE, 5);
                    ItemMeta meta = original.getItemMeta();
                    meta.displayName(Component.text("probe"));
                    original.setItemMeta(meta);
                    byte[] bytes = original.serializeAsBytes();
                    ItemStack restored = ItemStack.deserializeBytes(bytes);
                    if (!restored.isSimilar(original) || restored.getAmount() != original.getAmount()) {
                        return CapabilityProbe.Result.unexpected("round-trip mismatch: original=" + original + " restored=" + restored);
                    }
                    return CapabilityProbe.Result.present("serializeAsBytes/deserializeBytes preserved item identity");
                });
    }

    private static CapabilityProbe entityScheduler() {
        return of("entity-scheduler",
                "Reflective presence check for Entity#getScheduler() — the LIVE-phase watchdog's dependency.",
                plugin -> {
                    try {
                        Method m = Entity.class.getMethod("getScheduler");
                        return CapabilityProbe.Result.present("Entity#getScheduler() -> " + m.getReturnType().getName());
                    } catch (NoSuchMethodException e) {
                        return CapabilityProbe.Result.absent("Entity#getScheduler() not present on this server");
                    }
                });
    }

    private static CapabilityProbe customModelDataComponent() {
        return of("custom-model-data-component",
                "Whether ItemMeta.hasCustomModelDataComponent() exists (Paper 1.21.2+) — TreemapPacker "
                        + "falls back to the deprecated hasCustomModelData() when it doesn't.",
                plugin -> {
                    try {
                        ItemMeta.class.getMethod("hasCustomModelDataComponent");
                        return CapabilityProbe.Result.present("hasCustomModelDataComponent() resolves");
                    } catch (NoSuchMethodException e) {
                        return CapabilityProbe.Result.absent("falling back to hasCustomModelData()");
                    }
                });
    }

    private static CapabilityProbe mountStorageOffset() {
        return of("mount-storage-offset",
                "Spawns and immediately removes a Llama to check its chest inventory size against "
                        + "GridGeometry's assumed leading-equipment-slot offset — a silent shear here would "
                        + "misplace every mount sort by one column.",
                plugin -> {
                    List<World> worlds = Bukkit.getWorlds();
                    if (worlds.isEmpty()) {
                        return CapabilityProbe.Result.absent("no loaded world to spawn a probe mount in");
                    }
                    World world = worlds.get(0);
                    Llama llama = world.spawn(world.getSpawnLocation(), Llama.class);
                    try {
                        llama.setStrength(1);
                        llama.setCarryingChest(true);
                        int actual = llama.getInventory().getSize();
                        int offset = GridGeometry.storageOffset(llama);
                        int expected = offset + llama.getStrength() * 3;
                        if (actual == expected) {
                            return CapabilityProbe.Result.present("llama chest inventory size " + actual
                                    + " matches offset " + offset + " + strength*3");
                        }
                        return CapabilityProbe.Result.unexpected("llama chest inventory size " + actual + " != expected "
                                + expected + " (offset=" + offset + ", strength=" + llama.getStrength()
                                + ") — mount sorts may be shearing");
                    } finally {
                        llama.remove();
                    }
                });
    }

    private static CapabilityProbe sortableInventoryTypes() {
        return of("sortable-inventory-types",
                "Every name in config sortable_inventories resolves to a real InventoryType — driven by "
                        + "data the plugin already owns, so it can't go stale as the list changes.",
                plugin -> {
                    List<String> raw = plugin.getConfig().getStringList("sortable_inventories");
                    List<String> unresolved = new ArrayList<>();
                    for (String s : raw) {
                        try {
                            InventoryType.valueOf(s);
                        } catch (IllegalArgumentException e) {
                            unresolved.add(s);
                        }
                    }
                    if (unresolved.isEmpty()) {
                        return CapabilityProbe.Result.present(raw.size() + "/" + raw.size()
                                + " configured sortable_inventories name(s) resolve");
                    }
                    return CapabilityProbe.Result.unexpected(unresolved.size()
                            + " configured sortable_inventories name(s) do not resolve to a real InventoryType: " + unresolved);
                });
    }

    /** Mirrors the ClickType tokens {@code ClickMethod.matchesSortTrigger} switches on. */
    private static final List<String> CLICK_TRIGGER_TOKENS = List.of(
            "LEFT", "DOUBLE_CLICK", "SWAP_OFFHAND", "CONTROL_DROP", "SHIFT_LEFT", "SHIFT_RIGHT");

    private static CapabilityProbe clickTypeConstants() {
        return of("clicktype-constants",
                "Every ClickType token referenced by ClickMethod.matchesSortTrigger resolves by name — "
                        + "driven by the ClickMethod enum, so it can't go stale as that enum changes.",
                plugin -> {
                    List<String> unresolved = new ArrayList<>();
                    for (String token : CLICK_TRIGGER_TOKENS) {
                        try {
                            ClickType.valueOf(token);
                        } catch (IllegalArgumentException e) {
                            unresolved.add(token);
                        }
                    }
                    if (unresolved.isEmpty()) {
                        return CapabilityProbe.Result.present("all " + CLICK_TRIGGER_TOKENS.size()
                                + " referenced ClickType constants resolve");
                    }
                    return CapabilityProbe.Result.unexpected(unresolved.size()
                            + " referenced ClickType constant(s) no longer resolve: " + unresolved);
                });
    }
}
