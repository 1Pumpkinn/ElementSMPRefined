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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Starfire: calls down a barrage of flaming meteors on the area the caster is looking at.
 * <p>
 * Each meteor is a {@link BlockDisplay} spawned in the sky and moved to its landing spot with a single
 * client-interpolated teleport (the spin is a single interpolated transformation), so the server does no
 * per-tick movement work.
 */
public class StarFallAbility extends BaseAbility {
    private static final double RANGE = 40.0;
    private static final double GROUND_SEARCH_DISTANCE = 40.0;

    private static final double ZONE_RADIUS = 6.0;
    private static final int BARRAGE_DURATION_TICKS = 60;
    private static final int SPAWN_INTERVAL_TICKS = 4;
    private static final double SPAWN_HEIGHT = 18.0;
    private static final int FALL_TICKS = 16;
    // The sky spawn must reach the client before the landing teleport, otherwise it has nothing to fall from
    private static final int LAUNCH_DELAY_TICKS = 2;

    private static final Material METEOR_BLOCK = Material.MAGMA_BLOCK;
    private static final float METEOR_MIN_SIZE = 1.0f;
    private static final float METEOR_MAX_SIZE = 1.7f;
    private static final Color METEOR_GLOW = Color.fromRGB(204, 85, 0);
    private static final float TOTAL_SPIN = (float) Math.PI * 1.5f;

    private static final Particle.DustOptions MARKER_DUST = new Particle.DustOptions(Color.fromRGB(255, 120, 20), 1.0f);
    private static final int MARKER_POINTS = 12;
    private static final int ZONE_POINTS = 24;

    private static final double IMPACT_RADIUS = 2.5;
    private static final double IMPACT_DAMAGE = 5.0;
    private static final int IMPACT_FIRE_TICKS = 60;
    private static final double IMPACT_KNOCKBACK = 0.5;
    private static final double IMPACT_KNOCKBACK_UP = 0.35;

    // Hard stop so a barrage can never run forever
    private static final int MAX_TASK_TICKS = BARRAGE_DURATION_TICKS + LAUNCH_DELAY_TICKS + FALL_TICKS + 20;

    // Every live meteor display, so they can be swept up when the plugin shuts down or reloads
    private static final Set<BlockDisplay> ACTIVE_DISPLAYS = new HashSet<>();

    private final ElementSMPRefined plugin;

    public StarFallAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("fire_starfire", ElementType.FIRE, 2, 60, 2, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    /** Removes every meteor still on screen. Call from the plugin's disable hook. */
    public static void removeAll() {
        for (BlockDisplay display : ACTIVE_DISPLAYS) {
            display.remove();
        }
        ACTIVE_DISPLAYS.clear();
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        Location center = findTargetPoint(player);
        if (center == null) {
            return false;
        }

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.6f);
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.6f);
        drawRing(world, center, ZONE_RADIUS, ZONE_POINTS);

        new Barrage(player, context.getTrustManager(), center).runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private final class Barrage extends BukkitRunnable {
        private final Player player;
        private final TrustManager trust;
        private final World world;
        private final Location center;
        private final List<Meteor> meteors = new ArrayList<>();
        private int ticks = 0;

        Barrage(Player player, TrustManager trust, Location center) {
            this.player = player;
            this.trust = trust;
            this.world = center.getWorld();
            this.center = center;
        }

        @Override
        public void run() {
            if (!player.isOnline() || ticks > MAX_TASK_TICKS) {
                stop();
                return;
            }

            // The counter must advance even if something below throws, or the barrage would never time out
            try {
                tick();
            } finally {
                ticks++;
            }
        }

        private void tick() {
            if (ticks <= BARRAGE_DURATION_TICKS && ticks % SPAWN_INTERVAL_TICKS == 0) {
                spawnMeteor();
            }

            meteors.removeIf(this::updateMeteor);

            if (ticks > BARRAGE_DURATION_TICKS && meteors.isEmpty()) {
                stop();
            }
        }

        /** @return true once the meteor is finished and should be dropped from the list */
        private boolean updateMeteor(Meteor meteor) {
            if (!meteor.display.isValid()) {
                meteor.remove();
                if (meteor.launched) {
                    detonate(meteor.impact);
                }
                return true;
            }

            if (!meteor.launched) {
                if (ticks - meteor.spawnTick >= LAUNCH_DELAY_TICKS) {
                    meteor.launch(ticks + FALL_TICKS);
                }
                return false;
            }

            if (ticks >= meteor.landTick) {
                meteor.remove();
                detonate(meteor.impact);
                return true;
            }
            return false;
        }

        private void spawnMeteor() {
            ThreadLocalRandom rng = ThreadLocalRandom.current();

            double angle = rng.nextDouble(0, Math.PI * 2);
            double dist = Math.sqrt(rng.nextDouble()) * ZONE_RADIUS;
            Location skyStart = center.clone().add(Math.cos(angle) * dist, SPAWN_HEIGHT, Math.sin(angle) * dist);

            RayTraceResult ground = world.rayTraceBlocks(
                    skyStart, new Vector(0, -1, 0), SPAWN_HEIGHT + GROUND_SEARCH_DISTANCE,
                    FluidCollisionMode.NEVER, true);
            if (ground == null) {
                return;
            }

            Location impact = ground.getHitPosition().toLocation(world);
            drawRing(world, impact, IMPACT_RADIUS, MARKER_POINTS);
            world.playSound(impact, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.6f, 1.4f);

            meteors.add(Meteor.spawn(impact, ticks, rng));
        }

        private void detonate(Location impact) {
            try {
                explode(player, trust, impact);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Starfire impact failed", e);
            }
        }

        private void stop() {
            meteors.forEach(Meteor::remove);
            meteors.clear();
            cancel();
        }
    }

