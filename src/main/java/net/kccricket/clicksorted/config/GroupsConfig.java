package net.kccricket.clicksorted.config;

import net.kccricket.clicksorted.logging.Log;
import net.kccricket.clicksorted.model.SortingMethod;
import org.bukkit.Material;
import org.bukkit.configuration.Configuration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages {@code groups.yml}: material-to-group mappings used by
 * {@link SortingMethod#GROUP}.
 * <p>
 * Every {@link #load()} passes through {@link ResourceUpdater#update} so the file is
 * recreated from bundled defaults when absent — including after a mid-session delete
 * followed by {@code /clicksorted reload}.
 */
public class GroupsConfig implements ManagedConfig {

    private static final String FILE_NAME = "groups.yml";

    private final Plugin plugin;
    // Swapped atomically on reload: readers (getGroup/isAvailable) run on Folia region threads
    // while load() runs on another thread, so we publish a fully-built map rather than mutating
    // one in place.
    private volatile Map<String, String> mapping = Map.of();

    public GroupsConfig(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String fileName() {
        return FILE_NAME;
    }

    @Override
    public void load() {
        Configuration cfg = ResourceUpdater.update(plugin, FILE_NAME);

        Map<String, String> next = new HashMap<>();
        for (String grpName : cfg.getKeys(false)) {
            for (String matName : cfg.getStringList(grpName)) {
                try {
                    addMapping(next, matName, grpName);
                } catch (IllegalArgumentException e) {
                    Log.warning("Unknown material name '" + matName + "' in group '" + grpName + "'");
                }
            }
        }
        mapping = next;
    }

    private static void addMapping(Map<String, String> target, String matName, String grpName) {
        Material material = Material.matchMaterial(matName);
        if (material == null) {
            throw new IllegalArgumentException();
        }
        String key = material.toString();
        target.put(key, grpName);
        Log.trace("addMapping: " + key + " = " + grpName);
    }

    public String getGroup(ItemStack stack) {
        String group = mapping.get(stack.getType().toString());
        if (group == null) {
            group = plugin.getConfig().getString("default_group_name", "000-default");
        }
        Log.trace("getGroup: " + stack + " = " + group);
        return group;
    }

    public boolean isAvailable() {
        return !mapping.isEmpty();
    }
}
