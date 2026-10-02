package net.rose.elementSMPRefined.ability.main.basic.fire;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fire ability2: Starfire.
 * <p>
 * Calls down a barrage of flaming meteors on whatever the caster is looking at. Each meteor is a
 * glowing, tumbling magma block ({@link BlockDisplay}) that falls from the sky and detonates on
 * impact, damaging and igniting anything caught in the blast.
 * <p>
 * Lag note: each meteor's whole fall is handed to the client in a single update - one transformation
 * update interpolated over the full fall time (the entity stays at its landing spot and only its
 * translation offset animates) - so the client animates the motion and spin smoothly by itself. The server does no per-tick work on a meteor while it is falling
 * (only a tick counter to know when it lands), which is what removes the stutter that per-tick
 * <p>
 */
public class StarfireAbility extends BaseAbility {
    // Targeting
    private static final double RANGE = 40.0;
    private static final double GROUND_SEARCH_DISTANCE = 40.0; // how far down to look for ground if the cast hit nothing solid

    // Barrage shape
    private static final double ZONE_RADIUS = 6.0;
    private static final int BARRAGE_DURATION_TICKS = 60; // 3s of meteors being spawned
    private static final int SPAWN_INTERVAL_TICKS = 4; // one new meteor every 4 ticks -> ~15 meteors
    private static final double SPAWN_HEIGHT = 18.0; // blocks above the impact point
    private static final int FALL_TICKS = 16; // how long every meteor takes to fall (client-interpolated)
    private static final int LAUNCH_DELAY_TICKS = 2; // ticks between spawning the display (in the sky) and starting its fall

    // Meteor visuals
    private static final Material METEOR_BLOCK = Material.OCHRE_FROGLIGHT;
    private static final float METEOR_MIN_SIZE = 1.0f;
    private static final float METEOR_MAX_SIZE = 1.7f;
    private static final Color METEOR_GLOW = Color.fromRGB(255, 255, 255);
    private static final float TOTAL_SPIN = (float) Math.PI * 1.5f; // total tumble over the fall, in radians

    // Markers (dust, not flame)
    private static final Particle.DustOptions MARKER_DUST = new Particle.DustOptions(Color.fromRGB(255, 120, 20), 1.0f);
    private static final int MARKER_POINTS = 12;
    private static final int ZONE_POINTS = 24;

    // Impact
    private static final double IMPACT_RADIUS = 2.5;
    private static final double IMPACT_DAMAGE = 5.0; // 2.5 hearts per meteor
    private static final int IMPACT_FIRE_TICKS = 60; // 3s alight
    private static final double IMPACT_KNOCKBACK = 0.5;
    private static final double IMPACT_KNOCKBACK_UP = 0.35;

    // Safety cap so the task can never run forever
    private static final int MAX_TASK_TICKS = BARRAGE_DURATION_TICKS + LAUNCH_DELAY_TICKS + FALL_TICKS + 20;

    private final ElementSMPRefined plugin;

