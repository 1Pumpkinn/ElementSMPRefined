package net.rose.elementSMPRefined.ability.main.basic.earth;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.util.damage.TrueDamage;
import net.rose.elementSMPRefined.util.scan.GroundScan;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shockwave - Leap into the air, hang for a beat, then slam back down. The
 * impact sends a ring of rippling earth outward in every direction, launching
 * every player and mob it passes under into the air.
 *
 * <p>The ring expands one step per tick. At each step the ground block under
 * every point on the circle is copied as a {@link BlockDisplay} that heaves up
 * straight up, holds, and settles back, so the terrain visibly ripples
 * outward like a real shockwave. No real blocks are ever changed, so there is
 * nothing to clean up if the server stops mid-cast.</p>
 *
 * <p>The displays are spawned in the air block <em>above</em> the ground block
 * (not inside it). A display inside a solid block samples that block's light
 * level, which is zero, and renders pitch black.</p>
 */
public class ShockwaveAbility extends BaseAbility {

    private static final double JUMP_VELOCITY = 1.1;   // ~6-7 blocks of hop
    private static final double SLAM_SPEED = 1.5;      // downward velocity while slamming
    private static final int MIN_RISE_TICKS = 4;       // don't check for the apex before this
    private static final int MAX_RISE_TICKS = 25;      // force the slam if they never peak (ceiling, etc.)
    private static final int SLAM_TIMEOUT_TICKS = 100; // force the impact if they never land (void, water)

    private static final double MAX_RANGE = 10.0;
    private static final double START_RADIUS = 1.5;
    private static final double STEP_PER_TICK = 0.8;   // how far the ring advances each tick
    private static final double ARC_SPACING = 0.5;     // small enough that the ring has no gaps (columns are de-duplicated)

    private static final double LAUNCH_UP = 1.1;       // ~6-8 blocks of height
    private static final double LAUNCH_OUTWARD = 0.6;
    private static final double DAMAGE = 6.0;
    private static final double HIT_RADIUS_XZ = 1.5;
    private static final double HIT_RADIUS_Y = 3.0;

    private static final int RISE_TICKS = 2;
    private static final int HOLD_TICKS = 1;
    private static final int FALL_TICKS = 3;
    private static final double RISE_HEIGHT = 1.0;     // how far each block heaves up, in blocks
    private static final float CUBE_SCALE = 0.98f;     // slightly under 1 so the copy doesn't z-fight the real block

    private static final int SCAN_UP = 2;
    private static final int SCAN_DOWN = 4;

    private final ElementSMPRefined plugin;
    private final Set<UUID> activeCasters = new HashSet<>();

