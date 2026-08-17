package hs.elementSMPRefined.data;

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
 * All public methods are synchronized: the YAML config object is shared
 * mutable state, and the plugin can call into this class from async
 * tasks (e.g. join/quit handling) as well as the main thread.
 */
public class DataStore implements PlayerDataRepository {

    private final JavaPlugin plugin;

    private final File playerFile;
    private FileConfiguration playerCfg;
    private final File serverFile;
    private FileConfiguration serverCfg;

    private final Map<UUID, PlayerData> playerDataCache = new ConcurrentHashMap<>();

    public DataStore(JavaPlugin plugin) {
        this.plugin = plugin;

        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            throw new IllegalStateException("Could not create data directory: " + dataDir.getAbsolutePath());
        }

        this.playerFile = createIfMissing(dataDir, "players.yml");
        this.playerCfg = loadYaml(playerFile, "players.yml");

        this.serverFile = createIfMissing(dataDir, "server.yml");
        this.serverCfg = loadYaml(serverFile, "server.yml");
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
    public synchronized PlayerData getPlayerData(UUID uuid) {
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

    private PlayerData loadPlayerDataFromFile(UUID uuid) {
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

    @Override
    public synchronized void save(PlayerData data) {
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

            playerDataCache.put(data.getUuid(), data);
            flushPlayerData();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save player data for " + data.getUuid(), e);
        }
    }

    @Override
    public synchronized void invalidateCache(UUID uuid) {
        playerDataCache.remove(uuid);
    }

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
    public synchronized Set<UUID> getTrusted(UUID owner) {
        return getPlayerData(owner).getTrustedPlayers();
    }

    @Override
    public synchronized void setTrusted(UUID owner, Set<UUID> trusted) {
        PlayerData data = getPlayerData(owner);
        data.setTrustedPlayers(trusted);
        save(data);
    }

    // === SERVER-WIDE CONFIG (data/server.yml) ===

    /** Exposes the server-wide config section for callers that need plugin-level (non-per-player) storage. */
    public synchronized FileConfiguration getServerConfig() {
        return serverCfg;
    }

    /** Persists whatever's currently in {@link #getServerConfig()} to disk. */
    public synchronized void saveServerConfig() {
        try {
            serverCfg.save(serverFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save server.yml to disk", e);
        }
    }
}