package net.rose.elementSMPRefined.ability.passive.basic.air.listeners;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ElementManager;
import org.bukkit.plugin.java.JavaPlugin;
import net.rose.elementSMPRefined.util.sound.SoundUtils;
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
 * Handles Air element's passives - no fall damage, and the further you fall,
 * the further nearby entities get knocked back on landing.
 * Also inherited by Storm (upgraded from Air).
 */
public class AirFallImpactListener implements Listener {
    private final ElementSMPRefined plugin;
    private final ElementManager elementManager;
    private final Map<UUID, Double> playerFallStartY;
    private final Map<UUID, Boolean> playerIsFalling;

    public AirFallImpactListener(JavaPlugin plugin, ElementManager elementManager) {
        this.plugin = (ElementSMPRefined) plugin;
        this.elementManager = elementManager;
        this.playerFallStartY = new HashMap<>();
        this.playerIsFalling = new HashMap<>();
    }

    /** Air, and Storm (upgraded from Air, inherits these passives). */
    private static boolean hasAirPassives(ElementType type) {
        return type == ElementType.AIR || type == ElementType.STORM;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;

        var playerData = elementManager.data(player.getUniqueId());
        if (playerData == null || !hasAirPassives(playerData.getCurrentElement())) return;

        UUID playerId = player.getUniqueId();
        Double startY = playerFallStartY.get(playerId);

        double fallDistance = 0;
        if (startY != null) {
            fallDistance = startY - player.getLocation().getY();
        }

        event.setCancelled(true);

        playerFallStartY.remove(playerId);
        playerIsFalling.remove(playerId);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        var playerData = elementManager.data(playerId);
        if (playerData == null || !hasAirPassives(playerData.getCurrentElement())) return;

        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null) return;

        boolean isOnGround = player.isOnGround();

        if (!isOnGround && to.getY() < from.getY() && (from.getY() - to.getY()) > 0.5) {
            Boolean isFalling = playerIsFalling.get(playerId);

            if (isFalling == null || !isFalling) {
                playerFallStartY.put(playerId, from.getY());
                playerIsFalling.put(playerId, true);
            }
        }

        Boolean isFalling = playerIsFalling.get(playerId);
        if (isFalling != null && isFalling && isOnGround) {
            Double startY = playerFallStartY.get(playerId);
            double fallDistance = 0;
            if (startY != null) {
                fallDistance = startY - to.getY();
            }

            if (fallDistance >= 4.0) {
                applyFallImpactKnockback(player, fallDistance);
            }

            playerFallStartY.remove(playerId);
            playerIsFalling.remove(playerId);
        }

        if (to.getY() > from.getY() && (to.getY() - from.getY()) > 1.0) {
            playerFallStartY.remove(playerId);
            playerIsFalling.remove(playerId);
        }
    }

    private void applyFallImpactKnockback(Player player, double fallDistance) {
        Location impactLocation = player.getLocation();
        double knockbackStrength = calculateKnockbackStrength(fallDistance);
        double knockbackRadius = calculateKnockbackRadius(fallDistance);


        int entitiesKnocked = 0;
        for (Entity entity : player.getNearbyEntities(knockbackRadius, knockbackRadius, knockbackRadius)) {
            if (entity == player) continue;
            if (!(entity instanceof org.bukkit.entity.LivingEntity)) continue;

            Vector offset = entity.getLocation().toVector().subtract(impactLocation.toVector());
            if (offset.lengthSquared() < 1.0E-4) {
                offset = new Vector(0, 0, 1); // normalize() of a zero vector is NaN and setVelocity throws
            }
            Vector direction = offset.normalize();

            Vector knockback = direction.multiply(knockbackStrength);
            knockback.setY(Math.min(knockbackStrength * 0.5, 2.0));

            entity.setVelocity(knockback);
            entitiesKnocked++;
        }

        SoundUtils.playTo(player, SoundUtils.Element.AIR);

        createAnimatedImpactEffect(impactLocation, knockbackRadius);
    }

    private double calculateKnockbackStrength(double fallDistance) {
        // Base strength of 1.0, increases by 0.1 for every block fallen beyond 4
        // Cap at maximum strength of 3.0 (reduced from 5.0 for better balance)
        double baseStrength = 1.0;
        double additionalStrength = Math.min((fallDistance - 4.0) * 0.1, 2.0);
        return Math.min(baseStrength + additionalStrength, 3.0);
    }

    private double calculateKnockbackRadius(double fallDistance) {
        // Base radius of 3.0 blocks, increases by 0.2 for every block fallen beyond 4
        // Cap at maximum radius of 8.0 blocks (reduced from 10.0 for better balance)
        double baseRadius = 3.0;
        double additionalRadius = Math.min((fallDistance - 4.0) * 0.2, 5.0);
        return Math.min(baseRadius + additionalRadius, 8.0);
    }

    private void createAnimatedImpactEffect(Location location, double radius) {
        World world = location.getWorld();
        if (world == null) return;

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
                double currentRadius = radius * (1.0 - progress * 0.7);

                int cloudCount = (int) (20 * (1.0 - progress));
                for (int i = 0; i < cloudCount; i++) {
                    double angle = (2 * Math.PI * i) / cloudCount + (progress * Math.PI);
                    double x = Math.cos(angle) * currentRadius;
                    double z = Math.sin(angle) * currentRadius;
                    double y = 0.3 + (progress * 0.5);

                    Location particleLoc = location.clone().add(x, y, z);
                    world.spawnParticle(Particle.CLOUD, particleLoc, 1, 0, 0.1, 0, 0.02);
                }

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
