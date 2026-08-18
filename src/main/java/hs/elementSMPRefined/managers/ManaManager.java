package hs.elementSMPRefined.managers;

import hs.elementSMPRefined.data.DataStore;
import hs.elementSMPRefined.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles per-second mana regen and spend/check calls.
 * <p>
 * Mana changes only mutate the {@link PlayerData} instance cached in
 * {@link DataStore} - they do NOT hit disk immediately. Writing a full
 * players.yml on every regen tick for every online player is a main-thread
 * disk I/O storm waiting to happen as the player count grows. Instead,
 * changed players are flushed in a batch every {@link #FLUSH_INTERVAL_SECONDS}
 * seconds via {@link DataStore#saveAsync}, which does the actual YAML
 * read/write off the main thread, plus explicitly on quit (see
 * {@code PlayerLifecycleListener}, synchronous since it's a single player)
 * and on plugin disable (see {@code AbstractElementPlugin#onDisable}, a
 * final synchronous flush so nothing is lost outside of a hard crash).
 */
public class ManaManager {
    private static final int FLUSH_INTERVAL_SECONDS = 30;

    private final JavaPlugin plugin;
    private final DataStore store;
    private final ConfigManager configManager;
    private BukkitTask task;

    /** UUIDs with in-memory mana changes not yet written to disk. */
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private int ticksSinceFlush = 0;

    public ManaManager(JavaPlugin plugin, DataStore store, ConfigManager configManager) {
        this.plugin = plugin;
        this.store = store;
        this.configManager = configManager;
    }

    public void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int maxMana = configManager.getMaxMana();
            int regenRate = configManager.getManaRegenPerSecond();

            for (Player p : Bukkit.getOnlinePlayers()) {
                PlayerData pd = get(p.getUniqueId());

                // Creative mode players have infinite mana
                if (p.getGameMode() == GameMode.CREATIVE) {
                    pd.setMana(maxMana);
                } else {
                    // Normal mana regen for survival/adventure/spectator
                    int before = pd.getMana();
                    if (before < maxMana) {
                        pd.addMana(regenRate);
                        // Ensure we don't exceed max mana
                        if (pd.getMana() > maxMana) {
                            pd.setMana(maxMana);
                        }
                        dirty.add(p.getUniqueId());
                    }
                }

                // Action bar display with mana emoji
                String manaDisplay = p.getGameMode() == GameMode.CREATIVE ? "∞" : String.valueOf(pd.getMana());
                p.sendActionBar(
                        net.kyori.adventure.text.Component.text("Ⓜ Mana: ")
                                .color(net.kyori.adventure.text.format.NamedTextColor.AQUA)
                                .append(net.kyori.adventure.text.Component.text(manaDisplay, net.kyori.adventure.text.format.NamedTextColor.WHITE))
                                .append(net.kyori.adventure.text.Component.text("/" + maxMana, net.kyori.adventure.text.format.NamedTextColor.GRAY))
                );
            }

            flushDirtyIfDue();
        }, 20L, 20L);
    }

    /** Every FLUSH_INTERVAL_SECONDS ticks of this task, persist whatever changed since the last flush. */
    private void flushDirtyIfDue() {
        ticksSinceFlush++;
        if (ticksSinceFlush < FLUSH_INTERVAL_SECONDS) {
            return;
        }
        ticksSinceFlush = 0;
        flushDirty();
    }

    private void flushDirty() {
        if (dirty.isEmpty()) return;
        for (UUID uuid : dirty) {
            PlayerData pd = store.getPlayerData(uuid);
            // Async: this runs on the main thread every FLUSH_INTERVAL_SECONDS for
            // every dirty player, so a synchronous store.save() here would mean
            // main-thread YAML read+write per player, scaling with player count.
            store.saveAsync(pd);
        }
        dirty.clear();
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        // Don't lose the last <30s of regen when the manager stops (e.g. on reload).
        flushDirty();
    }

    public PlayerData get(UUID uuid) {
        return store.getPlayerData(uuid);
    }

    /** Immediately persists a player's mana, bypassing the batch - used on quit. */
    public void save(UUID uuid) {
        dirty.remove(uuid);
        PlayerData pd = store.getPlayerData(uuid);
        store.save(pd);
    }

    public boolean spend(Player player, int amount) {
        // Creative mode players don't spend mana
        if (player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }

        PlayerData pd = get(player.getUniqueId());
        if (pd.getMana() < amount) return false;
        pd.addMana(-amount);
        dirty.add(player.getUniqueId());
        return true;
    }

    /**
     * Check if player has enough mana without spending it
     * @param player The player to check
     * @param amount The amount of mana required
     * @return true if player has enough mana, false otherwise
     */
    public boolean hasMana(Player player, int amount) {
        // Creative mode players always have mana
        if (player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }

        PlayerData pd = get(player.getUniqueId());
        return pd.getMana() >= amount;
    }
}