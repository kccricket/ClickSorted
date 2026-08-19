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
import net.kccricket.clicksorted.model.FillAxis;
import net.kccricket.clicksorted.model.PlayerSortingPrefs;
import net.kccricket.clicksorted.model.SortingMethod;
import net.kccricket.clicksorted.model.StartCorner;
import net.kccricket.kcmclib.logging.Log;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.permissions.PermissionAttachment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Per-player self-test run state: a snapshot of everything the run touches, a crash-safety backup
 * written to disk immediately (so a server that dies mid-test doesn't lose the tester's real
 * inventory or preferences), and the restore path. Modeled on the equivalent mechanism in
 * Spigot-BestTools's self-test.
 */
final class SelfTestSession {

    enum Phase {SIM, LIVE, DONE}

    private final ClickSortedPlugin plugin;
    private final Player player;
    private final File backupFile;

    private final ItemStack[] savedContents;
    private final ItemStack[] savedArmor;
    private final ItemStack savedOffHand;
    private final ItemStack savedCursor;
    private final int savedHeldSlot;
    private final GameMode savedGameMode;

    private final boolean savedEnabled;
    private final ClickMethod savedClickMethod;
    private final SortingMethod savedSortingMethod;
    private final StartCorner savedStartCorner;
    private final FillAxis savedFillAxis;
    private final boolean savedSortOverItems;
    private final boolean savedBundleInInventory;
    private final boolean savedBundleInContainers;
    private final int savedBundleStackLimit;
    private final Set<Integer> savedLockedSlots;

    final SelfTestReport report = new SelfTestReport();
    volatile Phase phase = Phase.SIM;
    volatile int liveIndex = 0;
    volatile Inventory testChest;

    /** LIVE-phase cycle catalog for this run (quick sweep or full); empty for a SIM-only session. */
    volatile List<LiveCycle> cycles = List.of();
    /** Version fingerprint captured before this session's LIVE phase started; used by {@link LiveCycle#skipReason}. */
    volatile CompatibilityReport probes;
    /** A temporary permission grant a running {@link LiveCycle} may need (e.g. {@code live-blacklist}); always released in {@link #restoreAndClear()}. */
    volatile PermissionAttachment liveAttachment;
    /** Repeating task refreshing the current cycle's action-bar instruction; started once in {@code startLive}, always cancelled in {@link #restoreAndClear()}. */
    volatile ScheduledTask actionBarTask;

    SelfTestSession(ClickSortedPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;

        PlayerInventory inv = player.getInventory();
        this.savedContents = inv.getContents().clone();
        this.savedArmor = inv.getArmorContents().clone();
        this.savedOffHand = inv.getItemInOffHand().clone();
        this.savedCursor = player.getItemOnCursor().clone();
        this.savedHeldSlot = inv.getHeldItemSlot();
        this.savedGameMode = player.getGameMode();

        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        this.savedEnabled = prefs.getEnabled(player);
        this.savedClickMethod = prefs.getClickMethod(player);
        this.savedSortingMethod = prefs.getSortingMethod(player);
        this.savedStartCorner = prefs.getStartCorner(player);
        this.savedFillAxis = prefs.getFillAxis(player);
        this.savedSortOverItems = prefs.getSortOverItems(player);
        this.savedBundleInInventory = prefs.getBundlePackInInventory(player);
        this.savedBundleInContainers = prefs.getBundlePackInContainers(player);
        this.savedBundleStackLimit = prefs.getBundleStackLimit(player);
        this.savedLockedSlots = prefs.getLockedSlots(player);

        this.backupFile = new File(plugin.getDataFolder(), "selftest-backup-" + player.getUniqueId() + ".yml");
        writeBackupFile();
    }

    Player player() {
        return player;
    }

    LiveCycle currentLiveCycle() {
        return liveIndex < cycles.size() ? cycles.get(liveIndex) : null;
    }

    boolean liveFinished() {
        return liveIndex >= cycles.size();
    }

    /** Forces deterministic test conditions, overriding whatever the player had configured. */
    void applyTestSettings() {
        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        prefs.setEnabled(player, true);
        prefs.setSortingMethod(player, SortingMethod.NAME);
        prefs.setStartCorner(player, StartCorner.TOP_LEFT);
        prefs.setFillAxis(player, FillAxis.HORIZONTAL);
        prefs.setSortOverItems(player, true);
        prefs.setBundlePackInInventory(player, false);
        prefs.setBundlePackInContainers(player, false);
        prefs.setLockedSlots(player, Set.of());
    }

