package hs.elementSMPRefined.util.bukkit;

import hs.elementSMPRefined.util.time.TimeUtils.Expiration;
import org.bukkit.entity.Entity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced metadata helper with caching, more data types, and improved error handling.
 * Provides a clean API for entity metadata management with automatic cleanup.
 */
public final class MetadataHelper {
    private final JavaPlugin plugin;
    private final ConcurrentHashMap<String, Long> cache = new ConcurrentHashMap<>();

    public MetadataHelper(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // Timed metadata with automatic expiration checking
    public void setTimed(Entity entity, String key, long durationMillis) {
        Expiration expiration = Expiration.fromNow(durationMillis);
        entity.setMetadata(key, new FixedMetadataValue(plugin, expiration.expiresAt()));
        cache.put(key + ":" + entity.getUniqueId(), expiration.expiresAt());
    }

    public boolean isActive(Entity entity, String key) {
        return getExpiration(entity, key)
                .map(Expiration::isActive)
                .orElse(false);
    }

    public Optional<Expiration> getExpiration(Entity entity, String key) {
        if (!entity.hasMetadata(key)) return Optional.empty();

        try {
            long expiresAt = entity.getMetadata(key).get(0).asLong();
            return Optional.of(new Expiration(expiresAt));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public boolean removeIfExpired(Entity entity, String key) {
        Optional<Expiration> exp = getExpiration(entity, key);
        if (exp.isPresent() && exp.get().isExpired()) {
            entity.removeMetadata(key, plugin);
            cache.remove(key + ":" + entity.getUniqueId());
            return true;
        }
        return false;
    }

    // UUID/Owner metadata
    public void setOwner(Entity entity, String key, UUID owner) {
        entity.setMetadata(key, new FixedMetadataValue(plugin, owner.toString()));
    }

    public Optional<UUID> getOwner(Entity entity, String key) {
        if (!entity.hasMetadata(key)) return Optional.empty();

        try {
            String uuidStr = entity.getMetadata(key).get(0).asString();
            return Optional.of(UUID.fromString(uuidStr));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public boolean hasOwner(Entity entity, String key, UUID owner) {
        return getOwner(entity, key)
                .map(o -> o.equals(owner))
                .orElse(false);
    }

    // Boolean flag metadata
    public void setFlag(Entity entity, String key, boolean value) {
        entity.setMetadata(key, new FixedMetadataValue(plugin, value));
    }

    public boolean hasFlag(Entity entity, String key) {
        if (!entity.hasMetadata(key)) return false;

        try {
            return entity.getMetadata(key).get(0).asBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean getFlag(Entity entity, String key, boolean defaultValue) {
        if (!entity.hasMetadata(key)) return defaultValue;

        try {
            return entity.getMetadata(key).get(0).asBoolean();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // String metadata
    public void setString(Entity entity, String key, String value) {
        entity.setMetadata(key, new FixedMetadataValue(plugin, value));
    }

    public Optional<String> getString(Entity entity, String key) {
        if (!entity.hasMetadata(key)) return Optional.empty();

        try {
            return Optional.of(entity.getMetadata(key).get(0).asString());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public String getString(Entity entity, String key, String defaultValue) {
        return getString(entity, key).orElse(defaultValue);
    }

    // Integer metadata
    public void setInt(Entity entity, String key, int value) {
        entity.setMetadata(key, new FixedMetadataValue(plugin, value));
    }

    public Optional<Integer> getInt(Entity entity, String key) {
        if (!entity.hasMetadata(key)) return Optional.empty();

        try {
            return Optional.of(entity.getMetadata(key).get(0).asInt());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public int getInt(Entity entity, String key, int defaultValue) {
        return getInt(entity, key).orElse(defaultValue);
    }

    // Double metadata
    public void setDouble(Entity entity, String key, double value) {
        entity.setMetadata(key, new FixedMetadataValue(plugin, value));
    }

    public Optional<Double> getDouble(Entity entity, String key) {
        if (!entity.hasMetadata(key)) return Optional.empty();

        try {
            return Optional.of(entity.getMetadata(key).get(0).asDouble());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public double getDouble(Entity entity, String key, double defaultValue) {
        return getDouble(entity, key).orElse(defaultValue);
    }

    // Long metadata
    public void setLong(Entity entity, String key, long value) {
        entity.setMetadata(key, new FixedMetadataValue(plugin, value));
    }

    public Optional<Long> getLong(Entity entity, String key) {
        if (!entity.hasMetadata(key)) return Optional.empty();

        try {
            return Optional.of(entity.getMetadata(key).get(0).asLong());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public long getLong(Entity entity, String key, long defaultValue) {
        return getLong(entity, key).orElse(defaultValue);
    }

    // Generic object metadata
    public void setObject(Entity entity, String key, Object value) {
        entity.setMetadata(key, new FixedMetadataValue(plugin, value));
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getObject(Entity entity, String key, Class<T> type) {
        if (!entity.hasMetadata(key)) return Optional.empty();

        try {
            Object value = entity.getMetadata(key).get(0).value();
            if (type.isInstance(value)) {
                return Optional.of(type.cast(value));
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // Computed/ cached metadata
    public <T> T getOrCompute(Entity entity, String key, java.util.function.Supplier<T> supplier) {
        if (entity.hasMetadata(key)) {
            try {
                @SuppressWarnings("unchecked")
                T value = (T) entity.getMetadata(key).get(0).value();
                if (value != null) return value;
            } catch (Exception e) {
                // Fall through to compute
            }
        }

        T value = supplier.get();
        entity.setMetadata(key, new FixedMetadataValue(plugin, value));
        return value;
    }

    // Metadata removal and cleanup
    public void remove(Entity entity, String key) {
        entity.removeMetadata(key, plugin);
        cache.remove(key + ":" + entity.getUniqueId());
    }

    public void removeWithPrefix(Entity entity, String prefix) {
        // Bukkit metadata doesn't have a direct way to get all keys
        // We need to iterate through the entity's metadata values
        for (MetadataValue value : entity.getMetadata(prefix)) {
            if (value.getOwningPlugin().equals(plugin)) {
                entity.removeMetadata(prefix, plugin);
                cache.remove(prefix + ":" + entity.getUniqueId());
            }
        }
    }

    public void removeAll(Entity entity) {
        // Bukkit doesn't provide a way to get all metadata keys
        // This method removes all metadata owned by this plugin
        // Note: This is a limitation of the Bukkit API
        // For now, we'll just clear the cache
        cache.keySet().removeIf(key -> key.endsWith(":" + entity.getUniqueId()));
    }

    // Utility methods
    public boolean has(Entity entity, String key) {
        if (!entity.hasMetadata(key)) return false;

        for (MetadataValue value : entity.getMetadata(key)) {
            if (value.getOwningPlugin().equals(plugin)) {
                return true;
            }
        }
        return false;
    }

    // Cache cleanup
    public void cleanupExpiredCache() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    // Batch operations
    public void setBatch(Entity entity, java.util.Map<String, Object> values) {
        values.forEach((key, value) -> {
            if (value instanceof Boolean) {
                setFlag(entity, key, (Boolean) value);
            } else if (value instanceof String) {
                setString(entity, key, (String) value);
            } else if (value instanceof Integer) {
                setInt(entity, key, (Integer) value);
            } else if (value instanceof Long) {
                setLong(entity, key, (Long) value);
            } else if (value instanceof Double) {
                setDouble(entity, key, (Double) value);
            } else {
                setObject(entity, key, value);
            }
        });
    }

    public java.util.Map<String, Object> getBatch(Entity entity, String... keys) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        for (String key : keys) {
            getString(entity, key).ifPresent(value -> result.put(key, value));
            getInt(entity, key).ifPresent(value -> result.put(key, value));
            getLong(entity, key).ifPresent(value -> result.put(key, value));
            getDouble(entity, key).ifPresent(value -> result.put(key, value));
            getFlag(entity, key, false); // Booleans handled separately
        }
        return result;
    }
}

