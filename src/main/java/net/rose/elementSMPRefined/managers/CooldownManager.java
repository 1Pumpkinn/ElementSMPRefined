package net.rose.elementSMPRefined.managers;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player, per-ability cooldowns in memory.
 * <p>
 * Replaces the old mana system: abilities no longer have a resource cost,
 * they simply go on cooldown after a successful activation. Purely in-memory
 * and keyed on {@code System.currentTimeMillis()} - nothing here is persisted
 * to disk, so cooldowns reset on server restart.
 * <p>
 * Creative mode players bypass cooldowns entirely, mirroring the old
 * infinite-mana behavior in creative.
 */
public class CooldownManager {
    // player UUID -> (abilityId -> timestamp the cooldown ends, millis)
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    /**
     * Whether the given ability is currently off cooldown (and thus usable)
     * for this player. Creative mode players are always considered ready.
     */
    public boolean isReady(Player player, String abilityId) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }
        Long readyAt = cooldowns.getOrDefault(player.getUniqueId(), Map.of()).get(abilityId);
        return readyAt == null || System.currentTimeMillis() >= readyAt;
    }

    /**
     * Seconds remaining before the ability is off cooldown, or 0 if it's
     * already ready.
     */
    public long getRemainingSeconds(Player player, String abilityId) {
        Long readyAt = cooldowns.getOrDefault(player.getUniqueId(), Map.of()).get(abilityId);
        if (readyAt == null) return 0;
        long remainingMillis = readyAt - System.currentTimeMillis();
        if (remainingMillis <= 0) return 0;
        return (remainingMillis + 999) / 1000; // round up to the nearest second
    }

    /** Starts (or restarts) the cooldown for an ability, `seconds` from now. */
    public void startCooldown(Player player, String abilityId, int seconds) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (seconds <= 0) return;
        long readyAt = System.currentTimeMillis() + (seconds * 1000L);
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).put(abilityId, readyAt);
    }

    /**
     * Reduces the remaining cooldown on an ability by a number of seconds,
     * clearing it entirely if the reduction covers what's left. No-op if the
     * ability isn't currently on cooldown. Used by effects that shorten a
     * player's own other ability (e.g. Death's backstab).
     */
    public void reduceCooldown(Player player, String abilityId, int seconds) {
        if (seconds <= 0) return;
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) return;

        playerCooldowns.computeIfPresent(abilityId, (id, readyAt) -> {
            long reduced = readyAt - (seconds * 1000L);
            return reduced <= System.currentTimeMillis() ? null : reduced;
        });
    }

    /** Clears every cooldown for a player, e.g. on element change or admin reset. */
    public void clearAll(Player player) {
        cooldowns.remove(player.getUniqueId());
    }

    /** Clears a single ability's cooldown for a player. */
    public void clear(Player player, String abilityId) {
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns != null) {
            playerCooldowns.remove(abilityId);
        }
    }
}