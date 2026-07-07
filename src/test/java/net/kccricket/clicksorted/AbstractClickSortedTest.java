package net.kccricket.clicksorted;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.scheduler.BukkitSchedulerMock;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Base class for ClickSorted integration tests. Loads the real plugin via MockBukkit so that
 * all @EventHandler methods, command dispatch, and the PDC-backed preference store are
 * exercised through the same code paths as production.
 */
abstract class AbstractClickSortedTest {

    protected ServerMock server;
    protected ClickSortedPlugin plugin;

    @BeforeEach
    void setUpServer() throws Exception {
        server = MockBukkit.mock();
        // Use the test-config.yml fixture: disables bStats.
        InputStream configStream = getClass().getClassLoader().getResourceAsStream("test-config.yml");
        plugin = MockBukkit.loadWithConfig(ClickSortedPlugin.class, configStream);
        // Install the sentinel lang fixture as an on-disk override (highest precedence in the
        // resolution order), so assertions key off stable tokens rather than production prose.
        // Any key the fixture omits still resolves from the bundled internal en_us default.
        InputStream langStream = getClass().getClassLoader().getResourceAsStream("test-lang.yml");
        if (langStream != null) {
            File langDir = new File(plugin.getDataFolder(), "lang");
            langDir.mkdirs();
            try (langStream) {
                Files.copy(langStream, new File(langDir, "en_us.yml").toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            plugin.getConfigManager().reloadAll();
        }
    }

    @AfterEach
    void tearDownServer() {
        MockBukkit.unmock();
    }

    // --- Helpers ---

    /** Adds an op player (all permissions) and returns it. */
    protected PlayerMock addOpPlayer(String name) {
        PlayerMock player = server.addPlayer(name);
        player.setOp(true);
        return player;
    }

    /**
     * Opens a chest-sized inventory for the player, pre-populated with the given items (from
     * slot 0 upward).  Returns the InventoryView so click simulation can target specific slots.
     */
    protected InventoryView openChest(PlayerMock player, ItemStack... items) {
        Inventory chest = server.createInventory(null, InventoryType.CHEST);
        for (int i = 0; i < items.length && i < chest.getSize(); i++) {
            chest.setItem(i, items[i]);
        }
        return player.openInventory(chest);
    }

    /**
     * Fires an InventoryClickEvent at the given slot in the given view.  We construct the event
     * directly (rather than using the deprecated PlayerMock helper) so we can fully control the
     * ClickType, currentItem, and cursor.
     */
    protected InventoryClickEvent fireClick(
            InventoryView view, ClickType clickType, int rawSlot) {
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, rawSlot, clickType,
                InventoryAction.UNKNOWN);
        server.getPluginManager().callEvent(event);
        return event;
    }

    /** Shorthand: fire a click on the first slot (slot 0) of the currently open view. */
    protected InventoryClickEvent fireClick(InventoryView view, ClickType clickType) {
        return fireClick(view, clickType, 0);
    }

    protected ItemStack stack(Material mat, int amount) {
        return new ItemStack(mat, amount);
    }

    protected ItemStack stack(Material mat) {
        return stack(mat, 1);
    }

    /**
     * Count the total amount of each material present in the inventory (ignoring null/AIR slots).
     */
    protected Map<Material, Integer> countByMaterial(Inventory inv) {
        Map<Material, Integer> counts = new HashMap<>();
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                counts.put(item.getType(), counts.getOrDefault(item.getType(), 0) + item.getAmount());
            }
        }
        return counts;
    }

    /**
     * Waits for any pending async tasks to finish, then performs one scheduler tick to flush
     * any sync tasks those async tasks may have queued.
     */
    protected void waitForJoinHandler() {
        ((BukkitSchedulerMock) server.getScheduler()).waitAsyncTasksFinished();
        server.getScheduler().performTicks(1);
    }

    /** Drain and discard all queued messages for the player. */
    protected void drainMessages(PlayerMock player) {
        //noinspection StatementWithEmptyBody
        while (player.nextMessage() != null) {}
    }

    /**
     * Drains all queued messages for {@code player} and returns {@code true} if at least one
     * of them contains any of the given {@code keywords}.
     */
    protected boolean anyMessageContains(PlayerMock player, String... keywords) {
        String msg;
        while ((msg = player.nextMessage()) != null) {
            for (String kw : keywords) {
                if (msg.contains(kw)) return true;
            }
        }
        return false;
    }

    /** Drains all queued messages for {@code player} into a list for multi-assertion use. */
    protected List<String> drainMessageList(PlayerMock player) {
        List<String> messages = new ArrayList<>();
        String msg;
        while ((msg = player.nextMessage()) != null) {
            messages.add(msg);
        }
        return messages;
    }

    private static final Pattern RAW_PLACEHOLDER = Pattern.compile("<[a-z_]+>");

    /**
     * Asserts that {@code messages} contains at least one entry that contains <em>all</em>
     * of {@code tokens}, and that no message in the batch has an unsubstituted lowercase
     * MiniMessage placeholder (e.g. {@code <value>}, {@code <method>}).
     */
    protected void assertMessageSent(List<String> messages, String... tokens) {
        for (String msg : messages) {
            assertFalse(RAW_PLACEHOLDER.matcher(msg).find(),
                    "Unsubstituted placeholder in message: " + msg);
        }
        outer:
        for (String msg : messages) {
            for (String token : tokens) {
                if (!msg.contains(token)) continue outer;
            }
            return;
        }
        fail("No message contained all of " + Arrays.toString(tokens) + "; messages: " + messages);
    }
}