    /**
     * Restores everything to the pre-test snapshot, releases any LIVE-cycle permission grant, and
     * deletes the crash backup — but only once every restore step below has actually run. Each step
     * is independently guarded via {@link #attempt} so one throwing (e.g. a version-incompatible
     * API call — exactly the failure class this subsystem exists to probe for, already handled the
     * same way elsewhere in {@code SelfTestManager}/{@code SelfTestRunner}) doesn't skip the rest;
     * the backup is deliberately kept (not deleted) if any step failed, so
     * {@link #restoreOrphanedBackup} can still recover the tester's real state on next join.
     */
    void restoreAndClear() {
        boolean ok = true;
        // Unconditional, not just when testChest is set: SIM cases open their own Inventory
        // locally (never registered on the session), so gating this on testChest left the
        // client's GUI from the last SIM case open after the run finished.
        ok &= attempt("close test chest", player::closeInventory);
        testChest = null;
        PlayerInventory inv = player.getInventory();
        ok &= attempt("restore inventory contents", () -> inv.setContents(savedContents));
        ok &= attempt("restore armor", () -> inv.setArmorContents(savedArmor));
        ok &= attempt("restore offhand", () -> inv.setItemInOffHand(savedOffHand));
        ok &= attempt("restore cursor", () -> player.setItemOnCursor(savedCursor));
        ok &= attempt("restore held slot", () -> inv.setHeldItemSlot(savedHeldSlot));
        ok &= attempt("restore game mode", () -> player.setGameMode(savedGameMode));

        PlayerSortingPrefs prefs = plugin.getSortingPrefs();
        ok &= attempt("restore enabled", () -> prefs.setEnabled(player, savedEnabled));
        ok &= attempt("restore click method", () -> prefs.setClickMethod(player, savedClickMethod));
        ok &= attempt("restore sorting method", () -> prefs.setSortingMethod(player, savedSortingMethod));
        ok &= attempt("restore start corner", () -> prefs.setStartCorner(player, savedStartCorner));
        ok &= attempt("restore fill axis", () -> prefs.setFillAxis(player, savedFillAxis));
        ok &= attempt("restore sort-over-items", () -> prefs.setSortOverItems(player, savedSortOverItems));
        ok &= attempt("restore bundle-in-inventory", () -> prefs.setBundlePackInInventory(player, savedBundleInInventory));
        ok &= attempt("restore bundle-in-containers", () -> prefs.setBundlePackInContainers(player, savedBundleInContainers));
        ok &= attempt("restore bundle stack limit", () -> prefs.setBundleStackLimit(player, savedBundleStackLimit));
        // bulk setter — no event, always applies
        ok &= attempt("restore locked slots", () -> prefs.setLockedSlots(player, savedLockedSlots));

        if (liveAttachment != null) {
            player.removeAttachment(liveAttachment);
            liveAttachment = null;
        }
        if (actionBarTask != null) {
            actionBarTask.cancel();
            actionBarTask = null;
        }

        if (ok) {
            if (!backupFile.delete() && backupFile.exists()) {
                Log.warning("Could not delete self-test backup file " + backupFile + " — remove it by hand.");
            }
        } else {
            Log.severe("Self-test restore for " + player.getName() + " did not fully complete — keeping "
                    + backupFile + " for recovery on next join; see the errors above for which step(s) failed.");
        }
    }

