package hs.elementSMPRefined.data;

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

    /**
     * Returns the data for {@code uuid}, serving from cache when possible
     * and loading from storage on a cache miss. Never returns {@code null}
     * - a fresh {@link PlayerData} is created for players seen for the
     * first time.
     */
    PlayerData getPlayerData(UUID uuid);

    /**
     * Alias for {@link #getPlayerData(UUID)} kept for call-site readability
     * (e.g. "load this player's data on join").
     */
    PlayerData load(UUID uuid);

    /**
     * Persists {@code data} to storage and refreshes the cache entry.
     * Performs the disk write synchronously on the calling thread - safe to
     * call from the main thread for infrequent, one-off saves (quit, admin
     * commands) but NOT from a per-tick or high-frequency loop.
     */
    void save(PlayerData data);

    /**
     * Refreshes the cache entry immediately, then persists {@code data} to
     * storage on an async thread. Use this from high-frequency call sites
     * (periodic flush loops) so disk I/O never blocks the main thread.
     */
    void saveAsync(PlayerData data);

    /** Drops the cached entry for {@code uuid}, forcing a reload on the next {@link #get}. */
    void invalidateCache(UUID uuid);

    /** Flushes any buffered writes to disk. */
    void flushAll();

    /** Convenience accessor mirroring {@code get(owner).getTrustedPlayers()}. */
    Set<UUID> getTrusted(UUID owner);

    /** Convenience mutator that replaces an owner's trust list and saves. */
    void setTrusted(UUID owner, Set<UUID> trusted);
}