package hs.elementSMPRefined.data;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * YAML-backed {@link PlayerDataRepository}. Player data lives in
 * {@code data/players.yml} under the plugin's data folder, with an
 * in-memory cache in front of it so hot paths (ability checks, mana
 * ticks) don't hit disk.
 * <p>
 * All disk-touching methods are synchronized: the YAML config object is
 * shared mutable state, and both the main thread ({@link #save}) and the
 * async flush path ({@link #saveAsync}) can write it. Synchronizing on
 * {@code this} keeps a read-merge-write cycle from one caller from being
 * torn by another.
 * <p>
 * IMPORTANT: {@link #getPlayerData(UUID)} caches every UUID it has ever
 * seen and only drops an entry via {@link #invalidateCache(UUID)}. Callers
 * MUST invalidate on player quit (after the final save) or this cache grows
 * without bound for the lifetime of the server.
 */
public class DataStore implements PlayerDataRepository {

    private final JavaPlugin plugin;

    private final File playerFile;
    private FileConfiguration playerCfg;

    private final Map<UUID, PlayerData> playerDataCache = new ConcurrentHashMap<>();

    public DataStore(JavaPlugin plugin) {
        this.plugin = plugin;

        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            throw new IllegalStateException("Could not create data directory: " + dataDir.getAbsolutePath());
        }

        this.playerFile = createIfMissing(dataDir, "players.yml");
        this.playerCfg = loadYaml(playerFile, "players.yml");
    }

    private File createIfMissing(File dir, String name) {
        File file = new File(dir, name);
        if (!file.exists()) {
            try {
                if (!file.createNewFile()) {
                    throw new IllegalStateException("Failed to create " + name);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Could not create " + name, e);
            }
        }
        return file;
    }

    private FileConfiguration loadYaml(File file, String name) {
        try {
            return YamlConfiguration.loadConfiguration(file);
        } catch (Exception e) {
            throw new IllegalStateException("Could not load " + name, e);
        }
    }

    // === READ PATH ===

    @Override
    public PlayerData getPlayerData(UUID uuid) {
        PlayerData cached = playerDataCache.get(uuid);
        if (cached != null) {
            return cached;
        }

        PlayerData loaded = loadPlayerDataFromFile(uuid);
        playerDataCache.put(uuid, loaded);
        return loaded;
    }

    @Override
    public PlayerData load(UUID uuid) {
        return getPlayerData(uuid);
    }

    private synchronized PlayerData loadPlayerDataFromFile(UUID uuid) {
        try {
            playerCfg = YamlConfiguration.loadConfiguration(playerFile);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to reload players.yml, using blank data for " + uuid, e);
            return new PlayerData(uuid);
        }

        String uuidString = uuid.toString();

        // Primary format: players.<uuid>
        ConfigurationSection section = playerCfg.getConfigurationSection("players." + uuidString);

        // Legacy fallback: <uuid> at root, from before the "players." prefix was introduced
        if (section == null) {
            section = playerCfg.getConfigurationSection(uuidString);
        }

        return PlayerDataSerializer.deserialize(uuid, section);
    }

    // === WRITE PATH ===

    /**
     * Persists {@code data} synchronously on the calling thread. Intended
     * for infrequent, one-off saves (quit, admin commands, final shutdown
     * flush) where a single blocking YAML read+write is negligible. Do NOT
     * call this from a per-tick or per-player periodic loop - use
     * {@link #saveAsync(PlayerData)} instead.
     */
    @Override
    public synchronized void save(PlayerData data) {
        playerDataCache.put(data.getUuid(), data);
        persistToDisk(data);
    }

    /**
     * Refreshes the cache immediately (so subsequent reads see the change
     * right away) and defers the actual YAML read-merge-write to an async
     * task. This is the path {@code ManaManager}'s periodic dirty-flush
     * should use, since it can touch many players' data every flush
     * interval - doing that synchronously on the main thread would stall
     * the server tick as player count grows.
     */
    @Override
    public void saveAsync(PlayerData data) {
        playerDataCache.put(data.getUuid(), data);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (this) {
                persistToDisk(data);
            }
        });
    }

    /** Actual read-merge-write cycle. Caller must hold the lock on {@code this}. */
    private void persistToDisk(PlayerData data) {
        try {
            // Reload first so we merge onto whatever's currently on disk rather than
            // clobbering changes written by another save() call in between.
            playerCfg = YamlConfiguration.loadConfiguration(playerFile);

            String key = "players." + data.getUuid();
            ConfigurationSection section = playerCfg.getConfigurationSection(key);
            if (section == null) {
                section = playerCfg.createSection(key);
            }

            PlayerDataSerializer.serialize(data, section);

            flushPlayerData();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save player data for " + data.getUuid(), e);
        }
    }

    @Override
    public void invalidateCache(UUID uuid) {
        playerDataCache.remove(uuid);
    }

    /** Synchronous - only safe to call on shutdown/disable where blocking briefly is acceptable. */
    @Override
    public synchronized void flushAll() {
        flushPlayerData();
    }

    private void flushPlayerData() {
        try {
            playerCfg.save(playerFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save players.yml to disk", e);
        }
    }

    // === TRUST (delegates to PlayerData) ===

    @Override
    public Set<UUID> getTrusted(UUID owner) {
        return getPlayerData(owner).getTrustedPlayers();
    }

    @Override
    public void setTrusted(UUID owner, Set<UUID> trusted) {
        PlayerData data = getPlayerData(owner);
        data.setTrustedPlayers(trusted);
        save(data);
    }
}