    /**
     * Runs {@code step}, logging and returning {@code false} instead of propagating on failure — so
     * one failing restore step never prevents the rest of {@link #restoreAndClear} from running.
     */
    private boolean attempt(String description, Runnable step) {
        try {
            step.run();
            return true;
        } catch (RuntimeException | LinkageError e) {
            Log.severe("Self-test restore step '" + description + "' failed for " + player.getName()
                    + ": " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Best-effort crash-safety net: persists the snapshot to disk so a server that dies mid-test
     * doesn't just lose the tester's real inventory and preferences. Deleted the moment the session
     * ends normally via {@link #restoreAndClear()}; recovered on the player's next join if not.
     */
    private void writeBackupFile() {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("heldSlot", savedHeldSlot);
            yaml.set("gameMode", savedGameMode.name());
            yaml.set("enabled", savedEnabled);
            yaml.set("clickMethod", savedClickMethod.name());
            yaml.set("sortingMethod", savedSortingMethod.name());
            yaml.set("startCorner", savedStartCorner.name());
            yaml.set("fillAxis", savedFillAxis.name());
            yaml.set("sortOverItems", savedSortOverItems);
            yaml.set("bundleInInventory", savedBundleInInventory);
            yaml.set("bundleInContainers", savedBundleInContainers);
            yaml.set("bundleStackLimit", savedBundleStackLimit);
            yaml.set("lockedSlots", savedLockedSlots.stream().sorted().toList());

            yaml.set("items.contents", encodeItems(savedContents));
            yaml.set("items.armor", encodeItems(savedArmor));
            yaml.set("items.offhand", encodeItems(new ItemStack[]{savedOffHand}));
            yaml.set("items.cursor", encodeItems(new ItemStack[]{savedCursor}));

            yaml.save(backupFile);
        } catch (IOException e) {
            Log.warning("Could not write self-test backup file for " + player.getName()
                    + " — if the server stops before the test ends, restore their inventory/preferences manually.", e);
        }
    }

    /**
     * Restores an orphaned backup left by an interrupted run (server stop, crash), if one exists
     * for {@code player}. Called from {@link SelfTestListener#onJoin}.
     *
     * @return {@code true} if a backup was found and restored (or at least attempted)
     */
    static boolean restoreOrphanedBackup(ClickSortedPlugin plugin, Player player) {
        File file = new File(plugin.getDataFolder(), "selftest-backup-" + player.getUniqueId() + ".yml");
        if (!file.isFile()) {
            return false;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            PlayerInventory inv = player.getInventory();

            restoreArray(player, "main inventory contents", decodeItems(yaml.getStringList("items.contents")),
                    inv.getContents().length, inv::setContents);
            restoreArray(player, "armor contents", decodeItems(yaml.getStringList("items.armor")),
                    inv.getArmorContents().length, inv::setArmorContents);

            ItemStack[] offhand = decodeItems(yaml.getStringList("items.offhand"));
            if (offhand.length == 1) {
                inv.setItemInOffHand(offhand[0] == null ? new ItemStack(Material.AIR) : offhand[0]);
            }
            ItemStack[] cursor = decodeItems(yaml.getStringList("items.cursor"));
            if (cursor.length == 1) {
                player.setItemOnCursor(cursor[0] == null ? new ItemStack(Material.AIR) : cursor[0]);
            }

            inv.setHeldItemSlot(yaml.getInt("heldSlot", 0));
            try {
                player.setGameMode(GameMode.valueOf(yaml.getString("gameMode", "SURVIVAL")));
            } catch (IllegalArgumentException ignored) {
                // unknown gamemode token — leave the player's current gamemode as-is
            }

            PlayerSortingPrefs prefs = plugin.getSortingPrefs();
            prefs.setEnabled(player, yaml.getBoolean("enabled", prefs.getEnabled(player)));
            prefs.setSortOverItems(player, yaml.getBoolean("sortOverItems", prefs.getSortOverItems(player)));
            prefs.setBundlePackInInventory(player, yaml.getBoolean("bundleInInventory", prefs.getBundlePackInInventory(player)));
            prefs.setBundlePackInContainers(player, yaml.getBoolean("bundleInContainers", prefs.getBundlePackInContainers(player)));
            prefs.setBundleStackLimit(player, yaml.getInt("bundleStackLimit", prefs.getBundleStackLimit(player)));
            prefs.setClickMethod(player, parseOr(ClickMethod.class, yaml.getString("clickMethod"), prefs.getClickMethod(player)));
            prefs.setSortingMethod(player, parseOr(SortingMethod.class, yaml.getString("sortingMethod"), prefs.getSortingMethod(player)));
            prefs.setStartCorner(player, parseOr(StartCorner.class, yaml.getString("startCorner"), prefs.getStartCorner(player)));
            prefs.setFillAxis(player, parseOr(FillAxis.class, yaml.getString("fillAxis"), prefs.getFillAxis(player)));
            prefs.setLockedSlots(player, Set.copyOf(yaml.getIntegerList("lockedSlots")));

            Log.warning("Restored " + player.getName() + "'s pre-self-test inventory and preferences "
                    + "from an interrupted run.");
            return true;
        } catch (Exception e) {
            Log.warning("Found a self-test backup for " + player.getName() + " but failed to restore it: "
                    + file + " — check it by hand.", e);
            return false;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    /**
     * Applies a decoded item array to the live inventory via {@code setter}, unless its length
     * doesn't match what this server's equivalent array actually holds — a length mismatch means the
     * backup was written by a different server version, and forcing it in with index arithmetic
     * (the previous behavior) is itself a version-drift hazard; skip that field instead of corrupting it.
     */
    private static void restoreArray(Player player, String label, ItemStack[] decoded, int expectedSize,
                                      Consumer<ItemStack[]> setter) {
        if (decoded.length != expectedSize) {
            Log.warning("Self-test backup for " + player.getName() + " has a " + label + " array of length "
                    + decoded.length + " but this server expects " + expectedSize
                    + " — skipping restore of that field (likely a version mismatch between the interrupted run and this restart).");
            return;
        }
        setter.accept(decoded);
    }

    /** Encodes each item independently via {@link ItemStack#serializeAsBytes()} (present well before this plugin's floor), never the 1.21.1+ batch overloads. Empty string is the null/AIR sentinel. */
    static List<String> encodeItems(ItemStack[] items) {
        List<String> out = new ArrayList<>(items.length);
        for (ItemStack is : items) {
            if (is == null || is.getType() == Material.AIR) {
                out.add("");
            } else {
                out.add(Base64.getEncoder().encodeToString(is.serializeAsBytes()));
            }
        }
        return out;
    }

    /** Inverse of {@link #encodeItems}. A corrupt individual blob nulls that slot and logs, rather than aborting the whole restore. */
    static ItemStack[] decodeItems(List<String> blobs) {
        ItemStack[] out = new ItemStack[blobs.size()];
        for (int i = 0; i < blobs.size(); i++) {
            String blob = blobs.get(i);
            if (blob == null || blob.isEmpty()) {
                continue;
            }
            try {
                out[i] = ItemStack.deserializeBytes(Base64.getDecoder().decode(blob));
            } catch (RuntimeException e) {
                Log.warning("Could not decode a self-test backup item at index " + i + " — leaving that slot empty.", e);
            }
        }
        return out;
    }

    private static <E extends Enum<E>> E parseOr(Class<E> type, String token, E fallback) {
        if (token == null) return fallback;
        try {
            return Enum.valueOf(type, token);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
