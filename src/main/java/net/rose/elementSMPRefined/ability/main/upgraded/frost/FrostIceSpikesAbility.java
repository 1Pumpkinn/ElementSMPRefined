package net.rose.elementSMPRefined.ability.main.upgraded.frost;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.util.damage.TrueDamage;
import net.rose.elementSMPRefined.util.visual.model.IceSpikeVisual;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ice Spike - Frost ability 1. The caster stays on the ground: a short wind-up where frost
 * gathers around their feet, then a stomp. The impact cracks the ground and rings of small ice
 * spikes burst up around the caster, sweeping around the circle like it is unzipping. Anything a
 * spike comes up under is hurt, thrown up and slowed.
 *
 * <p>The spikes are Blockbench models (see {@link IceSpikeVisual}); no real blocks are ever
 * changed, so there is nothing to clean up if the server stops mid-cast.</p>
 */
public class FrostIceSpikesAbility extends BaseAbility {

    // Wind-up before the stomp (caster does not leave the ground)
    private static final int WINDUP_TICKS = 10;
    private static final double WINDUP_RADIUS = 2.5;

    // Rings of spikes: radius and spike count per ring, nearest ring first
    private static final double[] RING_RADII = {2.0, 3.5, 5.0};
    private static final int[] RING_SPIKES = {8, 13, 18};
    private static final int RING_DELAY_TICKS = 2;      // gap between one ring starting and the next
    private static final int SWEEP_TICKS = 8;           // a ring unzips around the circle over this long
    private static final double SPIKE_MAX_HEIGHT = 1.9; // inner ring, tallest point of the model, in blocks
    private static final double SPIKE_MIN_HEIGHT = 1.2; // outer ring, in blocks

    // Hit
    private static final double DAMAGE = 5.0;
    private static final double LAUNCH_UP = 0.8;
    private static final double HIT_RADIUS_XZ = 1.2;
    private static final double HIT_RADIUS_Y = 2.0;
    private static final int SLOW_TICKS = 60;
    private static final int SLOW_AMPLIFIER = 1;       // Slowness II

    private static final int SCAN_UP = 2;
    private static final int SCAN_DOWN = 4;

    private final ElementSMPRefined plugin;
    private final Set<UUID> activeCasters = new HashSet<>();

