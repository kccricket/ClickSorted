package net.kccricket.clicksorted.migration;

import net.kccricket.clicksorted.ClickSortedPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Store-neutral handle for reading and writing a single logical preference key, so migration rules
 * can be declared once and applied to both config ({@code defaults.*}) and player PDC.
 *
 * <p>A "key" passed to any method is a <em>leaf name</em> (e.g. {@code "click_mode"}); each
 * adapter applies its own namespace mapping without any per-key table.
 */
public interface Store {
    String getString(String key);
    /** Returns the stored boolean, or {@code null} if the key is absent or not a boolean type. */
    Boolean getBoolean(String key);
    void setString(String key, String value);
    void setBoolean(String key, boolean value);
    void clear(String key);
    boolean contains(String key);

    /**
     * Wraps a {@link ConfigurationSection} so that leaf {@code k} maps to the YAML path
     * {@code defaults.k}. Booleans are stored as native YAML booleans.
     */
    final class ConfigStore implements Store {
        private final ConfigurationSection section;

        ConfigStore(ConfigurationSection section) {
            this.section = section;
        }

        private String path(String key) {
            return "defaults." + key;
        }

        @Override public String getString(String key) { return section.getString(path(key)); }
        @Override public Boolean getBoolean(String key) { Object v = section.get(path(key)); return v instanceof Boolean b ? b : null; }
        @Override public void setString(String key, String value) { section.set(path(key), value); }
        @Override public void setBoolean(String key, boolean value) { section.set(path(key), value); }
        @Override public void clear(String key) { section.set(path(key), null); }
        @Override public boolean contains(String key) { return section.contains(path(key)); }
    }

    /**
     * Wraps a {@link PersistentDataContainer} so that leaf {@code k} maps to a
     * {@link NamespacedKey} with that leaf as the key. Strings via {@code STRING}; booleans as
     * {@code BYTE} (1/0).
     */
    final class PdcStore implements Store {
        private final PersistentDataContainer pdc;
        private final ClickSortedPlugin plugin;

        PdcStore(PersistentDataContainer pdc, ClickSortedPlugin plugin) {
            this.pdc = pdc;
            this.plugin = plugin;
        }

        private NamespacedKey nsKey(String key) {
            return new NamespacedKey(plugin, key);
        }

        @Override public String getString(String key) { return pdc.get(nsKey(key), PersistentDataType.STRING); }
        @Override public Boolean getBoolean(String key) { Byte b = pdc.get(nsKey(key), PersistentDataType.BYTE); return b != null ? b != 0 : null; }
        @Override public void setString(String key, String value) { pdc.set(nsKey(key), PersistentDataType.STRING, value); }
        @Override public void setBoolean(String key, boolean value) { pdc.set(nsKey(key), PersistentDataType.BYTE, value ? (byte) 1 : (byte) 0); }
        @Override public void clear(String key) { pdc.remove(nsKey(key)); }
        @Override public boolean contains(String key) { return pdc.getKeys().contains(nsKey(key)); }
    }
}
