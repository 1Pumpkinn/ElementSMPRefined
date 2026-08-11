package hs.elementSMPRefined.elements.impl.air.listeners;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.elements.ElementType;
import hs.elementSMPRefined.managers.ElementManager;
import hs.elementSMPRefined.util.visual.SoundUtils;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles Air element's fall impact passive - the further you fall, 
 * the further nearby entities get knocked back on landing
 */
public class AirFallImpactListener implements Listener {
    private final ElementSMPRefined plugin;
    private final ElementManager elementManager;
    private final Map<UUID, Double> playerFallStartY;
    private final Map<UUID, Boolean> playerIsFalling;

    public AirFallImpactListener(ElementSMPRefined plugin, ElementManager elementManager) {
        this.plugin = plugin;
        this.elementManager = elementManager;
        this.playerFallStartY = new HashMap<>();
        this.playerIsFalling = new HashMap<>();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;

        var playerData = elementManager.data(player.getUniqueId());
        if (playerData == null || playerData.getCurrentElement() != ElementType.AIR) return;

        UUID playerId = player.getUniqueId();
        Double startY = playerFallStartY.get(playerId);
        
        // Calculate fall distance from stored start Y
        double fallDistance = 0;
        if (startY != null) {
            fallDistance = startY - player.getLocation().getY();
        }
        
        plugin.getLogger().info("Air fall damage cancelled for " + player.getName() + 
                ", fall distance: " + fallDistance);
        
        // Cancel fall damage for Air element players (passive 1)
        event.setCancelled(true);
        
        // Clean up tracking
        playerFallStartY.remove(playerId);
        playerIsFalling.remove(playerId);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        var playerData = elementManager.data(playerId);
        if (playerData == null || playerData.getCurrentElement() != ElementType.AIR) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        
        if (to == null) return;
        
        // Check if player is currently on ground
        boolean isOnGround = player.isOnGround();
        
        // Check if player just started falling (going down significantly)
        if (!isOnGround && to.getY() < from.getY() && (from.getY() - to.getY()) > 0.5) {
            // Player is falling downward
            Boolean isFalling = playerIsFalling.get(playerId);
            
            if (isFalling == null || !isFalling) {
                // Just started falling
                playerFallStartY.put(playerId, from.getY());
                playerIsFalling.put(playerId, true);
                plugin.getLogger().info("Player " + player.getName() + " started falling at Y: " + from.getY());
            }
        }
        
        // Check if player landed (was falling, now on ground)
        Boolean isFalling = playerIsFalling.get(playerId);
        if (isFalling != null && isFalling && isOnGround) {
            // Player landed - apply knockback here
            Double startY = playerFallStartY.get(playerId);
            double fallDistance = 0;
            if (startY != null) {
                fallDistance = startY - to.getY();
            }
            
            plugin.getLogger().info("Player " + player.getName() + " landed, fall distance: " + fallDistance);
            
            // Apply knockback based on fall distance
            if (fallDistance > 3.0) {
                applyFallImpactKnockback(player, fallDistance);
            }
            
            // Clean up tracking
            playerFallStartY.remove(playerId);
            playerIsFalling.remove(playerId);
        }
        
        // Reset if player is going up significantly (jumping/levitating/flying)
        if (to.getY() > from.getY() && (to.getY() - from.getY()) > 1.0) {
            playerFallStartY.remove(playerId);
            playerIsFalling.remove(playerId);
        }
    }

