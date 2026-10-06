package net.rose.elementSMPRefined.ability.main.upgraded.lava;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.util.damage.TrueDamage;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
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
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;


/**
 * Magma Eruption - look at an enemy and the ground under them cracks open. After a short,
 * dodgeable warning the spot erupts: everything in the blast takes damage, is set on fire
 * and is thrown into the air.
 * <p>
 * The eruption is a ring of magma {@link BlockDisplay} spires around a rising particle column
 * (dust, flame, a spiral and a ground shockwave). It never changes real blocks, so nothing needs
 * to be restored afterwards.
 */
public class MagmaEruptionAbility extends BaseAbility {

    private static final double RANGE = 30.0;
    private static final double RAY_SIZE = 0.8;
    /** Warning time before the blast. The spot is locked when cast, so moving away dodges it. */
    private static final int WARNING_TICKS = 14;
    private static final double RADIUS = 2.5;
    private static final double DAMAGE = 7.0;
    private static final double LAUNCH_VELOCITY = 1.0;
    private static final int BURN_TICKS = 80;

    private static final Component NO_TARGET = Component.text("No target in sight!", NamedTextColor.RED);
    private static final BlockData MAGMA = Material.MAGMA_BLOCK.createBlockData();
    /** Dark rock mixed in among the magma spires so the ring doesn't look uniform. */
    private static final BlockData CRUST = Material.BLACKSTONE.createBlockData();

    /** Every live spire tier, so plugin disable can sweep up anything still on screen. */
    private static final Set<BlockDisplay> ACTIVE_PILLARS = new HashSet<>();


    private final ElementSMPRefined plugin;

    public MagmaEruptionAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("lava_magma_eruption", ElementType.LAVA, 1, 20, 1, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        TrustManager trust = context.getTrustManager();

        LivingEntity target = findTarget(player, trust);
        if (target == null) {
            player.sendMessage(NO_TARGET);
            return false; // failed cast stays free (no cooldown)
        }

        startEruption(player, trust, groundBelow(target.getLocation()));
        return true;
    }

    /** First valid living entity under the crosshair, not hidden behind a wall. */
    private LivingEntity findTarget(Player player, TrustManager trust) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();

        RayTraceResult blocks = world.rayTraceBlocks(eye, dir, RANGE, FluidCollisionMode.NEVER, true);
        double maxDistance = blocks == null ? RANGE : blocks.getHitPosition().distance(eye.toVector());