    public FrostIceSpikesAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("frost_ice_spikes", ElementType.FROST, 1, 30, 1, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player caster = context.getPlayer();
        TrustManager trust = context.getTrustManager();
        UUID casterId = caster.getUniqueId();

        if (!activeCasters.add(casterId)) return false;

        World world = caster.getWorld();
        world.playSound(caster.getLocation(), Sound.BLOCK_POWDER_SNOW_BREAK, 1.2f, 0.6f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!caster.isOnline() || caster.isDead()) {
                    activeCasters.remove(casterId);
                    cancel();
                    return;
                }

                Location feet = caster.getLocation();

                if (ticks < WINDUP_TICKS) {
                    // Frost gathers inward toward the caster's feet.
                    double shrink = 1.0 - (double) ticks / WINDUP_TICKS;
                    double r = WINDUP_RADIUS * shrink + 0.3;
                    for (int i = 0; i < 6; i++) {
                        double angle = (Math.PI * 2 * i / 6) + ticks * 0.5;
                        world.spawnParticle(Particle.SNOWFLAKE,
                                feet.clone().add(Math.cos(angle) * r, 0.15, Math.sin(angle) * r),
                                1, 0.05, 0.05, 0.05, 0.0, null, true);
                    }
                    if (ticks % 3 == 0) {
                        world.playSound(feet, Sound.BLOCK_GLASS_BREAK, 0.6f, 0.5f + ticks * 0.06f);
                    }
                    ticks++;
                    return;
                }

                cancel();
                activeCasters.remove(casterId);
                caster.swingMainHand();
                startSpikes(caster, trust, feet);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    /** The stomp: impact burst, then the rings of spikes sweeping around the caster. */
    private void startSpikes(Player caster, TrustManager trust, Location origin) {
        World world = origin.getWorld();
        world.spawnParticle(Particle.SNOWFLAKE, origin.clone().add(0, 0.3, 0), 60, 1.0, 0.2, 1.0, 0.1, null, true);
        world.spawnParticle(Particle.CLOUD, origin.clone().add(0, 0.3, 0), 20, 0.8, 0.1, 0.8, 0.05, null, true);
        world.spawnParticle(Particle.BLOCK, origin.clone().add(0, 0.2, 0), 30, 0.8, 0.1, 0.8, 0.0,
                Material.PACKED_ICE.createBlockData(), true);
        world.playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.5f);
        world.playSound(origin, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.6f);

        final Set<UUID> alreadyHit = new HashSet<>();
        final int startY = origin.getBlockY();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        // Every ring starts unzipping from the same random angle so the sweep reads as one motion.
        final double sweepStart = rnd.nextDouble() * Math.PI * 2;

        for (int ring = 0; ring < RING_RADII.length; ring++) {
            int count = RING_SPIKES[ring];
            double radius = RING_RADII[ring];

            // Taller near the centre, shorter toward the edge.
            double t = RING_RADII.length == 1 ? 0.0 : (double) ring / (RING_RADII.length - 1);
            double ringHeight = SPIKE_MAX_HEIGHT + (SPIKE_MIN_HEIGHT - SPIKE_MAX_HEIGHT) * t;

            for (int i = 0; i < count; i++) {
                double fraction = (double) i / count;
                // A little jitter in angle and distance keeps the ring from looking machine-made.
                double angle = sweepStart + Math.PI * 2 * fraction + (rnd.nextDouble() - 0.5) * 0.25;
                double r = radius + (rnd.nextDouble() - 0.5) * 0.5;
                double height = ringHeight * (0.8 + rnd.nextDouble() * 0.4);
                long delay = (long) ring * RING_DELAY_TICKS + Math.round(fraction * SWEEP_TICKS);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Location point = origin.clone().add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
                    Block ground = findGround(point, startY);
                    if (ground == null) return;

                    Location base = new Location(world, point.getX(), ground.getY() + 1, point.getZ());
                    IceSpikeVisual.spawn(plugin, base, height);
                    hitNearby(caster, trust, base, alreadyHit);
                }, delay);
            }

            world.playSound(origin.clone().add(radius, 0, 0), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 0.6f);
        }
    }

    /** Topmost solid, non-special block with open air above it near the given column. */
    private Block findGround(Location point, int startY) {
        World w = point.getWorld();
        int x = point.getBlockX();
        int z = point.getBlockZ();
        for (int y = startY + SCAN_UP; y >= startY - SCAN_DOWN; y--) {
            Block b = w.getBlockAt(x, y, z);
            if (!b.getType().isSolid()) continue;
            if (!b.getRelative(0, 1, 0).isPassable()) continue;
            if (b.getType().getHardness() < 0) return null;          // bedrock, barriers, etc.
            if (b.getState(false) instanceof TileState) return null; // chests, spawners, signs
            return b;
        }
        return null;
    }

    private void hitNearby(Player caster, TrustManager trust, Location at, Set<UUID> alreadyHit) {
        for (LivingEntity entity : at.getNearbyLivingEntities(HIT_RADIUS_XZ, HIT_RADIUS_Y, HIT_RADIUS_XZ)) {
            if (entity.equals(caster)) continue;
            if (entity instanceof ArmorStand) continue;
            if (trust != null && entity instanceof Player other
                    && trust.isTrusted(caster.getUniqueId(), other.getUniqueId())) continue;
            if (!alreadyHit.add(entity.getUniqueId())) continue;

            // Damage first: it applies its own knockback, which would otherwise
            // overwrite the launch if we set velocity before it.
            TrueDamage.of(DAMAGE).attacker(caster).ability(getName()).apply(entity);

            entity.setVelocity(new Vector(0, LAUNCH_UP, 0));
            entity.setFreezeTicks(entity.getMaxFreezeTicks());
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, SLOW_TICKS, SLOW_AMPLIFIER, false, true, true));

            entity.getWorld().spawnParticle(Particle.SNOWFLAKE, entity.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05, null, true);
        }
    }

    @Override
    public String getName() {
        return ChatColor.AQUA + "Ice Spikes";
    }

    @Override
    public String getDescription() {
        return ChatColor.GRAY + "Cast Ice Spikes after a short wind-up rings of ice spikes cast around you that damage, launch and slow entities.";
    }
}
