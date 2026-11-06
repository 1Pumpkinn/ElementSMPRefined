package net.rose.elementSMPRefined.data;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
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

    private static final int BACKUP_KEEP = 30;
    private static final long BACKUP_MIN_AGE_MINUTES = 30;

    private final File playerFile;
    private FileConfiguration playerCfg;
    private long lastUnreadableLogMillis = 0L;

    // Fingerprint of players.yml as of our last successful read/write, so unchanged files are
    // not re-parsed on every join/quit. -1 forces the next reloadFromDisk() to hit the disk.
    private long loadedModified = -1L;
    private long loadedLength = -1L;

    // Players whose data could not be loaded (players.yml unreadable at the time). They get a
    // throwaway default PlayerData that is never cached or written, so it can't overwrite the
    // real saved data once the file is readable again.
    private final Set<UUID> failedLoads = ConcurrentHashMap.newKeySet();

    private final Map<UUID, PlayerData> playerDataCache = new ConcurrentHashMap<>();

    public DataStore(JavaPlugin plugin) {
        this.plugin = plugin;

        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            throw new IllegalStateException("Could not create data directory: " + dataDir.getAbsolutePath());
        }

        this.playerFile = createIfMissing(dataDir, "players.yml");
        this.playerCfg = loadYaml(playerFile, "players.yml");

        // Rolling copy of players.yml taken before anything can modify it this session, so a bad
        // update/edit is always recoverable from data/backups/.
        try {
            YamlFiles.backup(playerFile, new File(dataDir, "backups"), "players", BACKUP_KEEP, BACKUP_MIN_AGE_MINUTES);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not back up players.yml", e);
        }
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
            FileConfiguration loaded = YamlFiles.loadStrict(file);
            stampLoaded();
            return loaded;
        } catch (IOException | InvalidConfigurationException e) {
            reportUnreadable(name, e);
            loadedModified = -1L;
            return new YamlConfiguration();
        }
    }

    private void reportUnreadable(String name, Exception e) {
        plugin.getLogger().log(Level.SEVERE, name + " could not be read - player data will NOT be saved until it is "
                + "fixed (nothing has been overwritten). Fix or restore it from data/backups/ and restart.", e);
        try {
            File copy = YamlFiles.quarantine(playerFile);
            plugin.getLogger().severe("Copied the unreadable file to " + copy.getName());
        } catch (IOException ioe) {
            plugin.getLogger().log(Level.WARNING, "Could not quarantine " + name, ioe);
        }
    }

    private boolean reloadFromDisk() {
        if (loadedModified >= 0 && playerFile.lastModified() == loadedModified
                && playerFile.length() == loadedLength) {
            return true; // unchanged since we last read/wrote it - the in-memory config is current
        }
        try {
            playerCfg = YamlFiles.loadStrict(playerFile);
            stampLoaded();
            return true;
        } catch (IOException | InvalidConfigurationException e) {
            loadedModified = -1L;
            long now = System.currentTimeMillis();
            if (now - lastUnreadableLogMillis > 60_000L) { // don't spam on every save
                lastUnreadableLogMillis = now;
                plugin.getLogger().log(Level.SEVERE, "players.yml is unreadable - skipping disk access to protect saved data", e);
            }
            return false;
        }
    }

    private void stampLoaded() {
        loadedModified = playerFile.lastModified();
        loadedLength = playerFile.length();
    }

    @Override
    public PlayerData getPlayerData(UUID uuid) {
        PlayerData cached = playerDataCache.get(uuid);
        if (cached != null) {
            return cached;
        }

        PlayerData loaded = loadPlayerDataFromFile(uuid);
        if (loaded == null) {
            // players.yml is unreadable: hand back defaults for now, but never cache or save them.
            failedLoads.add(uuid);
            return new PlayerData(uuid);
        }
        failedLoads.remove(uuid);
        // putIfAbsent (not computeIfAbsent): the loader takes the synchronized save() lock, and
        // computing inside the map could deadlock against a save() that is touching the cache.
        // If another thread won the race, hand back its instance so everyone shares one object.
        PlayerData existing = playerDataCache.putIfAbsent(uuid, loaded);
        return existing != null ? existing : loaded;
    }

    @Override
    public PlayerData load(UUID uuid) {
        return getPlayerData(uuid);
    }

    private synchronized PlayerData loadPlayerDataFromFile(UUID uuid) {
        if (!reloadFromDisk()) {
            return null;
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

    /**
     * Persists {@code data} synchronously on the calling thread. Intended
     * for infrequent, one-off saves (quit, admin commands, final shutdown
     * flush) where a single blocking YAML read+write is negligible. Do NOT
     * call this from a per-tick or per-player periodic loop - use
     * {@link #saveAsync(PlayerData)} instead.
     */
    @Override
    public synchronized void save(PlayerData data) {
        if (failedLoads.contains(data.getUuid())) return; // never overwrite real data with defaults
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
        if (failedLoads.contains(data.getUuid())) return; // never overwrite real data with defaults
        playerDataCache.put(data.getUuid(), data);
        // Snapshot on the calling (main) thread: the async task must not read a PlayerData that
        // the main thread may be changing at the same time.
        PlayerData snapshot = data.copy();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (this) {
                persistToDisk(snapshot);
            }
        });
    }

    private void persistToDisk(PlayerData data) {
        try {
            // Reload first so we merge onto whatever's currently on disk rather than
            // clobbering changes written by another save() call in between. If the file is
            // unreadable we must NOT continue: merging onto an empty config and saving would
            // erase every other player.
            if (!reloadFromDisk()) return;

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

    /**
     * Synchronous - only safe to call on shutdown/disable where blocking briefly is acceptable.
     * Writes every cached player to disk in one pass, so changes still queued in
     * {@link #saveAsync} when the server stops are not lost. Does nothing if players.yml is
     * unreadable (never overwrite a file that couldn't be parsed).
     */
    @Override
    public synchronized void flushAll() {
        if (!reloadFromDisk()) return;

        for (PlayerData data : playerDataCache.values()) {
            String key = "players." + data.getUuid();
            ConfigurationSection section = playerCfg.getConfigurationSection(key);
            if (section == null) {
                section = playerCfg.createSection(key);
            }
            PlayerDataSerializer.serialize(data, section);
        }
        flushPlayerData();
    }

    private void flushPlayerData() {
        try {
            YamlFiles.saveAtomic(playerCfg, playerFile);
            stampLoaded();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save players.yml to disk", e);
        }
    }
}