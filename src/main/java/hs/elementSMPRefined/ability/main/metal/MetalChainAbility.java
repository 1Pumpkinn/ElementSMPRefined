package hs.elementSMPRefined.ability.main.metal;

import hs.elementSMPRefined.API.element.ElementContext;
import hs.elementSMPRefined.API.ability.BaseAbility;
import hs.elementSMPRefined.ElementSMPRefined;
import org.bukkit.plugin.java.JavaPlugin;
import hs.elementSMPRefined.util.visual.SoundUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class MetalChainAbility extends BaseAbility {
    private final ElementSMPRefined plugin;

    public MetalChainAbility(JavaPlugin plugin) {
        super("metal_chain", 50, 10, 1);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();

        // --- Target detection (cone-based with line-of-sight check) ---
        LivingEntity target = null;
        double range = 20;
        double coneAngle = Math.toRadians(25);
        Location eyeLoc = player.getEyeLocation();
        Vector lookDir = eyeLoc.getDirection();

        for (LivingEntity entity : player.getWorld().getLivingEntities()) {
            if (entity.equals(player)) continue;

            // Skip armor stands
            if (entity instanceof org.bukkit.entity.ArmorStand) continue;

            if (eyeLoc.distanceSquared(entity.getLocation()) > range * range) continue;

            Vector toEntity = entity.getLocation().toVector().subtract(eyeLoc.toVector());
            double angle = lookDir.angle(toEntity);

            if (angle < coneAngle) {
                // Check line of sight - make sure there are no solid blocks between player and target
                if (player.hasLineOfSight(entity)) {
                    target = entity;
                    break;
                }
            }
        }

        if (target == null) {
            player.sendMessage(ChatColor.RED + "No target found!");
            return false;
        }

        // Don't target trusted players
        if (target instanceof Player targetPlayer) {
            if (context.getTrustManager().isTrusted(player.getUniqueId(), targetPlayer.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "You cannot chain trusted players!");
                return false;
            }
        }

        final LivingEntity finalTarget = target;

        // Play sounds
        SoundUtils.playTo(player, SoundUtils.Element.METAL);
        if (finalTarget instanceof Player) {
            SoundUtils.playTo((Player) finalTarget, SoundUtils.Combat.HIT);
        }

        // Start chain particle animation and reeling
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 40; // 2 seconds of reeling
            final Location playerStartLoc = player.getEyeLocation().subtract(0, 0.3, 0);

            @Override
            public void run() {
                if (!player.isOnline() || !finalTarget.isValid() || ticks >= maxTicks) {
                    cancel();
                    return;
                }

                // Get current positions
                Location currentPlayerLoc = player.getEyeLocation().subtract(0, 0.3, 0);
                Location targetLoc = finalTarget.getEyeLocation();

                // Calculate distance
                double distance = currentPlayerLoc.distance(targetLoc);

                // If target is close enough, stop
                if (distance < 2.0) {
                    // Set velocity to zero to stop movement
                    finalTarget.setVelocity(new Vector(0, 0, 0));

                    // Apply stun using StatusEffectManager (3 seconds = 60 ticks)
                    if (finalTarget instanceof Player targetPlayer) {
                        plugin.getStatusEffectManager().applyStun(targetPlayer, 60);
                    } else {
                        // For mobs, stop movement by disabling AI and applying slowness
                        if (finalTarget instanceof Mob mob) {
                            // Store original AI state
                            boolean wasAware = mob.isAware();
                            
                            // Disable AI to stop movement
                            mob.setAware(false);
                            mob.setAI(false);
                            
                            // Apply slowness potion effect to further reduce movement
                            mob.addPotionEffect(new PotionEffect(
                                PotionEffectType.SLOWNESS,
                                60, // 3 seconds
                                10, // High amplifier
                                false, // No particles
                                false  // No icon
                            ));
                            
                            // Set velocity to zero immediately
                            finalTarget.setVelocity(new Vector(0, 0, 0));
                            
                            // Re-enable AI after 3 seconds
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (finalTarget.isValid() && finalTarget instanceof Mob m) {
                                        m.setAware(wasAware);
                                        m.setAI(true);
                                        m.removePotionEffect(PotionEffectType.SLOWNESS);
                                    }
                                }
                            }.runTaskLater(plugin, 60L);
                        }
                    }

                    // Visual/audio feedback for stun
                    player.getWorld().playSound(targetLoc, Sound.BLOCK_ANVIL_LAND, 1.0f, 2.0f);
                    player.getWorld().spawnParticle(Particle.BLOCK, targetLoc, 30,
                            0.3, 0.5, 0.3, 0.1,
                            org.bukkit.Material.IRON_BLOCK.createBlockData(), true);

                    cancel();
                    return;
                }

                // Draw particle chain
                Vector direction = targetLoc.toVector().subtract(currentPlayerLoc.toVector()).normalize();
                double particleSpacing = 0.3;
                int numParticles = (int) (distance / particleSpacing);

                for (int i = 0; i <= numParticles; i++) {
                    double t = i * particleSpacing;
                    Location particleLoc = currentPlayerLoc.clone().add(direction.clone().multiply(t));

                    // Main chain particles (iron blocks/anvil look)
                    player.getWorld().spawnParticle(Particle.BLOCK, particleLoc, 1,
                            0.05, 0.05, 0.05, 0.0,
                            org.bukkit.Material.IRON_BLOCK.createBlockData(), true);

                    // Add some sparkles for effect
                    if (i % 3 == 0) {
                        player.getWorld().spawnParticle(Particle.CRIT, particleLoc, 1,
                                0.02, 0.02, 0.02, 0.01, null, true);
                    }
                }

                // Apply gentle pull force to target
                Vector pullDirection = currentPlayerLoc.toVector().subtract(targetLoc.toVector()).normalize();

                // Gentle upward component to prevent dragging on ground
                pullDirection.setY(pullDirection.getY() + 0.1);

                // Smooth pull force (not instant velocity)
                double pullStrength = 0.25; // Gentle pull
                Vector currentVelocity = finalTarget.getVelocity();
                Vector newVelocity = currentVelocity.add(pullDirection.multiply(pullStrength));

                // Cap velocity to prevent launching
                double maxSpeed = 0.8;
                if (newVelocity.length() > maxSpeed) {
                    newVelocity = newVelocity.normalize().multiply(maxSpeed);
                }

                finalTarget.setVelocity(newVelocity);

                // Play chain sound periodically
                if (ticks % 10 == 0) {
                    player.getWorld().playSound(currentPlayerLoc, Sound.BLOCK_CHAIN_STEP, 0.5f, 1.2f);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    @Override
    public String getName() {
        return ChatColor.GRAY + "Chain Reel";
    }

    @Override
    public String getDescription() {
        return "Pull a targeted enemy towards you with a chain. (50 mana)";
    }
}