package me.desht.clicksort.config;

import me.desht.dhutils.LogUtils;
import org.bukkit.Material;
import org.bukkit.configuration.Configuration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import xyz.chengzi.clicksort.util.ResourceUpdater;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages {@code groups.yml}: material-to-group mappings used by
 * {@link me.desht.clicksort.SortingMethod#GROUP}.
 * <p>
 * Every {@link #load()} passes through {@link ResourceUpdater#update} so the file is
 * recreated from bundled defaults when absent — including after a mid-session delete
 * followed by {@code /clicksort reload}.
 */
public class GroupsConfig implements ManagedConfig {

    private static final String FILE_NAME = "groups.yml";

    private final Plugin plugin;
    private final Map<String, String> mapping = new HashMap<>();

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

        mapping.clear();
        for (String grpName : cfg.getKeys(false)) {
            for (String matName : cfg.getStringList(grpName)) {
                try {
                    addMapping(matName, grpName);
                } catch (IllegalArgumentException e) {
                    LogUtils.warning("Unknown material name '" + matName + "' in group '" + grpName + "'");
                }
            }
        }
    }

    private void addMapping(String matName, String grpName) {
        Material material = Material.matchMaterial(matName);
        if (material == null) {
            throw new IllegalArgumentException();
        }
        String key = material.toString();
        mapping.put(key, grpName);
        LogUtils.trace("addMapping: " + key + " = " + grpName);
    }

    public String getGroup(ItemStack stack) {
        String group = mapping.get(stack.getType().toString());
        if (group == null) {
            group = plugin.getConfig().getString("default_group_name", "000-default");
        }
        LogUtils.trace("getGroup: " + stack + " = " + group);
        return group;
    }

    public boolean isAvailable() {
        return !mapping.isEmpty();
    }
}
