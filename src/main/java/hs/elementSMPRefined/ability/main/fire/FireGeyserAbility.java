package hs.elementSMPRefined.ability.main.fire;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.API.element.ElementContext;
import hs.elementSMPRefined.API.ability.BaseAbility;
import org.bukkit.plugin.java.JavaPlugin;
import hs.elementSMPRefined.managers.ManaManager;
import hs.elementSMPRefined.managers.TrustManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Fire Geyser - Erupts a ring of lava geysers around the caster,
 * dealing damage and launching nearby entities and players into the air.
 */
public class FireGeyserAbility extends BaseAbility {
    private final ElementSMPRefined plugin;

    // Ring configuration
    private static final int GEYSER_COUNT = 6;
    private static final double RING_RADIUS = 4.0;
    private static final double LAUNCH_RADIUS = 5.5; // radius around caster affected by all geysers combined

    public FireGeyserAbility(JavaPlugin plugin) {
        super("fire_geyser", 50, 8, 1);
        this.plugin = (ElementSMPRefined) plugin;
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

        World world = player.getWorld();
        Location center = player.getLocation();

        // Build a ring of geyser eruption points around the caster, snapped to the ground.
        List<Location> geyserLocations = new ArrayList<>(GEYSER_COUNT);
        for (int i = 0; i < GEYSER_COUNT; i++) {
            double angle = (2 * Math.PI / GEYSER_COUNT) * i;
            double x = center.getX() + RING_RADIUS * Math.cos(angle);
            double z = center.getZ() + RING_RADIUS * Math.sin(angle);
            Location groundLoc = findGroundLocation(world, x, center.getY(), z);
            geyserLocations.add(groundLoc);
        }

        world.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.8f);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.9f);
        world.playSound(center, Sound.BLOCK_LAVA_EXTINGUISH, 1.2f, 0.6f);

        double damage = 6.0;
        double knockupStrength = 1.6;
        double perGeyserRadius = 2.0;

        BlockData lavaData = Material.LAVA.createBlockData();

        new BukkitRunnable() {
            int tick = 0;
            final int duration = 24; // slightly longer eruption for the bigger effect
            boolean burstPlayed = false;

            @Override
            public void run() {
                if (tick >= duration || !player.isOnline()) {
                    cancel();
                    return;
                }

                // Initial eruption burst, once, across every geyser point.
                if (!burstPlayed) {
                    for (Location geyser : geyserLocations) {
                        world.spawnParticle(Particle.EXPLOSION, geyser.clone().add(0, 0.2, 0), 1);
                        world.spawnParticle(Particle.BLOCK, geyser.clone().add(0, 0.3, 0), 25, 0.4, 0.1, 0.4, 0.1, lavaData);
                    }
                    burstPlayed = true;
                }

                for (Location geyser : geyserLocations) {
                    // Rising column of lava/fire particles.
                    for (int y = 0; y < 6; y++) {
                        double yOffset = y * 0.75;
                        Location particleLoc = geyser.clone().add(0, yOffset, 0);

                        double spread = 0.25 + (y * 0.12);
                        for (int i = 0; i < 3; i++) {
                            double offsetX = (Math.random() - 0.5) * spread;
                            double offsetZ = (Math.random() - 0.5) * spread;
                            Location offsetLoc = particleLoc.clone().add(offsetX, 0, offsetZ);

                            world.spawnParticle(Particle.LAVA, offsetLoc, 1, 0.05, 0.1, 0.05, 0);
                            world.spawnParticle(Particle.DRIPPING_LAVA, offsetLoc, 2, 0.1, 0.15, 0.1, 0);
                            world.spawnParticle(Particle.FLAME, offsetLoc, 4, 0.1, 0.25, 0.1, 0.04, null, true);
                            world.spawnParticle(Particle.LARGE_SMOKE, offsetLoc, 1, 0.1, 0.2, 0.1, 0.01, null, true);
                        }
                    }

                    // Base scorch/smoke ring so the ground reads as molten.
                    world.spawnParticle(Particle.LARGE_SMOKE, geyser.clone().add(0, 0.1, 0), 6, 0.5, 0.05, 0.5, 0.01, null, true);
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Damage and launch everything caught around the caster (covers the whole ring at once).
        for (LivingEntity entity : center.getNearbyLivingEntities(LAUNCH_RADIUS)) {
            if (entity.equals(player)) continue;
            if (entity instanceof Player other && trust.isTrusted(player.getUniqueId(), other.getUniqueId())) continue;

            entity.damage(damage, player);

            Vector knockup = new Vector(0, knockupStrength, 0);
            Vector direction = entity.getLocation().toVector()
                    .subtract(center.toVector())
                    .setY(0)
                    .normalize()
                    .multiply(0.35);
            entity.setVelocity(entity.getVelocity().add(knockup).add(direction));

            entity.setFireTicks(60); // Set on fire for 3 seconds
        }

        return true;
    }

    /**
     * Raycasts downward from a starting height to find the ground surface at the given x/z,
     * so ring geysers erupt correctly on slopes and uneven terrain instead of floating/clipping.
     */
    private Location findGroundLocation(World world, double x, double startY, double z) {
        Location probe = new Location(world, x, startY + 3, z);
        for (int i = 0; i < 8; i++) {
            Block block = probe.getBlock();
            if (!block.isPassable() || block.getType().isSolid()) {
                return block.getLocation().add(0.5, 1, 0.5);
            }
            probe.add(0, -1, 0);
        }
        // Fallback: couldn't find solid ground within range, use the original height.
        return new Location(world, x, startY, z).add(0.5, 0, 0.5);
    }

    @Override
    public String getName() {
        return ChatColor.RED + "Fire Geyser";
    }

    @Override
    public String getDescription() {
        return ChatColor.GRAY + "Erupt a ring of lava geysers around you, dealing damage and launching nearby enemies into the air.";
    }
}