    public StarfireAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("fire_starfire", ElementType.FIRE, 2, 60, 2, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        World world = player.getWorld();
        TrustManager trust = context.getTrustManager();

        Location center = findTargetPoint(player);
        if (center == null) {
            return false;
        }

        world.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.6f);
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.6f);

        // One-off outline of the danger zone
        drawRing(world, center, ZONE_RADIUS, ZONE_POINTS);

        new BukkitRunnable() {
            final List<Meteor> meteors = new ArrayList<>();
            int ticks = 0;

            /** Every exit path calls cancel(), so a meteor entity can never outlive the ability. */
            @Override
            public synchronized void cancel() throws IllegalStateException {
                for (Meteor m : meteors) {
                    m.display.remove();
                }
                meteors.clear();
                super.cancel();
            }

            @Override
            public void run() {
                if (!player.isOnline() || ticks > MAX_TASK_TICKS) {
                    cancel();
                    return;
                }

                // Spawn a new meteor on the interval until the barrage ends
                if (ticks <= BARRAGE_DURATION_TICKS && ticks % SPAWN_INTERVAL_TICKS == 0) {
                    spawnMeteor();
                }

                Iterator<Meteor> it = meteors.iterator();
                while (it.hasNext()) {
                    Meteor meteor = it.next();

                    // Hand the whole fall to the client in one go, but only a couple of ticks AFTER
                    // the spawn. If the start and end transformations go out in the same tick the
                    // client never renders the start state, so there's nothing to interpolate from
                    // and the block just appears on the ground.
                    if (!meteor.launched) {
                        if (ticks - meteor.spawnTick >= LAUNCH_DELAY_TICKS) {
                            meteor.launch();
                            meteor.landTick = ticks + FALL_TICKS;
                        }
                        continue;
                    }

                    if (!meteor.display.isValid() || ticks >= meteor.landTick) {
                        meteor.display.remove();
                        detonate(player, trust, meteor.impact);
                        it.remove();
                    }
                }

                // Barrage over and every meteor has landed
                if (ticks > BARRAGE_DURATION_TICKS && meteors.isEmpty()) {
                    cancel();
                    return;
                }

                ticks++;
            }

            private void spawnMeteor() {
                ThreadLocalRandom rng = ThreadLocalRandom.current();

                // Uniform random point inside the zone circle
                double angle = rng.nextDouble(0, Math.PI * 2);
                double dist = Math.sqrt(rng.nextDouble()) * ZONE_RADIUS;
                double x = center.getX() + Math.cos(angle) * dist;
                double z = center.getZ() + Math.sin(angle) * dist;

                // Find the actual ground under that column so meteors land on terrain instead of
                // clipping into hills or hovering over pits
                Location skyStart = new Location(world, x, center.getY() + SPAWN_HEIGHT, z);
                RayTraceResult ground = world.rayTraceBlocks(
                        skyStart, new Vector(0, -1, 0), SPAWN_HEIGHT + GROUND_SEARCH_DISTANCE,
                        FluidCollisionMode.NEVER, true);
                if (ground == null) {
                    return; // open void/column with nothing under it - skip this one
                }

                Location impact = ground.getHitPosition().toLocation(world);
                float size = rng.nextFloat(METEOR_MIN_SIZE, METEOR_MAX_SIZE);
                // The entity itself stays at the landing spot (resting on the ground) for its whole
                // life; the fall is a client-side translation offset that interpolates from
                // SPAWN_HEIGHT blocks up back down to zero.
                Location spawnLoc = impact.clone().add(0, size / 2.0, 0);

                // Random tumble: a starting orientation and a different one to spin towards
                Quaternionf startRot = new Quaternionf().rotateXYZ(
                        rng.nextFloat(0f, (float) (Math.PI * 2)),
                        rng.nextFloat(0f, (float) (Math.PI * 2)),
                        rng.nextFloat(0f, (float) (Math.PI * 2)));
                Quaternionf endRot = new Quaternionf(startRot).rotateXYZ(
                        TOTAL_SPIN * rng.nextFloat(0.6f, 1.0f),
                        TOTAL_SPIN * rng.nextFloat(0.4f, 0.8f),
                        TOTAL_SPIN * rng.nextFloat(0.2f, 0.6f));

                // Small warning marker where it will land
                drawRing(world, impact, IMPACT_RADIUS, MARKER_POINTS);
                world.playSound(impact, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.6f, 1.4f);

                BlockDisplay display = world.spawn(spawnLoc, BlockDisplay.class, d -> {
                    d.setBlock(METEOR_BLOCK.createBlockData());
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setGlowing(true);
                    d.setGlowColorOverride(METEOR_GLOW);
                    d.setViewRange(2.0f);
                    d.setPersistent(false);
                    d.setTransformation(centred(startRot, size, (float) SPAWN_HEIGHT));
                });

                meteors.add(new Meteor(display, impact, size, endRot, ticks));
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    /**
     * Display rotation pivots around the block's corner, so offset the translation by the rotated
     * half-extent to make the block tumble around its own centre instead.
     */
    private static Transformation centred(Quaternionf rotation, float size, float heightOffset) {
        Vector3f translation = new Vector3f(size / 2f, size / 2f, size / 2f).rotate(rotation).negate();
        translation.y += heightOffset;
        return new Transformation(translation, rotation, new Vector3f(size, size, size), new Quaternionf());
    }

    /** Where the barrage is centred: the first solid block the caster is looking at, or the ground below the end of their reach. */
    private Location findTargetPoint(Player player) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();

        RayTraceResult hit = world.rayTraceBlocks(eye, direction, RANGE, FluidCollisionMode.NEVER, true);
        if (hit != null) {
            return hit.getHitPosition().toLocation(world);
        }

        // Nothing solid in reach (aiming at the sky / far out) - drop straight down from the end of the ray
        Location end = eye.clone().add(direction.clone().multiply(RANGE));
        RayTraceResult down = world.rayTraceBlocks(
                end, new Vector(0, -1, 0), GROUND_SEARCH_DISTANCE, FluidCollisionMode.NEVER, true);
        return down != null ? down.getHitPosition().toLocation(world) : null;
    }

    private void detonate(Player player, TrustManager trust, Location impact) {
        World world = impact.getWorld();

        // Short, restrained burst - one explosion puff plus a little magma debris
        world.spawnParticle(Particle.EXPLOSION, impact, 1, 0, 0, 0, 0, null, true);
        world.spawnParticle(Particle.BLOCK, impact.clone().add(0, 0.3, 0), 12,
                IMPACT_RADIUS / 4, 0.2, IMPACT_RADIUS / 4, 0.1, METEOR_BLOCK.createBlockData(), true);
        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
        world.playSound(impact, Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.9f);

        // Cosmetic boom only - deliberately not World#createExplosion, so the damage is exactly
        // what's applied below (no block damage, no double-hit, no blast-protection surprises).
        for (LivingEntity entity : impact.getNearbyLivingEntities(IMPACT_RADIUS)) {
            if (entity.equals(player)) continue;
            if (trust != null && entity instanceof Player other
                    && trust.isTrusted(player.getUniqueId(), other.getUniqueId())) continue;

            entity.damage(IMPACT_DAMAGE, player);
            entity.setFireTicks(Math.max(entity.getFireTicks(), IMPACT_FIRE_TICKS));

            Vector knockback = entity.getLocation().toVector().subtract(impact.toVector());
            if (knockback.lengthSquared() < 1.0E-4) {
                knockback = new Vector(1, 0, 0);
            }
            knockback = knockback.normalize().multiply(IMPACT_KNOCKBACK);
            knockback.setY(IMPACT_KNOCKBACK_UP);
            entity.setVelocity(entity.getVelocity().add(knockback));
        }
    }

    private void drawRing(World world, Location centre, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2 * i) / points;
            Location p = centre.clone().add(Math.cos(a) * radius, 0.15, Math.sin(a) * radius);
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, MARKER_DUST, true);
        }
    }

    /** A single meteor: its block display, where it lands, and the orientation it tumbles towards. */
    private static final class Meteor {
        final BlockDisplay display;
        final Location impact;
        final float size;
        final Quaternionf endRotation;
        final int spawnTick;
        boolean launched = false;
        int landTick;

        Meteor(BlockDisplay display, Location impact, float size, Quaternionf endRotation, int spawnTick) {
            this.spawnTick = spawnTick;
            this.display = display;
            this.impact = impact;
            this.size = size;
            this.endRotation = endRotation;
        }

        /**
         * Starts the whole fall + spin on the client with a single transformation update: the
         * translation offset interpolates from SPAWN_HEIGHT back to 0 while the rotation tumbles.
         * No teleporting involved - the entity never moves.
         */
        void launch() {
            launched = true;

            display.setInterpolationDelay(0);
            display.setInterpolationDuration(FALL_TICKS);
            display.setTransformation(centred(endRotation, size, 0f));
        }
    }

    @Override
    public String getName() {
        return ChatColor.RED + "Starfire";
    }

    @Override
    public String getDescription() {
        return ChatColor.GRAY + "Call down a barrage of flaming meteors on the area you're looking at. "
                + "Each one marks where it will land, then crashes down - damaging and igniting everything nearby.";
    }
}