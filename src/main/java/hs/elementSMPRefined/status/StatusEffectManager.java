package hs.elementSMPRefined.status;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized manager for status effects like stun, slow, silence, etc.
 * Provides a data-driven API for applying and managing status effects.
 */
public class StatusEffectManager {
    private final JavaPlugin plugin;
    private final Map<UUID, Map<StatusEffectType, StatusEffectInstance>> activeEffects = new ConcurrentHashMap<>();
    private final Map<StatusEffectType, StatusEffectData> effectData = new EnumMap<>(StatusEffectType.class);

    public StatusEffectManager(JavaPlugin plugin) {
        this.plugin = plugin;
        initializeDefaultEffects();
        startEffectMonitor();
    }

    /**
     * Initialize default status effects with their data
     */
    private void initializeDefaultEffects() {
        // Full Stun - cannot move, look around, or interact
        registerEffectData(StatusEffectType.FULL_STUN, StatusEffectData.builder()
                .displayName("Full Stun")
                .description("Unable to move, look around, or interact")
                .potionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 127, false, false))
                .isStackable(false)
                .maxDuration(300) // 5 seconds max
                .build());

        // Partial Stun - cannot move or look around, but can interact
        registerEffectData(StatusEffectType.PARTIAL_STUN, StatusEffectData.builder()
                .displayName("Partial Stun")
                .description("Unable to move or look around, but can interact")
                .potionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 127, false, false))
                .isStackable(false)
                .maxDuration(400) // 8 seconds max
                .build());

        // Stun - cannot move, but can look and interact
        registerEffectData(StatusEffectType.STUN, StatusEffectData.builder()
                .displayName("Stun")
                .description("Unable to move, but can look and interact")
                .potionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 5, false, false))
                .isStackable(false)
                .maxDuration(400) // 8 seconds max
                .build());

        // Root - same as STUN (cannot move but can look and interact)
        registerEffectData(StatusEffectType.ROOT, StatusEffectData.builder()
                .displayName("Root")
                .description("Rooted in place")
                .potionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 5, false, false))
                .isStackable(false)
                .maxDuration(400) // 8 seconds max
                .build());

        // Slow effect - reduces movement speed
        registerEffectData(StatusEffectType.SLOW, StatusEffectData.builder()
                .displayName("Slow")
                .description("Reduced movement speed")
                .potionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 2, false, false))
                .isStackable(true)
                .maxDuration(600) // 10 seconds max
                .build());

        // Silence effect - prevents ability usage
        registerEffectData(StatusEffectType.SILENCE, StatusEffectData.builder()
                .displayName("Silence")
                .description("Unable to use abilities")
                .isStackable(false)
                .maxDuration(400) // 8 seconds max
                .build());

        // Weakness effect - reduces damage dealt
        registerEffectData(StatusEffectType.WEAKNESS, StatusEffectData.builder()
                .displayName("Weakness")
                .description("Reduced damage output")
                .potionEffect(new PotionEffect(PotionEffectType.WEAKNESS, PotionEffect.INFINITE_DURATION, 1, false, false))
                .isStackable(true)
                .maxDuration(800) // 13 seconds max
                .build());

        // Freeze effect - prevents movement but allows actions (handled by event listener)
        registerEffectData(StatusEffectType.FREEZE, StatusEffectData.builder()
                .displayName("Freeze")
                .description("Frozen in place")
                .isStackable(false)
                .maxDuration(200) // 4 seconds max
                .build());

        // Bleed effect - damage over time
        registerEffectData(StatusEffectType.BLEED, StatusEffectData.builder()
                .displayName("Bleed")
                .description("Taking damage over time")
                .isStackable(true)
                .maxDuration(1000) // 16 seconds max
                .damagePerSecond(1.0)
                .build());

        // Burn effect - fire damage over time
        registerEffectData(StatusEffectType.BURN, StatusEffectData.builder()
                .displayName("Burn")
                .description("Taking fire damage over time")
                .isStackable(true)
                .maxDuration(600) // 10 seconds max
                .damagePerSecond(2.0)
                .build());
    }

    /**
     * Register custom status effect data
     */
    public void registerEffectData(StatusEffectType type, StatusEffectData data) {
        effectData.put(type, data);
    }

    /**
     * Apply a status effect to a player
     * @param player The target player
     * @param type The type of status effect
     * @param duration Duration in ticks (20 ticks = 1 second)
     * @param amplifier The strength of the effect (for stackable effects)
     */
    public void applyEffect(Player player, StatusEffectType type, int duration, int amplifier) {
        UUID uuid = player.getUniqueId();
        StatusEffectData data = effectData.get(type);

        if (data == null) {
            return;
        }

        // Cap duration at max
        int actualDuration = Math.min(duration, data.maxDuration());

        Map<StatusEffectType, StatusEffectInstance> playerEffects = activeEffects
                .computeIfAbsent(uuid, k -> new HashMap<>());

        StatusEffectInstance existing = playerEffects.get(type);

        if (existing != null) {
            if (data.isStackable()) {
                // Stack the effect
                existing.addStack(amplifier);
                existing.extendDuration(actualDuration);
            } else {
                // Refresh duration for non-stackable effects
                existing.refreshDuration(actualDuration);
            }
        } else {
            // New effect
            playerEffects.put(type, new StatusEffectInstance(type, actualDuration, amplifier));
        }

        // Apply potion effects immediately
        applyPotionEffects(player, type, data);

        // Notify player
        player.sendMessage("§c" + data.displayName() + " applied for " + (actualDuration / 20.0) + " seconds");
    }

    /**
     * Apply a status effect with default amplifier (1)
     */
    public void applyEffect(Player player, StatusEffectType type, int duration) {
        applyEffect(player, type, duration, 1);
    }

    /**
     * Convenience method to apply a full stun
     */
    public void applyFullStun(Player player, int duration) {
        applyEffect(player, StatusEffectType.FULL_STUN, duration);
    }

    /**
     * Convenience method to apply a partial stun
     */
    public void applyPartialStun(Player player, int duration) {
        applyEffect(player, StatusEffectType.PARTIAL_STUN, duration);
    }

    /**
     * Convenience method to apply a regular stun
     */
    public void applyStun(Player player, int duration) {
        applyEffect(player, StatusEffectType.STUN, duration);
    }

    /**
     * Convenience method to apply a root effect
     */
    public void applyRoot(Player player, int duration) {
        applyEffect(player, StatusEffectType.ROOT, duration);
    }

    /**
     * Remove a status effect from a player
     */
    public void removeEffect(Player player, StatusEffectType type) {
        UUID uuid = player.getUniqueId();
        Map<StatusEffectType, StatusEffectInstance> playerEffects = activeEffects.get(uuid);

        if (playerEffects != null) {
            StatusEffectInstance removed = playerEffects.remove(type);
            if (removed != null) {
                removePotionEffects(player, type);
                player.sendMessage("§a" + effectData.get(type).displayName() + " removed");
            }
        }
    }

    /**
     * Remove all status effects from a player
     */
    public void removeAllEffects(Player player) {
        UUID uuid = player.getUniqueId();
        Map<StatusEffectType, StatusEffectInstance> playerEffects = activeEffects.remove(uuid);

        if (playerEffects != null) {
            for (StatusEffectType type : playerEffects.keySet()) {
                removePotionEffects(player, type);
            }
            player.sendMessage("§aAll status effects removed");
        }
    }

    /**
     * Check if a player has a specific status effect
     */
    public boolean hasEffect(Player player, StatusEffectType type) {
        UUID uuid = player.getUniqueId();
        Map<StatusEffectType, StatusEffectInstance> playerEffects = activeEffects.get(uuid);
        return playerEffects != null && playerEffects.containsKey(type);
    }

    /**
     * Get the remaining duration of a status effect in ticks
     */
    public int getRemainingDuration(Player player, StatusEffectType type) {
        UUID uuid = player.getUniqueId();
        Map<StatusEffectType, StatusEffectInstance> playerEffects = activeEffects.get(uuid);

        if (playerEffects != null) {
            StatusEffectInstance instance = playerEffects.get(type);
            return instance != null ? instance.getRemainingDuration() : 0;
        }
        return 0;
    }

    /**
     * Check if a player is silenced (cannot use abilities)
     */
    public boolean isSilenced(Player player) {
        return hasEffect(player, StatusEffectType.SILENCE);
    }

    /**
     * Check if a player is fully stunned (cannot move, look, or interact)
     */
    public boolean isFullyStunned(Player player) {
        return hasEffect(player, StatusEffectType.FULL_STUN);
    }

    /**
     * Check if a player is partially stunned (cannot move or look, but can interact)
     */
    public boolean isPartiallyStunned(Player player) {
        return hasEffect(player, StatusEffectType.PARTIAL_STUN);
    }

    /**
     * Check if a player is stunned (cannot move, but can look and interact)
     */
    public boolean isStunned(Player player) {
        return hasEffect(player, StatusEffectType.STUN);
    }

    /**
     * Check if a living entity is stunned (for mob support)
     */
    public boolean isStunned(org.bukkit.entity.LivingEntity entity) {
        if (entity instanceof Player player) {
            return isStunned(player);
        }
        // Mobs don't use the status effect system for stuns
        return false;
    }

    /**
     * Check if a player is rooted (cannot move but can look and interact)
     */
    public boolean isRooted(Player player) {
        return hasEffect(player, StatusEffectType.ROOT);
    }

    /**
     * Check if a player has any type of stun (full, partial, or regular)
     */
    public boolean hasAnyStun(Player player) {
        return isFullyStunned(player) || isPartiallyStunned(player) || isStunned(player) || isRooted(player);
    }

    /**
     * Check if a player is frozen (cannot move but can act)
     */
    public boolean isFrozen(Player player) {
        return hasEffect(player, StatusEffectType.FREEZE);
    }

    /**
     * Apply potion effects for a status effect
     * Note: Stun and Freeze are handled by event listeners, not potion effects
     */
    private void applyPotionEffects(Player player, StatusEffectType type, StatusEffectData data) {
        if (data.potionEffects() != null) {
            for (PotionEffect effect : data.potionEffects()) {
                player.addPotionEffect(effect);
            }
        }
        // Stun and Freeze are handled by event listeners, no potion effects needed
    }

    /**
     * Remove potion effects for a status effect
     * Note: Stun and Freeze are handled by event listeners, not potion effects
     */
    private void removePotionEffects(Player player, StatusEffectType type) {
        StatusEffectData data = effectData.get(type);
        if (data != null && data.potionEffects() != null) {
            for (PotionEffect effect : data.potionEffects()) {
                player.removePotionEffect(effect.getType());
            }
        }
        // Stun and Freeze are handled by event listeners, no cleanup needed here
    }

    /**
     * Start the effect monitoring task
     */
    private void startEffectMonitor() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                Iterator<Map.Entry<UUID, Map<StatusEffectType, StatusEffectInstance>>> playerIterator =
                        activeEffects.entrySet().iterator();

                while (playerIterator.hasNext()) {
                    Map.Entry<UUID, Map<StatusEffectType, StatusEffectInstance>> entry = playerIterator.next();
                    UUID uuid = entry.getKey();
                    Map<StatusEffectType, StatusEffectInstance> effects = entry.getValue();

                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline()) {
                        playerIterator.remove();
                        continue;
                    }

                    Iterator<Map.Entry<StatusEffectType, StatusEffectInstance>> effectIterator =
                            effects.entrySet().iterator();

                    while (effectIterator.hasNext()) {
                        Map.Entry<StatusEffectType, StatusEffectInstance> effectEntry = effectIterator.next();
                        StatusEffectType type = effectEntry.getKey();
                        StatusEffectInstance instance = effectEntry.getValue();

                        // Process damage over time effects
                        StatusEffectData data = effectData.get(type);
                        if (data != null && data.damagePerSecond() > 0) {
                            if (instance.shouldApplyDamage()) {
                                double damage = data.damagePerSecond() * instance.getAmplifier();
                                player.damage(damage);
                            }
                        }

                        // Check if effect has expired
                        if (instance.isExpired()) {
                            removePotionEffects(player, type);
                            effectIterator.remove();
                        }
                    }

                    // Remove player entry if no effects remain
                    if (effects.isEmpty()) {
                        playerIterator.remove();
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L); // Run every tick
    }

    /**
     * Clean up when plugin disables
     */
    public void cleanup() {
        activeEffects.clear();
    }

    /**
     * Data class for status effect metadata
     */
    public record StatusEffectData(
            String displayName,
            String description,
            PotionEffect[] potionEffects,
            boolean isStackable,
            int maxDuration,
            double damagePerSecond
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String displayName;
            private String description;
            private List<PotionEffect> potionEffects = new ArrayList<>();
            private boolean isStackable = false;
            private int maxDuration = 1200; // 1 minute default
            private double damagePerSecond = 0.0;

            public Builder displayName(String displayName) {
                this.displayName = displayName;
                return this;
            }

            public Builder description(String description) {
                this.description = description;
                return this;
            }

            public Builder potionEffect(PotionEffect effect) {
                this.potionEffects.add(effect);
                return this;
            }

            public Builder isStackable(boolean isStackable) {
                this.isStackable = isStackable;
                return this;
            }

            public Builder maxDuration(int maxDuration) {
                this.maxDuration = maxDuration;
                return this;
            }

            public Builder damagePerSecond(double damagePerSecond) {
                this.damagePerSecond = damagePerSecond;
                return this;
            }

            public StatusEffectData build() {
                if (displayName == null) {
                    throw new IllegalStateException("Display name is required");
                }
                return new StatusEffectData(
                        displayName,
                        description,
                        potionEffects.toArray(new PotionEffect[0]),
                        isStackable,
                        maxDuration,
                        damagePerSecond
                );
            }
        }
    }

    /**
     * Internal class to track active effect instances
     */
    private static class StatusEffectInstance {
        private final StatusEffectType type;
        private long expiryTime;
        private int amplifier;
        private long lastDamageTick;

        public StatusEffectInstance(StatusEffectType type, int durationTicks, int amplifier) {
            this.type = type;
            this.expiryTime = System.currentTimeMillis() + (durationTicks * 50L); // Convert ticks to ms
            this.amplifier = amplifier;
            this.lastDamageTick = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() >= expiryTime;
        }

        public int getRemainingDuration() {
            long remaining = expiryTime - System.currentTimeMillis();
            return (int) Math.max(0, remaining / 50); // Convert ms to ticks
        }

        public void refreshDuration(int durationTicks) {
            this.expiryTime = System.currentTimeMillis() + (durationTicks * 50L);
        }

        public void extendDuration(int additionalTicks) {
            this.expiryTime += (additionalTicks * 50L);
        }

        public void addStack(int additionalAmplifier) {
            this.amplifier += additionalAmplifier;
        }

        public int getAmplifier() {
            return amplifier;
        }

        public boolean shouldApplyDamage() {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastDamageTick >= 1000) { // 1 second
                lastDamageTick = currentTime;
                return true;
            }
            return false;
        }
    }
}