        RayTraceResult hit = world.rayTraceEntities(eye, dir, maxDistance, RAY_SIZE,
                e -> e instanceof LivingEntity living && LavaTargets.isValid(player, living, trust));
        return hit == null ? null : (LivingEntity) hit.getHitEntity();
    }

    /** Snaps to the floor so an airborne target still gets an eruption on the ground below. */
    private Location groundBelow(Location from) {
        World world = from.getWorld();
        RayTraceResult down = world.rayTraceBlocks(from.clone().add(0, 0.5, 0), new Vector(0, -1, 0),
                12.0, FluidCollisionMode.NEVER, true);
        Location ground = down == null ? from.clone() : down.getHitPosition().toLocation(world);
        ground.add(0, 0.05, 0);
        ground.setYaw(0f);
        ground.setPitch(0f);
        return ground;
    }

    private void startEruption(Player caster, TrustManager trust, Location center) {
        World world = center.getWorld();
        world.playSound(center, Sound.BLOCK_LAVA_AMBIENT, 1.5f, 0.7f);
        world.playSound(center, Sound.BLOCK_LAVA_POP, 1.2f, 0.6f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= WARNING_TICKS) {
                    erupt(caster, trust, center);
                    cancel();
                    return;
                }

                // Warning: a glowing ring at the blast radius that tightens as the timer runs out.
                double progress = (double) ticks / WARNING_TICKS;
                double ringRadius = RADIUS * (1.0 - 0.25 * progress);
                for (int i = 0; i < 24; i++) {
                    double angle = i * (Math.PI * 2 / 24);
                    Location p = center.clone().add(Math.cos(angle) * ringRadius, 0.1, Math.sin(angle) * ringRadius);
                    world.spawnParticle(Particle.FLAME, p, 1, 0, 0, 0, 0.0, null, true);
                }
                if (ticks % 3 == 0) {
                    world.spawnParticle(Particle.LAVA, center.clone().add(0, 0.2, 0), 3, RADIUS * 0.5, 0.1, RADIUS * 0.5, 0.0, null, true);
                    world.spawnParticle(Particle.BLOCK, center.clone().add(0, 0.2, 0), 8, RADIUS * 0.4, 0.05, RADIUS * 0.4, 0.0, MAGMA, true);
                }
                if (ticks % 5 == 0) {
                    world.playSound(center, Sound.BLOCK_LAVA_POP, 0.8f, 0.8f + (float) progress * 0.6f);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void erupt(Player caster, TrustManager trust, Location center) {
        World world = center.getWorld();
        Location mid = center.clone().add(0, 1.0, 0);

        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.6f);
        world.playSound(center, Sound.ITEM_FIRECHARGE_USE, 1.4f, 0.5f);
        world.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.6f);

        world.spawnParticle(Particle.LAVA, mid, 40, 0.8, 1.2, 0.8, 0.0, null, true);
        world.spawnParticle(Particle.FLAME, mid, 70, 0.6, 1.6, 0.6, 0.12, null, true);
        world.spawnParticle(Particle.SMOKE, mid, 30, 0.7, 1.5, 0.7, 0.05, null, true);
        world.spawnParticle(Particle.BLOCK, mid, 60, 0.9, 1.4, 0.9, 0.0, MAGMA, true);

        spawnPillar(center);

        for (LivingEntity e : mid.getNearbyLivingEntities(RADIUS, 2.0, RADIUS)) {
            if (!LavaTargets.isValid(caster, e, trust)) continue;

            TrueDamage.of(DAMAGE).attacker(caster).ignoreIFrames(false).apply(e);
            e.setFireTicks(BURN_TICKS);
            e.setVelocity(new Vector(0, LAUNCH_VELOCITY, 0));
        }
    }

    // ---- particle eruption visual --------------------------------------------------------

    private static final Particle.DustOptions CORE_DUST = new Particle.DustOptions(Color.fromRGB(255, 120, 20), 1.8f);
    private static final Particle.DustOptions EDGE_DUST = new Particle.DustOptions(Color.fromRGB(200, 40, 0), 1.4f);
    private static final Particle.DustTransition RISING_DUST =
            new Particle.DustTransition(Color.fromRGB(255, 170, 40), Color.fromRGB(110, 20, 0), 1.6f);

    private static final double COLUMN_HEIGHT = 4.5;
    /** Ticks the column takes to shoot up. */
    private static final int RISE_TICKS = 5;
    private static final int ERUPTION_TICKS = 16;
    private static final double COLUMN_RADIUS = 0.9;

    /**
     * The centre of the eruption, drawn with particles only.
     * A dense dust column shoots upward with two spirals twisting around it, a shockwave ring races
     * out along the ground, and lava drips rain back down once the column peaks.
     */
    private void spawnColumn(Location center) {
        World world = center.getWorld();

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= ERUPTION_TICKS) {
                    cancel();
                    return;
                }

                // Shockwave ring racing out to the blast radius.
                if (t < 7) {
                    double ringRadius = RADIUS * (t + 1) / 7.0;
                    for (int i = 0; i < 28; i++) {
                        double a = i * (Math.PI * 2 / 28);
                        Location p = center.clone().add(Math.cos(a) * ringRadius, 0.15, Math.sin(a) * ringRadius);
                        world.spawnParticle(Particle.DUST, p, 1, 0.05, 0.05, 0.05, 0.0, EDGE_DUST, true);
                        if (i % 4 == 0) world.spawnParticle(Particle.FLAME, p, 1, 0, 0.05, 0, 0.02, null, true);
                    }
                }

                // Column: grows to full height over RISE_TICKS, then holds and thins out.
                double rise = Math.min(1.0, (t + 1) / (double) RISE_TICKS);
                double height = COLUMN_HEIGHT * rise;
                double fade = t < 9 ? 1.0 : Math.max(0.0, 1.0 - (t - 9) / 7.0);

                for (double y = 0; y <= height; y += 0.4) {
                    double taper = 1.0 - 0.55 * (y / COLUMN_HEIGHT);
                    double r = COLUMN_RADIUS * taper;
                    Location level = center.clone().add(0, y, 0);

                    int dust = Math.max(1, (int) Math.round(3 * fade));
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION, level, dust, r * 0.5, 0.15, r * 0.5, 0.0, RISING_DUST, true);
                    world.spawnParticle(Particle.DUST, level, 1, r * 0.3, 0.1, r * 0.3, 0.0, CORE_DUST, true);
                    if (fade > 0.3) {
                        world.spawnParticle(Particle.FLAME, level, 1, r * 0.4, 0.1, r * 0.4, 0.04, null, true);
                    }

                    // Two spirals twisting around the column.
                    double spin = t * 0.9 + y * 2.2;
                    for (int strand = 0; strand < 2; strand++) {
                        double a = spin + strand * Math.PI;
                        Location s = level.clone().add(Math.cos(a) * r * 1.3, 0, Math.sin(a) * r * 1.3);
                        world.spawnParticle(Particle.DUST, s, 1, 0, 0, 0, 0.0, CORE_DUST, true);
                    }
                }

                // Peak: a burst at the top, then lava rains back down.
                if (t == RISE_TICKS) {
                    Location top = center.clone().add(0, COLUMN_HEIGHT, 0);
                    world.spawnParticle(Particle.LAVA, top, 25, 0.6, 0.4, 0.6, 0.0, null, true);
                    world.spawnParticle(Particle.FLAME, top, 30, 0.5, 0.3, 0.5, 0.12, null, true);
                }
                if (t >= RISE_TICKS) {
                    Location top = center.clone().add(0, COLUMN_HEIGHT * 0.9, 0);
                    world.spawnParticle(Particle.FALLING_LAVA, top, 3, RADIUS * 0.45, 0.3, RADIUS * 0.45, 0.0, null, true);
                }

                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnPillar(Location center) {
        spawnColumn(center);
        spawnSpireRing(center);
    }

    // ---- magma spire ring ----------------------------------------------------------------

    /**
     * A ring of leaning spires around the particle column. Every spire is a stack of tapering,
     * roughly cube-shaped tiers (height == width), so no block is ever stretched. Each spire rises
     * out of the ground, holds, then sinks back into it.
     */
    private void spawnSpireRing(Location center) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int ring = 8;
        double offset = rng.nextDouble() * Math.PI * 2;
        for (int i = 0; i < ring; i++) {
            double angle = offset + i * (Math.PI * 2 / ring) + (rng.nextDouble() - 0.5) * 0.35;
            double dist = RADIUS * (0.5 + rng.nextDouble() * 0.35);
            float baseWidth = 0.55f + rng.nextFloat() * 0.25f;
            int tiers = 2 + rng.nextInt(2); // 2-3
            float tilt = (float) Math.toRadians(10 + rng.nextDouble() * 14);
            spawnSpire(center, (float) (Math.cos(angle) * dist), (float) (Math.sin(angle) * dist),
                    baseWidth, tiers, 0.75f, tilt, (float) angle, i % 3 == 0 ? CRUST : MAGMA);
        }
    }

    /**
     * One spire of {@code tiers} stacked cubes.
     *
     * @param baseWidth width of the bottom tier; each tier above is {@code taper} times the one below
     * @param tilt      lean angle in radians (0 = straight up)
     * @param leanDir   horizontal direction the spire leans towards, in radians
     */
    private void spawnSpire(Location center, float dx, float dz, float baseWidth, int tiers, float taper,
                            float tilt, float leanDir, BlockData block) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        World world = center.getWorld();

        Quaternionf lean = new Quaternionf();
        if (tilt != 0f) {
            // Axis = up x lean direction, so a positive angle tips the top towards the lean direction.
            lean.rotateAxis(tilt, (float) Math.sin(leanDir), 0f, (float) -Math.cos(leanDir));
        }

        // One twist for the whole spire plus a small per-tier wobble, so it reads as a single
        // stalagmite instead of a pile of randomly spun cubes.
        float baseYaw = (float) Math.toRadians(rng.nextDouble() * 90);
        // Where the spire meets the ground. Tiers grow out of / shrink back into this point.
        Vector3f groundPoint = new Vector3f(dx, -0.05f, dz);

        float width = baseWidth;
        float y = -0.05f; // sink slightly so the base never floats
        for (int i = 0; i < tiers; i++) {
            float yaw = baseYaw + (float) Math.toRadians((rng.nextDouble() - 0.5) * 24);
            Quaternionf rotation = new Quaternionf(lean).rotateY(yaw);

            // Tier base centre sits on the spire axis: position + lean * (0, y, 0).
            Vector3f axisPoint = lean.transform(new Vector3f(0f, y, 0f));
            Vector3f origin = new Vector3f(dx + axisPoint.x, axisPoint.y, dz + axisPoint.z);

            spawnTier(world, center, origin, groundPoint, rotation, width, block, 1L + i);

            y += width * 0.95f; // tiers overlap a touch so there are no gaps when leaning
            width *= taper;
        }
    }

    private void spawnTier(World world, Location center, Vector3f origin, Vector3f groundPoint,
                           Quaternionf rotation, float width, BlockData block, long growDelay) {
        // Pivot about the middle of the tier's base: translation = origin - rotation * (base centre).
        Vector3f toCorner = rotation.transform(new Vector3f(width / 2f, 0f, width / 2f));
        Transformation grown = new Transformation(
                new Vector3f(origin).sub(toCorner), rotation, new Vector3f(width, width, width), new Quaternionf());

        float tiny = 0.05f;
        Vector3f tinyCorner = rotation.transform(new Vector3f(tiny / 2f, 0f, tiny / 2f));
        // Hidden = a speck at the spire's base on the ground. Growing interpolates the tier up its
        // axis while it scales, so the spire rises out of the floor and sinks back into it.
        Transformation hidden = new Transformation(
                new Vector3f(groundPoint).sub(tinyCorner), rotation, new Vector3f(tiny, tiny, tiny), new Quaternionf());

        BlockDisplay tier = world.spawn(center, BlockDisplay.class, d -> {
            d.setBlock(block);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(hidden);
            d.setInterpolationDuration(3);
            d.setShadowRadius(0f);
            d.setPersistent(false);
            d.setInvulnerable(true);
        });
        ACTIVE_PILLARS.add(tier);

        // Pop up (interpolates over 3 ticks), hold, shrink back, remove.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!tier.isValid()) return;
            tier.setInterpolationDelay(0);
            tier.setTransformation(grown);
        }, growDelay);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!tier.isValid()) return;
            tier.setInterpolationDuration(6);
            tier.setInterpolationDelay(0);
            tier.setTransformation(hidden);
        }, 14L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (tier.isValid()) tier.remove();
            ACTIVE_PILLARS.remove(tier);
        }, 21L);
    }

    /** Safety net for plugin disable - removes every spire tier still on screen. */
    public static void removeAll() {
        for (BlockDisplay tier : new ArrayList<>(ACTIVE_PILLARS)) {
            if (tier.isValid()) tier.remove();
        }
        ACTIVE_PILLARS.clear();
    }

    @Override
    public String getName() {
        return ChatColor.GOLD + "Magma Eruption";
    }

    @Override
    public String getDescription() {
        return "Look at an enemy to crack the ground beneath them. After a short warning it erupts, damaging, igniting and launching everything in the blast.";
    }
}