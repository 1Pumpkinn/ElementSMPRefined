package net.rose.elementSMPRefined.ability.main.upgraded.lava;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.ability.passive.upgraded.lava.listeners.LavaTargets;
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;


/**
 * Magma Eruption - look at an enemy and the ground under them cracks open. After a short,
 * dodgeable warning the spot erupts: everything in the blast takes damage, is set on fire
 * and is thrown into the air.
 * <p>
 * The eruption is a ring of rock spikes (exactly one {@link BlockDisplay} each) around a lava
 * fountain made of particles. It never changes real blocks, so nothing needs to be restored.
 */
public class EruptionAbility extends BaseAbility {

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
    private static final BlockData BASALT = Material.BASALT.createBlockData();

    /** Every live spike, so plugin disable can sweep up anything still on screen. */
    private static final Set<BlockDisplay> ACTIVE_PILLARS = new HashSet<>();


    private final ElementSMPRefined plugin;

    public EruptionAbility(JavaPlugin plugin, ConfigManager configManager) {
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

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double[] crackAngle = new double[CRACKS];
        double[] crackPhase = new double[CRACKS];
        double crackOffset = rng.nextDouble() * Math.PI * 2;
        for (int c = 0; c < CRACKS; c++) {
            crackAngle[c] = crackOffset + c * (Math.PI * 2 / CRACKS) + (rng.nextDouble() - 0.5) * 0.5;
            crackPhase[c] = rng.nextDouble() * Math.PI * 2;
        }

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= WARNING_TICKS) {
                    erupt(caster, trust, center);
                    cancel();
                    return;
                }

                double progress = (double) ticks / WARNING_TICKS;

                if (ticks % 2 == 0) {
                    // Glowing cracks fan out from the centre and creep towards the blast radius.
                    double reach = RADIUS * (0.3 + 0.8 * progress);
                    for (int c = 0; c < CRACKS; c++) {
                        double dirX = Math.cos(crackAngle[c]);
                        double dirZ = Math.sin(crackAngle[c]);
                        double perpX = -dirZ;
                        double perpZ = dirX;
                        for (double d = 0.25; d <= reach; d += 0.25) {
                            double wobble = Math.sin(d * 4.5 + crackPhase[c]) * 0.18 * Math.min(1.0, d);
                            Location p = center.clone().add(dirX * d + perpX * wobble, 0.08, dirZ * d + perpZ * wobble);
                            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, p, 1, 0.02, 0.0, 0.02, 0.0, CRACK_DUST, true);
                        }
                        Location end = center.clone().add(dirX * reach, 0.12, dirZ * reach);
                        world.spawnParticle(Particle.SMALL_FLAME, end, 1, 0.03, 0.03, 0.03, 0.0, null, true);
                    }

