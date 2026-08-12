package hs.elementSMPRefined.elements.abilities.impl.fire;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.elements.ElementContext;
import hs.elementSMPRefined.elements.abilities.BaseAbility;
import hs.elementSMPRefined.managers.ManaManager;
import hs.elementSMPRefined.managers.TrustManager;
import org.bukkit.*;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Meteor Ride - Ride on a meteor and crash into entities to deal damage.
 * The player controls the meteor's direction and it deals damage on impact.
 */
public class MeteorRideAbility extends BaseAbility implements Listener {
    private final ElementSMPRefined plugin;
    private final Map<UUID, Fireball> activeMeteors = new HashMap<>();
    private final Map<UUID, BukkitRunnable> meteorTasks = new HashMap<>();

    public MeteorRideAbility(ElementSMPRefined plugin) {
        super("fire_meteor_ride", 60, 15, 2);
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        ManaManager mana = context.getManaManager();
        TrustManager trust = context.getTrustManager();
        int cost = getManaCost();

        if (!mana.hasMana(player, cost)) {
            player.sendMessage(ChatColor.RED + "Not enough mana (" + cost + ")");
            return false;
        }

        UUID playerId = player.getUniqueId();
        
        // Check if player is already riding a meteor
        if (activeMeteors.containsKey(playerId)) {
            player.sendMessage(ChatColor.RED + "You are already riding a meteor!");
            return false;
        }

        World world = player.getWorld();
        Location spawnLoc = player.getLocation().add(0, 2, 0);
        Vector direction = player.getLocation().getDirection().normalize();

        // Create the meteor (fireball)
        Fireball meteor = world.spawn(spawnLoc, Fireball.class);
        meteor.setShooter(player);
        meteor.setDirection(direction.multiply(1.5));
        meteor.setYield(2.0f); // High explosion power
        meteor.setIsIncendiary(false); // Don't set blocks on fire

        // Mount the player on the meteor
        meteor.addPassenger(player);

        // Store the meteor
        activeMeteors.put(playerId, meteor);

        // Play sounds
        world.playSound(spawnLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
        world.playSound(spawnLoc, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.8f);

        // Create particle trail and damage task
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !meteor.isValid() || !meteor.getPassengers().contains(player)) {
                    endMeteorRide(player);
                    cancel();
                    return;
                }

                // Create fire particle trail
                Location meteorLoc = meteor.getLocation();
                world.spawnParticle(Particle.FLAME, meteorLoc, 10, 0.5, 0.5, 0.5, 0.1, null, true);
                world.spawnParticle(Particle.LAVA, meteorLoc, 3, 0.3, 0.3, 0.3, 0.05, null, true);

                // Damage entities on contact
                double damageRadius = 2.0;
                for (LivingEntity entity : meteorLoc.getNearbyLivingEntities(damageRadius)) {
                    if (entity.equals(player)) continue;
                    if (entity instanceof Player other && trust.isTrusted(player.getUniqueId(), other.getUniqueId())) continue;

                    entity.damage(8.0, player);
                    entity.setFireTicks(80); // Set on fire for 4 seconds
                    
                    // Knockback away from meteor
                    Vector knockback = entity.getLocation().toVector()
                            .subtract(meteorLoc.toVector())
                            .normalize()
                            .multiply(1.5);
                    knockback.setY(0.5);
                    entity.setVelocity(entity.getVelocity().add(knockback));
                }

                // Play continuous sound
                world.playSound(meteorLoc, Sound.ENTITY_BLAZE_BURN, 0.5f, 1.0f);
            }
        };
        task.runTaskTimer(plugin, 0L, 2L);
        meteorTasks.put(playerId, task);

        // Auto-end after 8 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeMeteors.containsKey(playerId)) {
                    endMeteorRide(player);
                }
            }
        }.runTaskLater(plugin, 160L); // 8 seconds

        return true;
    }

    private void endMeteorRide(Player player) {
        UUID playerId = player.getUniqueId();
        Fireball meteor = activeMeteors.remove(playerId);
        BukkitRunnable task = meteorTasks.remove(playerId);

        if (task != null) {
            task.cancel();
        }

        if (meteor != null && meteor.isValid()) {
            // Create explosion effect when meteor ride ends
            Location meteorLoc = meteor.getLocation();
            meteorLoc.getWorld().createExplosion(meteorLoc, 2.0f, false, false);
            meteorLoc.getWorld().playSound(meteorLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
            
            // Eject player safely
            if (meteor.getPassengers().contains(player)) {
                meteor.removePassenger(player);
                // Give player a small boost upward to prevent fall damage
                player.setVelocity(new Vector(0, 0.5, 0));
            }
            
            meteor.remove();
        }
    }

    @EventHandler
    public void onMeteorExplode(EntityExplodeEvent event) {
        // Prevent meteor explosions from destroying blocks
        if (event.getEntity() instanceof Fireball) {
            event.blockList().clear();
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Check if player is riding a meteor
        Fireball meteor = activeMeteors.get(playerId);
        if (meteor == null || !meteor.isValid()) return;
        
        // Update meteor direction based on player's view direction
        Vector newDirection = player.getLocation().getDirection().normalize().multiply(1.5);
        meteor.setDirection(newDirection);
    }

    @Override
    public String getName() {
        return ChatColor.RED + "Meteor Ride";
    }

    @Override
    public String getDescription() {
        return ChatColor.GRAY + "Ride on a meteor and crash into enemies to deal massive damage. You control the meteor's direction.";
    }
}
