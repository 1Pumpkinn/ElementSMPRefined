package hs.elementSMPRefined.util.bukkit;

import hs.elementPlugin.ElementPlugin;
import hs.elementPlugin.util.time.TimeUtils.Expiration;
import org.bukkit.entity.Entity;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.Optional;
import java.util.UUID;

public final class MetadataHelper {
    private final ElementPlugin plugin;

    public MetadataHelper(ElementPlugin plugin) {
        this.plugin = plugin;
    }

    public void setTimed(Entity entity, String key, long durationMillis) {
        Expiration expiration = Expiration.fromNow(durationMillis);
        entity.setMetadata(key, new FixedMetadataValue(plugin, expiration.expiresAt()));
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
            return true;
        }
        return false;
    }

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

    public void remove(Entity entity, String key) {
        entity.removeMetadata(key, plugin);
    }

    public void removeWithPrefix(Entity entity, String prefix) {
        entity.getMetadata(prefix).stream()
                .filter(v -> v.getOwningPlugin() == plugin)
                .forEach(v -> entity.removeMetadata(prefix, plugin));
    }
}