    public ShockwaveAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("earth_shockwave", ElementType.EARTH, 2, 60, 2, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player caster = context.getPlayer();
        TrustManager trust = context.getTrustManager();
        UUID casterId = caster.getUniqueId();

        if (!activeCasters.add(casterId)) return false;

        World world = caster.getWorld();
        Location start = caster.getLocation();

        caster.setVelocity(new Vector(0, JUMP_VELOCITY, 0));
        caster.setFallDistance(0f);

        world.playSound(start, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.2f, 0.6f);
        world.playSound(start, Sound.BLOCK_GRAVEL_BREAK, 1.5f, 0.5f);
        world.spawnParticle(Particle.BLOCK, start.clone().add(0, 0.2, 0), 30, 0.6, 0.1, 0.6, 0.0,
                Material.DIRT.createBlockData(), true);

        new BukkitRunnable() {
            int ticks = 0;
            double lastY = start.getY();
            boolean slamming = false;

            @Override
            public void run() {
                if (!caster.isOnline() || caster.isDead()) {
                    activeCasters.remove(casterId);
                    cancel();
                    return;
                }

                ticks++;
                // The slam's own impact replaces vanilla fall damage.
                caster.setFallDistance(0f);

                if (!slamming) {
                    // Player#getVelocity isn't reliable for players (the client simulates its
                    // own movement), so detect the apex from the actual Y position instead.
                    double y = caster.getLocation().getY();
                    boolean peaked = ticks > MIN_RISE_TICKS && (y <= lastY + 1.0E-3 || ticks >= MAX_RISE_TICKS);
                    lastY = y;
                    if (!peaked) return;

                    slamming = true;
                    world.playSound(caster.getLocation(), Sound.ENTITY_BREEZE_SLIDE, 1.2f, 0.6f);
                }

                caster.setVelocity(new Vector(0, -SLAM_SPEED, 0));
                world.spawnParticle(Particle.BLOCK, caster.getLocation().add(0, 1, 0), 6, 0.3, 0.6, 0.3, 0.0,
                        Material.STONE.createBlockData(), true);

                if (caster.isOnGround() || isSolidBelow(caster) || ticks > SLAM_TIMEOUT_TICKS) {
                    cancel();
                    activeCasters.remove(casterId);
                    caster.setFallDistance(0f);
                    startShockwave(caster, trust, caster.getLocation());
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        return true;
    }

    private boolean isSolidBelow(Player player) {
        return player.getLocation().subtract(0, 0.2, 0).getBlock().getType().isSolid();
    }

    /** Impact burst, then the ring of rippling earth expanding in every direction. */
    private void startShockwave(Player caster, TrustManager trust, Location origin) {
        World world = origin.getWorld();
        world.spawnParticle(Particle.EXPLOSION, origin, 3, 0.5, 0.2, 0.5, 0.0);
        world.playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.6f);
        world.playSound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.4f);

        final Set<UUID> alreadyHit = new HashSet<>();
        final Set<Long> raisedColumns = new HashSet<>();
        // The ring revisits the same columns many times (points are closer together than a block),
        // so each column's ground is looked up once per cast. null = "no usable ground here".
        final Map<Long, Block> groundByColumn = new HashMap<>();
        final int startY = origin.getBlockY();

        new BukkitRunnable() {
            double radius = START_RADIUS;

            @Override
            public void run() {
                if (radius > MAX_RANGE) {
                    cancel();
                    return;
                }

                int points = Math.max(8, (int) Math.ceil((2 * Math.PI * radius) / ARC_SPACING));

                // Fetched once per tick, and only if a point actually finds ground.
                List<LivingEntity> candidates = null;

                for (int i = 0; i < points; i++) {
                    double angle = (2 * Math.PI * i) / points;
                    double dirX = Math.cos(angle);
                    double dirZ = Math.sin(angle);
                    int bx = (int) Math.floor(origin.getX() + dirX * radius);
                    int bz = (int) Math.floor(origin.getZ() + dirZ * radius);

                    long column = planarKey(bx, bz);
                    Block ground;
                    if (groundByColumn.containsKey(column)) {
                        ground = groundByColumn.get(column);
                    } else {
                        ground = GroundScan.findGround(world, bx, bz, startY, SCAN_UP, SCAN_DOWN);
                        groundByColumn.put(column, ground);
                    }
                    if (ground == null) continue;

                    if (raisedColumns.add(columnKey(ground))) {
                        // Taller near the centre, tapering off toward the edge of the wave.
                        double falloff = 1.0 - (radius / MAX_RANGE) * 0.4;
                        raiseBlock(ground, falloff);
                    }

                    if (candidates == null) candidates = collectCandidates(caster, trust, origin, radius, alreadyHit);
                    if (!candidates.isEmpty()) {
                        launchNearby(caster, candidates, ground, dirX, dirZ, alreadyHit);
                    }
                }

                world.playSound(origin.clone().add(radius, 0, 0), Sound.BLOCK_STONE_BREAK, 0.5f, 0.7f);
                radius += STEP_PER_TICK;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Heaves a copy of the block up out of the ground, holds it, then settles it back down. */
    private void raiseBlock(Block ground, double falloff) {
        World w = ground.getWorld();
        BlockData data = ground.getBlockData();

        // Spawned in the air block above the ground so it's lit normally. Its resting
        // pose sits exactly over the real block (translation -1 on Y).
        Location spawn = ground.getLocation().add(0, 1, 0);

        float rise = (float) (RISE_HEIGHT * falloff);

        // Straight up and down, no rotation: flat cubes keep the ground looking like ground.
        Transformation resting = placed(-0.5f, new Quaternionf());
        Transformation raised = placed(-0.5f + rise, new Quaternionf());

        BlockDisplay display = w.spawn(spawn, BlockDisplay.class, d -> {
            d.setBlock(data);
            d.setPersistent(false);
            d.setTransformation(resting);
        });

        w.spawnParticle(Particle.BLOCK, spawn.clone().add(0.5, 0.1, 0.5), 4, 0.4, 0.1, 0.4, 0.0, data, true);

        // Interpolation only animates if the transformation changes after the
        // entity has been sent to clients, so the rise starts a tick later.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!display.isValid()) return;
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(RISE_TICKS);
            display.setTransformation(raised);
        }, 1L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!display.isValid()) return;
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(FALL_TICKS);
            display.setTransformation(resting);
        }, 1L + RISE_TICKS + HOLD_TICKS);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid()) display.remove();
        }, 1L + RISE_TICKS + HOLD_TICKS + FALL_TICKS + 2L);
    }

    /**
     * Pose for the cube whose centre sits at (0.5, centerY, 0.5) relative to the
     * spawn block, rotated about its own centre rather than a corner.
     */
    private static Transformation placed(float centerY, Quaternionf rotation) {
        float half = CUBE_SCALE / 2f;
        Vector3f rotatedHalf = new Vector3f(half, half, half).rotate(rotation);
        Vector3f translation = new Vector3f(0.5f - rotatedHalf.x, centerY - rotatedHalf.y, 0.5f - rotatedHalf.z);
        return new Transformation(translation, rotation,
                new Vector3f(CUBE_SCALE, CUBE_SCALE, CUBE_SCALE), new Quaternionf());
    }

    /** Everything near the ring that it is allowed to launch, fetched with a single query. */
    private List<LivingEntity> collectCandidates(Player caster, TrustManager trust, Location origin,
                                                 double radius, Set<UUID> alreadyHit) {
        double reachXZ = radius + STEP_PER_TICK + HIT_RADIUS_XZ + 1.0;
        double reachY = SCAN_UP + SCAN_DOWN + 1 + HIT_RADIUS_Y + 1.0;

        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity entity : origin.getNearbyLivingEntities(reachXZ, reachY, reachXZ)) {
            if (entity.equals(caster)) continue;
            if (entity instanceof ArmorStand) continue;
            if (trust != null && entity instanceof Player other
                    && trust.isTrusted(caster.getUniqueId(), other.getUniqueId())) continue;
            if (alreadyHit.contains(entity.getUniqueId())) continue;
            out.add(entity);
        }
        return out;
    }

    /** Launches whoever overlaps the hit zone above this ground block, once each. */
    private void launchNearby(Player caster, List<LivingEntity> candidates, Block ground,
                              double outwardX, double outwardZ, Set<UUID> alreadyHit) {
        BoundingBox zone = BoundingBox.of(
                new Vector(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5),
                HIT_RADIUS_XZ, HIT_RADIUS_Y, HIT_RADIUS_XZ);

        for (Iterator<LivingEntity> it = candidates.iterator(); it.hasNext(); ) {
            LivingEntity entity = it.next();
            if (!entity.isValid() || !entity.getBoundingBox().overlaps(zone)) continue;

            it.remove(); // each entity is only launched once per cast
            if (!alreadyHit.add(entity.getUniqueId())) continue;

            // Damage first: it applies its own knockback, which would otherwise
            // overwrite the launch if we set velocity before it.
            TrueDamage.of(DAMAGE).attacker(caster).ability(getName()).apply(entity);

            entity.setVelocity(new Vector(outwardX * LAUNCH_OUTWARD, LAUNCH_UP, outwardZ * LAUNCH_OUTWARD));
        }
    }

    /** Packs an (x, z) column into one long. */
    private static long planarKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static long columnKey(Block b) {
        return ((long) b.getX() & 0x3FFFFFFL) << 38 | ((long) b.getZ() & 0x3FFFFFFL) << 12 | (b.getY() & 0xFFFL);
    }

    @Override
    public String getName() {
        return ChatColor.YELLOW + "Shockwave";
    }

    @Override
    public String getDescription() {
        return "Jump up and slam down, sending a shockwave out that damages and launches enemies.";
    }
}
