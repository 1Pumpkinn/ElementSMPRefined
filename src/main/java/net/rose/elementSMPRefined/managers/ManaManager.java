package net.rose.elementSMPRefined.managers;

import net.rose.elementSMPRefined.core.API.event.ManaSpendEvent;
import net.rose.elementSMPRefined.data.DataStore;
import net.rose.elementSMPRefined.data.PlayerData;
import net.rose.elementSMPRefined.util.visual.ActionBarImage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
    private static final String INFINITE_MANA_DISPLAY = "\u221E"; // ∞, shown for creative mode

    // The label never changes, so it's built once instead of on every tick
    // for every online player - the only parts that vary per player/tick
    // are the current-mana value and (once per tick) the max-mana suffix.
    private static final Component MANA_LABEL =
            Component.text("\u24C2 Mana: ").color(NamedTextColor.AQUA); // Ⓜ Mana:

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
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** Runs once per second: regen everyone online, refresh their action bar, flush if due. */
    private void tick() {
        int maxMana = configManager.getMaxMana();
        int regenRate = configManager.getManaRegenPerSecond();
        // Same for every player this tick, so build it once rather than per player.
        Component maxManaSuffix = Component.text("/" + maxMana).color(NamedTextColor.GRAY);

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData pd = get(player.getUniqueId());
            boolean creative = player.getGameMode() == GameMode.CREATIVE;

            if (creative) {
                pd.setMana(maxMana);
            } else {
                regenMana(player.getUniqueId(), pd, maxMana, regenRate);
            }

            sendManaActionBar(player, pd, maxMana, maxManaSuffix, creative);
        }

        flushDirtyIfDue();
    }

    /** Adds regen to a non-creative player's mana, capping at max, and marks them dirty if it changed. */
    private void regenMana(UUID uuid, PlayerData pd, int maxMana, int regenRate) {
        if (pd.getMana() >= maxMana) return;

        pd.addMana(regenRate);
        if (pd.getMana() > maxMana) {
            pd.setMana(maxMana);
        }
        dirty.add(uuid);
    }

    private void sendManaActionBar(Player player, PlayerData pd, int maxMana, Component maxManaSuffix, boolean creative) {
        String manaDisplay = creative ? INFINITE_MANA_DISPLAY : String.valueOf(pd.getMana());
        player.sendActionBar(
                ActionBarImage.icon()
                        .append(Component.text(" "))
                        .append(MANA_LABEL)
                        .append(Component.text(manaDisplay).color(NamedTextColor.WHITE))
                        .append(maxManaSuffix)
        );
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
        // Creative mode players don't spend mana - nothing was actually
        // deducted, so no ManaSpendEvent fires for this bypass.
        if (player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }

        PlayerData pd = get(player.getUniqueId());
        if (pd.getMana() < amount) return false;
        pd.addMana(-amount);
        dirty.add(player.getUniqueId());

        Bukkit.getPluginManager().callEvent(new ManaSpendEvent(player, amount, pd.getMana()));

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

    /**
     * Forcibly removes mana from a player without firing {@link ManaSpendEvent}
     * - this is a drain being done TO the player (e.g. mana steal), not a spend
     * BY the player. Creative mode players are immune (infinite mana, nothing
     * to drain).
     *
     * @param target the player being drained
     * @param amount the amount to attempt to remove
     * @return the amount actually removed (capped at the player's current mana)
     */
    public int drain(Player target, int amount) {
        if (amount <= 0) return 0;
        if (target.getGameMode() == GameMode.CREATIVE) return 0;

        PlayerData pd = get(target.getUniqueId());
        int actual = Math.min(amount, pd.getMana());
        if (actual <= 0) return 0;

        pd.addMana(-actual);
        dirty.add(target.getUniqueId());
        return actual;
    }

    /**
     * Grants mana to a player, capped at their configured max mana. Used to
     * hand a caster the mana stolen from a mana-steal target.
     *
     * @param player the player receiving mana
     * @param amount the amount to add
     */
    public void restore(Player player, int amount) {
        if (amount <= 0) return;

        PlayerData pd = get(player.getUniqueId());
        int maxMana = configManager.getMaxMana();
        pd.addMana(amount);
        if (pd.getMana() > maxMana) {
            pd.setMana(maxMana);
        }
        dirty.add(player.getUniqueId());
    }
}