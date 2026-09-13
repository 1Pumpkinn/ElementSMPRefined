package net.rose.elementSMPRefined.util.bukkit;

import org.bukkit.entity.Entity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Small typed wrapper around Bukkit entity metadata.
 * <p>
 * This used to carry ~30 methods (timed/owner/string/int/double/object
 * metadata, batching, prefix removal, a cache). Only long/boolean metadata
 * had a real caller (EarthTunnelAbility's tunnel-timer tracking), including
 * two methods (removeWithPrefix, removeAll) that didn't actually do what
 * their names claimed - Bukkit has no API for either. Trimmed to what's
 * used; add a method back with a real call site if you need it, rather than
 * restoring the whole surface "just in case".
 */
public final class MetadataHelper {
    private final JavaPlugin plugin;

    public MetadataHelper(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean hasFlag(Entity entity, String key) {
        if (!entity.hasMetadata(key)) return false;
        try {
            return entity.getMetadata(key).get(0).asBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    public void setLong(Entity entity, String key, long value) {
        entity.setMetadata(key, new FixedMetadataValue(plugin, value));
    }

    public long getLong(Entity entity, String key, long defaultValue) {
        if (!entity.hasMetadata(key)) return defaultValue;
        try {
            return entity.getMetadata(key).get(0).asLong();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public void remove(Entity entity, String key) {
        entity.removeMetadata(key, plugin);
    }
}