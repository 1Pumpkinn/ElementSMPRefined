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

    public void startCooldown(Player player, String abilityId, int seconds) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (seconds <= 0) return;
        long readyAt = System.currentTimeMillis() + (seconds * 1000L);
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).put(abilityId, readyAt);
    }

    public void clear(Player player, String abilityId) {
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns != null) {
            playerCooldowns.remove(abilityId);
        }
    }

    /**
     * Drops every cooldown that has already finished, and any player left with none. Called when
     * someone leaves so the map doesn't keep an entry for every player who ever used an ability.
     * Cooldowns still running are kept, so relogging can't be used to skip one.
     */
    public void pruneExpired() {
        long now = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(entry -> {
            entry.getValue().values().removeIf(readyAt -> now >= readyAt);
            return entry.getValue().isEmpty();
        });
    }
}