    private static void explode(Player caster, TrustManager trust, Location impact) {
        World world = impact.getWorld();

        world.spawnParticle(Particle.EXPLOSION, impact, 1, 0, 0, 0, 0, null, true);
        world.spawnParticle(Particle.BLOCK, impact.clone().add(0, 0.3, 0), 12,
                IMPACT_RADIUS / 4, 0.2, IMPACT_RADIUS / 4, 0.1, METEOR_BLOCK.createBlockData(), true);
        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
        world.playSound(impact, Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.9f);

        // Damage is applied manually instead of World#createExplosion to avoid block damage and double hits
        for (LivingEntity entity : impact.getNearbyLivingEntities(IMPACT_RADIUS)) {
            if (entity.equals(caster)) continue;
            if (trust != null && entity instanceof Player other
                    && trust.isTrusted(caster.getUniqueId(), other.getUniqueId())) continue;

            entity.damage(IMPACT_DAMAGE, caster);
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

    /** The first solid block the caster is looking at, or the ground below the end of their reach. */
    private Location findTargetPoint(Player player) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();

        RayTraceResult hit = world.rayTraceBlocks(eye, direction, RANGE, FluidCollisionMode.NEVER, true);
        if (hit != null) {
            return hit.getHitPosition().toLocation(world);
        }

        Location end = eye.clone().add(direction.clone().multiply(RANGE));
        RayTraceResult down = world.rayTraceBlocks(
                end, new Vector(0, -1, 0), GROUND_SEARCH_DISTANCE, FluidCollisionMode.NEVER, true);
        return down != null ? down.getHitPosition().toLocation(world) : null;
    }

    private static void drawRing(World world, Location center, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2 * i) / points;
            Location p = center.clone().add(Math.cos(a) * radius, 0.15, Math.sin(a) * radius);
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, MARKER_DUST, true);
        }
    }

    /** Display rotation pivots on the block's corner; offset by the rotated half-extent to tumble around the centre. */
    private static Transformation centred(Quaternionf rotation, float size) {
        Vector3f translation = new Vector3f(size / 2f, size / 2f, size / 2f).rotate(rotation).negate();
        return new Transformation(translation, rotation, new Vector3f(size, size, size), new Quaternionf());
    }

    private static final class Meteor {
        final BlockDisplay display;
        final Location impact;
        final Location landing;
        final float size;
        final Quaternionf endRotation;
        final int spawnTick;
        boolean launched = false;
        int landTick;

        private Meteor(BlockDisplay display, Location impact, Location landing, float size,
                       Quaternionf endRotation, int spawnTick) {
            this.display = display;
            this.impact = impact;
            this.landing = landing;
            this.size = size;
            this.endRotation = endRotation;
            this.spawnTick = spawnTick;
        }

        static Meteor spawn(Location impact, int spawnTick, ThreadLocalRandom rng) {
            float size = rng.nextFloat(METEOR_MIN_SIZE, METEOR_MAX_SIZE);

            Quaternionf startRot = new Quaternionf().rotateXYZ(
                    rng.nextFloat(0f, (float) (Math.PI * 2)),
                    rng.nextFloat(0f, (float) (Math.PI * 2)),
                    rng.nextFloat(0f, (float) (Math.PI * 2)));
            Quaternionf endRot = new Quaternionf(startRot).rotateXYZ(
                    TOTAL_SPIN * rng.nextFloat(0.6f, 1.0f),
                    TOTAL_SPIN * rng.nextFloat(0.4f, 0.8f),
                    TOTAL_SPIN * rng.nextFloat(0.2f, 0.6f));

            Location landing = impact.clone().add(0, size / 2.0, 0);
            Location skyLoc = landing.clone().add(0, SPAWN_HEIGHT, 0);
            BlockDisplay display = impact.getWorld().spawn(skyLoc, BlockDisplay.class, d -> {
                d.setBlock(METEOR_BLOCK.createBlockData());
                d.setBrightness(new Display.Brightness(15, 15));
                d.setGlowing(true);
                d.setGlowColorOverride(METEOR_GLOW);
                d.setViewRange(2.0f);
                d.setPersistent(false);
                d.setTeleportDuration(FALL_TICKS);
                d.setTransformation(centred(startRot, size));
            });
            ACTIVE_DISPLAYS.add(display);

            return new Meteor(display, impact, landing, size, endRot, spawnTick);
        }

        /**
         * Starts the fall. Position interpolation runs from the entity's own last position, so it still works when the
         * spawn and this update reach the client in the same batch (e.g. after a lag spike); a transformation-based
         * fall would snap to its end state instead.
         */
        void launch(int landTick) {
            launched = true;
            this.landTick = landTick;
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(FALL_TICKS);
            display.setTransformation(centred(endRotation, size));
            display.teleport(landing);
        }

        void remove() {
            display.remove();
            ACTIVE_DISPLAYS.remove(display);
        }
    }

    @Override
    public String getName() {
        return ChatColor.RED + "Starfall";
    }

    @Override
    public String getDescription() {
        return ChatColor.GRAY + "Call down a barrage of meteors on the area you're looking at";
    }
}