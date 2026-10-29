package net.rose.elementSMPRefined.data;

import java.util.Set;
import java.util.UUID;

/**
 * Contract for loading, caching, and persisting {@link PlayerData}.
 * <p>
 * Implementations own the storage backend (YAML, SQL, Redis, etc.) and the
 * caching strategy. Code that only needs to read/write player data should
 * depend on this interface rather than a concrete implementation - it keeps
 * managers/listeners testable (mock the interface) and makes swapping the
 * storage backend later a one-class change instead of a codebase-wide one.
 */
public interface PlayerDataRepository {

    PlayerData getPlayerData(UUID uuid);

    PlayerData load(UUID uuid);

    void save(PlayerData data);

    void saveAsync(PlayerData data);

    void invalidateCache(UUID uuid);

    void flushAll();

    Set<UUID> getTrusted(UUID owner);

    void setTrusted(UUID owner, Set<UUID> trusted);
}