    private void applyFallImpactKnockback(Player player, double fallDistance) {
        Location impactLocation = player.getLocation();
        double knockbackStrength = calculateKnockbackStrength(fallDistance);
        double knockbackRadius = calculateKnockbackRadius(fallDistance);
        
        plugin.getLogger().info("Applying fall impact knockback - strength: " + knockbackStrength + 
                ", radius: " + knockbackRadius);
        
        // Find nearby entities
        int entitiesKnocked = 0;
        for (Entity entity : player.getNearbyEntities(knockbackRadius, knockbackRadius, knockbackRadius)) {
            if (entity == player) continue;
            if (!(entity instanceof org.bukkit.entity.LivingEntity)) continue;
            
            // Calculate knockback direction (away from player)
            Vector direction = entity.getLocation().toVector()
                    .subtract(impactLocation.toVector())
                    .normalize();
            
            // Apply knockback with upward component (capped)
            Vector knockback = direction.multiply(knockbackStrength);
            knockback.setY(Math.min(knockbackStrength * 0.5, 2.0)); // Cap upward component
            
            entity.setVelocity(knockback);
            entitiesKnocked++;
        }
        
        plugin.getLogger().info("Knocked back " + entitiesKnocked + " entities");
        
        // Play sound effect - air themed sound
        SoundUtils.playTo(player, SoundUtils.Element.AIR);
        
        // Visual feedback - animated particle effect
        createAnimatedImpactEffect(impactLocation, knockbackRadius);
    }

    private double calculateKnockbackStrength(double fallDistance) {
        // Base strength of 1.0, increases by 0.1 for every block fallen beyond 3
        // Cap at maximum strength of 3.0 (reduced from 5.0 for better balance)
        double baseStrength = 1.0;
        double additionalStrength = Math.min((fallDistance - 3.0) * 0.1, 2.0);
        return Math.min(baseStrength + additionalStrength, 3.0);
    }

    private double calculateKnockbackRadius(double fallDistance) {
        // Base radius of 3.0 blocks, increases by 0.2 for every block fallen beyond 3
        // Cap at maximum radius of 8.0 blocks (reduced from 10.0 for better balance)
        double baseRadius = 3.0;
        double additionalRadius = Math.min((fallDistance - 3.0) * 0.2, 5.0);
        return Math.min(baseRadius + additionalRadius, 8.0);
    }

    private void createAnimatedImpactEffect(Location location, double radius) {
        // Create animated particle effect for visual feedback
        World world = location.getWorld();
        if (world == null) return;
        
        // Animate particles over time using Bukkit scheduler
        org.bukkit.scheduler.BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            int tick = 0;
            private static final int DURATION = 10; // Animation duration in ticks
            
            @Override
            public void run() {
                if (tick >= DURATION) {
                    cancel();
                    return;
                }
                
                double progress = (double) tick / DURATION;
                double currentRadius = radius * (1.0 - progress * 0.7); // Particles expand outward then fade
                
                // Cloud particles that expand outward
                int cloudCount = (int) (20 * (1.0 - progress));
                for (int i = 0; i < cloudCount; i++) {
                    double angle = (2 * Math.PI * i) / cloudCount + (progress * Math.PI);
                    double x = Math.cos(angle) * currentRadius;
                    double z = Math.sin(angle) * currentRadius;
                    double y = 0.3 + (progress * 0.5); // Particles rise up
                    
                    Location particleLoc = location.clone().add(x, y, z);
                    world.spawnParticle(org.bukkit.Particle.CLOUD, particleLoc, 1, 0, 0.1, 0, 0.02);
                }
                
                // Smoke particles that drift outward
                int smokeCount = (int) (15 * (1.0 - progress));
                for (int i = 0; i < smokeCount; i++) {
                    double angle = (2 * Math.PI * i) / smokeCount + (progress * Math.PI * 0.5);
                    double x = Math.cos(angle) * (currentRadius * 0.8);
                    double z = Math.sin(angle) * (currentRadius * 0.8);
                    double y = 0.1 + (progress * 0.3);
                    
                    Location particleLoc = location.clone().add(x, y, z);
                    world.spawnParticle(Particle.SMOKE, particleLoc, 1, 0, 0.05, 0, 0.01);
                }
                
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void cleanupPlayer(UUID playerId) {
        playerFallStartY.remove(playerId);
        playerIsFalling.remove(playerId);
    }
}
