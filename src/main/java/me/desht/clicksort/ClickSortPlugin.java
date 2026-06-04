package me.desht.clicksort;

/*
 * This file is part of ClickSort
 *
 * ClickSort is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * ClickSort is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with ClickSort. If not, see <http://www.gnu.org/licenses/>.
 */

import me.desht.clicksort.commands.ClickSortCommands;
import me.desht.clicksort.config.ConfigManager;
import me.desht.clicksort.events.InventorySortEvent;
import me.desht.dhutils.*;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class ClickSortPlugin extends JavaPlugin implements Listener {
    private final CooldownMessager messager = new CooldownMessager();
    private Metrics metrics;
    private PlayerSortingPrefs sortingPrefs;
    private ConfigManager configManager;

    private static ClickSortPlugin instance = null;

    @Override
    public void onEnable() {
        instance = this;

        LogUtils.init(this);
        Debugger.getInstance().setPrefix("[ClickSort] ");
        Debugger.getInstance().setTarget(getServer().getConsoleSender());

        configManager = new ConfigManager(this);
        configManager.loadAll();

        if (getConfig().getBoolean("enable_metrics", true)) {
            metrics = new Metrics(this, 9432);
        }

        PluginManager pm = this.getServer().getPluginManager();
        pm.registerEvents(this, this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(ClickSortCommands.build(this), "Manage the ClickSort plugin"));

        sortingPrefs = new PlayerSortingPrefs(this);
    }

    @Override
    public void onDisable() {
        if (metrics != null) {
            metrics.shutdown();
        }
        if (configManager != null) {
            configManager.saveAll();
        }
        instance = null;
    }

    public static ClickSortPlugin getInstance() {
        return instance;
    }

    public CooldownMessager getMessager() {
        return messager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    /** @return the sorting prefs for all players */
    public PlayerSortingPrefs getSortingPrefs() {
        return sortingPrefs;
    }

    public SortingMethod getDefaultSortingMethod() {
        return configManager.main().getDefaultSortingMethod();
    }

    public ClickMethod getDefaultClickMethod() {
        return configManager.main().getDefaultClickMethod();
    }

    public boolean getDefaultShiftClick() {
        return configManager.main().getDefaultShiftClick();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInventoryClicked(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getCurrentItem() == null) {
            return;
        }
        if (!PermissionUtils.isAllowedTo(player, "clicksort.sort")) {
            return;
        }

        String playerName = player.getName();

        Debugger.getInstance().debug("inventory click by player " + playerName + ": type=" + event.getClick() + " slot="
                + event.getSlot() + " rawslot=" + event.getRawSlot());

        SortingMethod sortMethod = sortingPrefs.getSortingMethod(player);
        ClickMethod clickMethod = sortingPrefs.getClickMethod(player);
        boolean allowShiftClick = sortingPrefs.getShiftClickAllowed(player);

        if (event.getCurrentItem().getType() == Material.AIR && event.isShiftClick() && allowShiftClick) {
            if (event.isLeftClick() && clickMethod != ClickMethod.NONE) {
                // shift-left-clicking an empty slot cycles sort method for the player
                do {
                    sortMethod = sortMethod.cycle();
                } while (!sortMethod.isAvailable());
                sortingPrefs.setSortingMethod(player, sortMethod);
                MiscUtil.statusMessage(player,
                        configManager.lang().getColoredMessage("sortBy",
                                Placeholder.unparsed("method", sortMethod.toString()),
                                Placeholder.unparsed("instruction", clickMethod.getInstruction())));
                messager.message(player, "leftclick", 60,
                        configManager.lang().getColoredMessage("shiftLeftToChange")
                                .colorIfAbsent(NamedTextColor.GRAY)
                                .decorate(TextDecoration.ITALIC));
            } else if (event.isRightClick()) {
                // shift-right-clicking an empty slot cycles click method for the player
                clickMethod = clickMethod.cycle();
                sortingPrefs.setClickMethod(player, clickMethod);
                MiscUtil.statusMessage(player, clickMethod.getInstruction());
                messager.message(player, "rightclick", 60,
                        configManager.lang().getColoredMessage("shiftRightToChange")
                                .colorIfAbsent(NamedTextColor.GRAY)
                                .decorate(TextDecoration.ITALIC));
            }
            return;
        }

        boolean shouldSort = switch (clickMethod) {
            case SINGLE -> event.getClick() == ClickType.LEFT && event.getCurrentItem().getType() == Material.AIR && (
                    event.getCursor() == null || event.getCursor().getType() == Material.AIR);
            case DOUBLE -> event.getClick() == ClickType.DOUBLE_CLICK;
            case SWAP -> event.getClick() == ClickType.SWAP_OFFHAND;
            default -> false;
        };
        if (shouldSort && shouldSort(viewToClickedInventory(event.getView(), event.getRawSlot()))) {
            if (sortInventory(event, sortMethod) && clickMethod.shouldCancelEvent()) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    player.getInventory().setItemInOffHand(player.getInventory().getItemInOffHand());
                }, 1L);
                event.setCancelled(true);
            }
        }
    }

    private Inventory viewToClickedInventory(InventoryView view, int rawSlot) {
        return rawSlot < 0 ? null
                : (rawSlot < view.getTopInventory().getSize() ? view.getTopInventory() : view.getBottomInventory());
    }

    private boolean shouldSort(Inventory clickedInventory) {
        return clickedInventory != null && !shouldIgnore(clickedInventory)
                && configManager.main().getSortableInventories().contains(clickedInventory.getType());
    }

    private boolean shouldIgnore(Inventory inventory) {
        return getConfig().getBoolean("ignore_plugin_inventory") && !isVanillaInventoryHolder(inventory.getHolder());
    }

    private static boolean isVanillaInventoryHolder(InventoryHolder inventoryHolder) {
        return inventoryHolder != null && inventoryHolder.getClass().getPackageName().startsWith("org.bukkit.");
    }

    private boolean sortInventory(final InventoryClickEvent event, final SortingMethod sortMethod) {
        Player p = (Player) event.getWhoClicked();
        int rawSlot = event.getRawSlot();
        int slot = event.getView().convertSlot(rawSlot);

        Inventory inv;
        if (slot == rawSlot) {
            // upper inv was clicked
            inv = event.getView().getTopInventory();
            if (slot >= inv.getSize()) {
                // is this a Bukkit bug? clicking a player inventory when the
                // crafting or dispenser view is up
                // seems to give rawSlot==localSlot, implying the upper
                // inventory (crafting/dispenser) has been clicked
                // when in fact the lower inventory (player) was clicked
                inv = event.getView().getBottomInventory();
            }
        } else {
            // lower inv was clicked
            inv = event.getView().getBottomInventory();
        }

        Debugger.getInstance().debug("clicked inventory window " + inv.getType() + ", slot " + slot);
        int min, max; // slot range to sort
        InventoryType type = inv.getType();
        if (type == InventoryType.PLAYER) {
            if (slot < 9) {
                // hotbar
                if (!PermissionUtils.isAllowedTo(p, "clicksort.sort.hotbar")) {
                    return false;
                }
                min = 0;
                max = 9;
            } else {
                if (!PermissionUtils.isAllowedTo(p, "clicksort.sort.player")) {
                    return false;
                }
                // main player inventory
                min = getConfig().getInt("player_sort_min");
                // don't sort equipments and off-hand
                max = getConfig().getInt("player_sort_max");
            }
        } else if (configManager.main().getSortableInventories().contains(type)) {
            if (!PermissionUtils.isAllowedTo(p, "clicksort.sort.container")) {
                return false;
            }
            min = inv.getHolder() instanceof AbstractHorse ? 2 : 0;
            max = inv.getSize();
        } else {
            return false;
        }

        InventorySortEvent sortEvent = new InventorySortEvent(event.getView(), inv, min, max);
        Bukkit.getPluginManager().callEvent(sortEvent);
        if (sortEvent.isCancelled()) {
            return false;
        }

        Set<Integer> sortableSlots = sortEvent.getSortableSlots();
        List<ItemStack> sortedItems = sortAndMerge(inv.getContents(), sortableSlots, sortMethod);

        if (sortableSlots.size() < sortedItems.size() && !getConfig().getBoolean("drop_excess")) {
            MiscUtil.errorMessage(p, configManager.lang().getColoredMessage("invOverFlow"));
            return false;
        }

        for (int i : sortableSlots) {
            if (!sortedItems.isEmpty()) {
                ItemStack newItem = sortedItems.remove(0);
                inv.setItem(i, newItem);
            } else {
                inv.clear(i);
            }
        }

        if (!sortedItems.isEmpty()) {
            // This *shouldn't* happen, but there is a possibility if some other plugin has been messing
            // with max stack sizes, and we end up with an overflowing inventory after merging stacks.
            MiscUtil.alertMessage(p, configManager.lang().getColoredMessage("dropItems"));
            for (ItemStack item : sortedItems) {
                Debugger.getInstance().debug("dropping " + item + " by player " + p.getName());
                p.getWorld().dropItemNaturally(p.getLocation(), item);
            }
        }

        for (HumanEntity he : event.getViewers()) {
            if (he instanceof Player viewer) {
                viewer.updateInventory();
            }
        }

        return true;
    }

    private List<ItemStack> sortAndMerge(ItemStack[] items, Set<Integer> sortableSlots, SortingMethod sortMethod) {
        Map<SortKey, Integer> amounts = new HashMap<>();

        // phase 1: extract a list of unique material/data/item-meta strings and
        // use those as keys
        // into a hash which maps items to quantities
        Debugger.getInstance().debug("sortAndMerge: sortable = " + sortableSlots + ", size = " + items.length);
        for (int i : sortableSlots) {
            ItemStack is = items[i];
            if (is != null) {
                SortKey key = new SortKey(is, sortMethod);
                if (amounts.containsKey(key)) {
                    amounts.put(key, amounts.get(key) + is.getAmount());
                } else {
                    amounts.put(key, is.getAmount());
                }
            }
        }

        // Sanity check
        checkNoNulls(amounts, items);

        // phase 2: sort the extracted item keys and reconstruct the item stacks
        // from those keys
        List<ItemStack> sorted = new LinkedList<>();
        for (SortKey sortKey : MiscUtil.asSortedList(amounts.keySet())) {
            int amount = amounts.get(sortKey);
            Debugger.getInstance().debug(2, "Process item [" + sortKey + "], amount = " + amount);
            Material mat = sortKey.getMaterial();
            int maxStack = mat.getMaxStackSize();
            Debugger.getInstance().debug(2, "max stack size for " + mat + " = " + maxStack);
            if (maxStack != 0) {
                while (amount > maxStack) {
                    sorted.add(sortKey.toItemStack(maxStack));
                    amount -= maxStack;
                }
                sorted.add(sortKey.toItemStack(amount));
            }
        }

        return sorted;
    }

    private void checkNoNulls(Map<SortKey, Integer> amounts, ItemStack[] items) {
        for (SortKey key : amounts.keySet()) {
            if (key == null) {
                LogUtils.severe("Detected null sort key!  Inventory dump follows:");
                for (ItemStack item : items) {
                    LogUtils.severe(item.toString());
                }
                LogUtils.severe(
                        "Please report this, quoting all above error text, in a ticket at https://github.com/NewbieOrange/clicksort/issues/");
            }
        }
    }
}