                    // Thin ring marking the blast radius, tightening as the timer runs out.
                    double ringRadius = RADIUS * (1.0 - 0.2 * progress);
                    for (int i = 0; i < 32; i++) {
                        double angle = i * (Math.PI * 2 / 32);
                        Location p = center.clone().add(Math.cos(angle) * ringRadius, 0.1, Math.sin(angle) * ringRadius);
                        world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0.0, EDGE_DUST, true);
                    }
                }

                // Heat rising out of the cracked ground.
                world.spawnParticle(Particle.ASH, center.clone().add(0, 0.3, 0), 3, RADIUS * 0.5, 0.3, RADIUS * 0.5, 0.0, null, true);
                if (ticks % 3 == 0) {
                    world.spawnParticle(Particle.LAVA, center.clone().add(0, 0.2, 0), 2, RADIUS * 0.5, 0.1, RADIUS * 0.5, 0.0, null, true);
                    world.spawnParticle(Particle.BLOCK, center.clone().add(0, 0.15, 0), 6, RADIUS * 0.4, 0.05, RADIUS * 0.4, 0.0, MAGMA, true);
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

        world.spawnParticle(Particle.EXPLOSION, center.clone().add(0, 0.4, 0), 1, 0, 0, 0, 0.0, null, true);
        world.spawnParticle(Particle.LAVA, mid, 25, 0.7, 0.8, 0.7, 0.0, null, true);
        world.spawnParticle(Particle.LARGE_SMOKE, mid, 14, 0.6, 1.0, 0.6, 0.03, null, true);
        world.spawnParticle(Particle.BLOCK, mid, 45, 0.9, 1.0, 0.9, 0.0, MAGMA, true);
        world.spawnParticle(Particle.BLOCK, mid, 25, 0.9, 1.0, 0.9, 0.0, BASALT, true);

        List<Location> tips = spawnSpikeRing(center);
        spawnFountain(center, tips);

        for (LivingEntity e : mid.getNearbyLivingEntities(RADIUS, 2.0, RADIUS)) {
            if (!LavaTargets.isValid(caster, e, trust)) continue;

            TrueDamage.of(DAMAGE).attacker(caster).ignoreIFrames(false).apply(e);
            e.setFireTicks(BURN_TICKS);
            e.setVelocity(new Vector(0, LAUNCH_VELOCITY, 0));
        }
    }

    // ---- particle visuals ----------------------------------------------------------------

    private static final int CRACKS = 7;

    private static final Particle.DustOptions CORE_DUST = new Particle.DustOptions(Color.fromRGB(255, 130, 25), 1.5f);
    private static final Particle.DustOptions EDGE_DUST = new Particle.DustOptions(Color.fromRGB(210, 45, 0), 1.0f);
    private static final Particle.DustOptions DROP_DUST = new Particle.DustOptions(Color.fromRGB(255, 160, 40), 1.1f);
    private static final Particle.DustTransition CRACK_DUST =
            new Particle.DustTransition(Color.fromRGB(255, 150, 30), Color.fromRGB(120, 15, 0), 0.9f);
    private static final Particle.DustTransition RISING_DUST =
            new Particle.DustTransition(Color.fromRGB(255, 175, 45), Color.fromRGB(110, 20, 0), 1.4f);
    private static final Particle.DustTransition TIP_DUST =
            new Particle.DustTransition(Color.fromRGB(255, 200, 80), Color.fromRGB(200, 40, 0), 1.0f);

    private static final double COLUMN_HEIGHT = 3.2;
    /** Ticks the core column takes to shoot up. */
    private static final int RISE_TICKS = 4;
    private static final int ERUPTION_TICKS = 24;
    private static final double COLUMN_RADIUS = 0.55;
    private static final int DROPLETS = 16;
    private static final double GRAVITY = 0.07;

    /**
     * Particle side of the eruption: a slim, tapered core column, a double shockwave on the
     * ground, a fan of lava droplets that fly out in arcs and splash back down, and a glow at
     * the tip of every spike.
     */
    private void spawnFountain(Location center, List<Location> tips) {
        World world = center.getWorld();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Droplet ballistics, rolled once so each droplet follows a clean arc.
        double[] angle = new double[DROPLETS];
        double[] horizontal = new double[DROPLETS];
        double[] vertical = new double[DROPLETS];
        int[] start = new int[DROPLETS];
        boolean[] landed = new boolean[DROPLETS];
        for (int i = 0; i < DROPLETS; i++) {
            angle[i] = rng.nextDouble() * Math.PI * 2;
            horizontal[i] = 0.05 + rng.nextDouble() * 0.08;
            vertical[i] = 0.5 + rng.nextDouble() * 0.25;
            start[i] = rng.nextInt(4);
        }

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= ERUPTION_TICKS) {
                    cancel();
                    return;
                }

                // Shockwave: a bright ring plus a trailing smoke ring racing out along the ground.
                if (t < 7) {
                    double ringRadius = RADIUS * (t + 1) / 7.0;
                    for (int i = 0; i < 32; i++) {
                        double a = i * (Math.PI * 2 / 32);
                        double cos = Math.cos(a);
                        double sin = Math.sin(a);
                        Location p = center.clone().add(cos * ringRadius, 0.12, sin * ringRadius);
                        world.spawnParticle(Particle.DUST, p, 1, 0.04, 0.03, 0.04, 0.0, EDGE_DUST, true);
                        if (i % 4 == 0) {
                            world.spawnParticle(Particle.SMALL_FLAME, p, 1, 0, 0.03, 0, 0.01, null, true);
                        }
                        if (t > 1 && i % 8 == 0) {
                            Location inner = center.clone().add(cos * ringRadius * 0.7, 0.2, sin * ringRadius * 0.7);
                            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, inner, 0, 0, 0.06, 0, 1.0, null, true);
                        }
                    }
                }

                // Core column: grows to full height, then thins out. Tapered, with a single helix.
                double rise = Math.min(1.0, (t + 1) / (double) RISE_TICKS);
                double height = COLUMN_HEIGHT * rise;
                double fade = t < 9 ? 1.0 : Math.max(0.0, 1.0 - (t - 9) / 6.0);
                if (fade > 0.0) {
                    for (double y = 0; y <= height; y += 0.35) {
                        double taper = 1.0 - 0.7 * (y / COLUMN_HEIGHT);
                        double r = COLUMN_RADIUS * taper;
                        Location level = center.clone().add(0, y, 0);

                        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, level, Math.max(1, (int) Math.round(2 * fade)),
                                r * 0.4, 0.12, r * 0.4, 0.0, RISING_DUST, true);
                        if (fade > 0.4) {
                            world.spawnParticle(Particle.DUST, level, 1, r * 0.2, 0.08, r * 0.2, 0.0, CORE_DUST, true);
                        }

                        double a = t * 0.8 + y * 2.4;
                        Location s = level.clone().add(Math.cos(a) * r * 1.4, 0, Math.sin(a) * r * 1.4);
                        world.spawnParticle(Particle.DUST, s, 1, 0, 0, 0, 0.0, CORE_DUST, true);
                    }
                    if (t % 2 == 0) {
                        world.spawnParticle(Particle.FLAME, center.clone().add(0, height * 0.5, 0), 2,
                                COLUMN_RADIUS * 0.3, height * 0.4, COLUMN_RADIUS * 0.3, 0.03, null, true);
                    }
                }

                // Lava droplets flying out in parabolas, splashing where they land.
                for (int i = 0; i < DROPLETS; i++) {
                    if (landed[i]) continue;
                    for (int sub = 0; sub < 2; sub++) {
                        double tau = t - start[i] + sub * 0.5;
                        if (tau < 0) continue;
                        double y = 0.3 + vertical[i] * tau - 0.5 * GRAVITY * tau * tau;
                        if (y <= 0.05) {
                            landed[i] = true;
                            double d = horizontal[i] * tau;
                            Location splash = center.clone().add(Math.cos(angle[i]) * d, 0.1, Math.sin(angle[i]) * d);
                            world.spawnParticle(Particle.LAVA, splash, 1, 0.05, 0.0, 0.05, 0.0, null, true);
                            world.spawnParticle(Particle.SMALL_FLAME, splash, 2, 0.08, 0.02, 0.08, 0.02, null, true);
                            break;
                        }
                        double d = horizontal[i] * tau;
                        Location p = center.clone().add(Math.cos(angle[i]) * d, y, Math.sin(angle[i]) * d);
                        world.spawnParticle(Particle.DUST, p, 1, 0.02, 0.02, 0.02, 0.0, DROP_DUST, true);
                        if (sub == 0 && vertical[i] - GRAVITY * tau < 0 && (t + i) % 3 == 0) {
                            world.spawnParticle(Particle.FALLING_LAVA, p, 1, 0, 0, 0, 0.0, null, true);
                        }
                    }
                }

                // Glowing tip and a lava drip on every spike while it stands.
                if (t >= 1 && t <= 14) {
                    for (Location tip : tips) {
                        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, tip, 2, 0.08, 0.08, 0.08, 0.0, TIP_DUST, true);
                        if (t % 2 == 0) world.spawnParticle(Particle.SMALL_FLAME, tip, 1, 0.05, 0.05, 0.05, 0.01, null, true);
                        if (t % 4 == 0) world.spawnParticle(Particle.DRIPPING_LAVA, tip.clone().add(0, -0.3, 0), 1, 0.1, 0.0, 0.1, 0.0, null, true);
                    }
                }

                // Embers drifting up over the whole blast area.
                if (t < 16) {
                    world.spawnParticle(Particle.ASH, center.clone().add(0, 1.0, 0), 4, RADIUS * 0.8, 1.0, RADIUS * 0.8, 0.0, null, true);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ---- rock slabs ----------------------------------------------------------------------

    private static final BlockData SMOOTH_BASALT = Material.SMOOTH_BASALT.createBlockData();

    /** Every slab is the same size and leans the same amount. */
    private static final float SLAB_HEIGHT = 1.4f;
    private static final float SLAB_WIDTH = 0.55f;
    private static final float SLAB_DEPTH = 0.35f;
    private static final float SLAB_TILT = (float) Math.toRadians(14);

    /**
     * A ring of identical leaning basalt slabs around the particle fountain plus a few more inside
     * it. Every slab is exactly one {@link BlockDisplay}: a single block scaled into a rectangle.
     *
     * @return the world position of every slab top, for the glow
     */
    private List<Location> spawnSpikeRing(Location center) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        List<Location> tips = new ArrayList<>();

        int ring = 8;
        double offset = rng.nextDouble() * Math.PI * 2;
        for (int i = 0; i < ring; i++) {
            double angle = offset + i * (Math.PI * 2 / ring) + (rng.nextDouble() - 0.5) * 0.35;
            double dist = RADIUS * (0.6 + rng.nextDouble() * 0.3);
            float height = SLAB_HEIGHT;
            float width = SLAB_WIDTH;
            float depth = SLAB_DEPTH;
            float tilt = SLAB_TILT;
            tips.add(spawnSlab(center, (float) (Math.cos(angle) * dist), (float) (Math.sin(angle) * dist),
                    width, height, depth, tilt, (float) angle, i % 3 == 0 ? SMOOTH_BASALT : BASALT));
        }

        // Inner slabs so the ring has depth.
        int inner = 4;
        double innerOffset = rng.nextDouble() * Math.PI * 2;
        for (int i = 0; i < inner; i++) {
            double angle = innerOffset + i * (Math.PI * 2 / inner) + (rng.nextDouble() - 0.5) * 0.6;
            double dist = RADIUS * (0.2 + rng.nextDouble() * 0.2);
            float height = SLAB_HEIGHT;
            float width = SLAB_WIDTH;
            float depth = SLAB_DEPTH;
            float tilt = SLAB_TILT;
            tips.add(spawnSlab(center, (float) (Math.cos(angle) * dist), (float) (Math.sin(angle) * dist),
                    width, height, depth, tilt, (float) angle, BASALT));
        }
        return tips;
    }

    /**
     * One slab = one block display scaled to {@code width x height x depth} and leaned about the
     * middle of its base. It shoots up out of the floor, settles, holds, then sinks back in.
     *
     * @param tilt    lean angle in radians (0 = straight up)
     * @param leanDir horizontal direction the slab leans towards, in radians
     * @return world position of the top of the slab
     */
    private Location spawnSlab(Location center, float dx, float dz, float width, float height, float depth,
                               float tilt, float leanDir, BlockData block) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        World world = center.getWorld();

        // Lean away from the centre, then a spin about the slab's own axis so the faces differ.
        Quaternionf rotation = new Quaternionf();
        if (tilt != 0f) {
            rotation.rotateAxis(tilt, (float) Math.sin(leanDir), 0f, (float) -Math.cos(leanDir));
        }
        rotation.rotateY((float) (rng.nextDouble() * Math.PI * 2));

        Vector3f base = new Vector3f(dx, -0.2f, dz);

        Transformation hidden = slabTransform(base, rotation, 0.05f, 0.05f, 0.05f);
        Transformation overshoot = slabTransform(base, rotation, width, height * 1.12f, depth);
        Transformation grown = slabTransform(base, rotation, width, height, depth);

        BlockDisplay slab = world.spawn(center, BlockDisplay.class, d -> {
            d.setBlock(block);
            d.setBrightness(new Display.Brightness(10, 12));
            d.setTransformation(hidden);
            d.setInterpolationDuration(2);
            d.setShadowRadius(0f);
            d.setPersistent(false);
            d.setInvulnerable(true);
        });
        ACTIVE_PILLARS.add(slab);

        long delay = 1L + rng.nextInt(3);

        // Burst out past full height, settle back, hold, sink away, remove.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!slab.isValid()) return;
            slab.setInterpolationDelay(0);
            slab.setInterpolationDuration(2);
            slab.setTransformation(overshoot);
        }, delay);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!slab.isValid()) return;
            slab.setInterpolationDelay(0);
            slab.setInterpolationDuration(3);
            slab.setTransformation(grown);
        }, delay + 2);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!slab.isValid()) return;
            slab.setInterpolationDelay(0);
            slab.setInterpolationDuration(6);
            slab.setTransformation(hidden);
        }, 14L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (slab.isValid()) slab.remove();
            ACTIVE_PILLARS.remove(slab);
        }, 21L);

        Vector3f top = rotation.transform(new Vector3f(0f, height, 0f));
        return center.clone().add(base.x + top.x, base.y + top.y, base.z + top.z);
    }

    /**
     * Pivots the block about the middle of its base, which sits at {@code base}.
     * Display maths is {@code T * rotation * scale}, so the base centre lands at
     * {@code T + rotation * (0.5 * width, 0, 0.5 * depth)}; solve for T.
     */
    private static Transformation slabTransform(Vector3f base, Quaternionf rotation,
                                                float width, float height, float depth) {
        Vector3f baseCentre = rotation.transform(new Vector3f(0.5f * width, 0f, 0.5f * depth));
        return new Transformation(new Vector3f(base).sub(baseCentre), new Quaternionf(rotation),
                new Vector3f(width, height, depth), new Quaternionf());
    }

    /** Safety net for plugin disable - removes every spike still on screen. */
    public static void removeAll() {
        for (BlockDisplay spike : new ArrayList<>(ACTIVE_PILLARS)) {
            if (spike.isValid()) spike.remove();
        }
        ACTIVE_PILLARS.clear();
    }

    @Override
    public String getName() {
        return ChatColor.GOLD + "Eruption";
    }

    @Override
    public String getDescription() {
        return "Look at an enemy to crack the ground beneath them. After a short warning it erupts, damaging, igniting and launching everything in the blast.";
    }
}