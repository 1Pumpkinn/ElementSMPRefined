package net.rose.elementSMPRefined.ability.main.basic.earth;

import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.util.damage.TrueDamage;
import net.rose.elementSMPRefined.util.scan.GroundScan;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ShockwaveAbility extends BaseAbility {

    private static final double JUMP_VELOCITY = 1.1;   // ~6-7 blocks of hop
    private static final double SLAM_SPEED = 1.5;      // downward velocity while slamming
    private static final int MIN_RISE_TICKS = 8;       // don't check for the apex before this (the real apex is ~tick 12)
    private static final double APEX_DROP = 0.15;      // must have fallen this far below the highest point to count as the apex
    private static final int APEX_STALL_TICKS = 4;     // ...or sat at the highest point this long (ceiling bonk)
    private static final int MAX_RISE_TICKS = 25;      // force the slam if they never peak (ceiling, etc.)
    private static final int SLAM_TIMEOUT_TICKS = 100; // force the impact if they never land (void, water)

    private static final double MAX_RANGE = 10.0;
    private static final double START_RADIUS = 1.5;
    private static final double STEP_PER_TICK = 0.8;   // how far the ring advances each tick
    private static final double ARC_SPACING = 0.5;     // small enough that the ring has no gaps (columns are de-duplicated)

    private static final double LAUNCH_UP = 1.1;       // ~6-8 blocks of height
    private static final double LAUNCH_OUTWARD = 0.6;
    private static final double DAMAGE = 6.0;
    private static final double DAMAGE_AT_MAX_RANGE = 2.0;
    private static final double HIT_RADIUS_XZ = 1.5;
    private static final double HIT_RADIUS_Y = 3.0;

    // Per-block animation, in ticks. Interpolation is linear, so a slightly longer settle reads smoother.
    private static final int RISE_TICKS = 2;
    private static final int HOLD_TICKS = 1;
    private static final int FALL_TICKS = 4;
    private static final double RISE_HEIGHT = 1.0;     // how far each block heaves up, in blocks
    private static final float CUBE_SCALE = 0.98f;     // slightly under 1 so the copy doesn't z-fight the real block

    // Ticks after spawning at which each phase starts. The raise waits one tick because
    // interpolation only animates if the transformation changes after clients have the entity.
    private static final int RAISE_AT = 1;
    private static final int FALL_AT = RAISE_AT + RISE_TICKS + HOLD_TICKS;
    private static final int REMOVE_AT = FALL_AT + FALL_TICKS + 2;

    private static final float VIEW_RANGE = 0.5f;
    private static final int DUST_COUNT = 3;

    private static final int SCAN_UP = 2;
    private static final int SCAN_DOWN = 4;

    private static final Transformation RESTING = placed(-0.5f, new Quaternionf());

    private final ElementSMPRefined plugin;
    private final Set<UUID> activeCasters = new HashSet<>();

    private record WaveBlock(BlockDisplay display, Transformation raised, int born) {}

    public ShockwaveAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("earth_shockwave", ElementType.EARTH, 2, Constants.Cooldown.ABILITY_2_SECONDS, 2, configManager);
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
            double maxY = start.getY();
            int ticksSinceMax = 0;
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
                    // The server only sees a new position when the client sends one, so a single
                    // tick with an unchanged Y (lag, packet jitter) must NOT count as the apex -
                    // that used to slam the caster down after ~3 blocks. Only a real drop below
                    // the highest point, or a sustained stall (ceiling), ends the rise.
                    double y = caster.getLocation().getY();
                    if (y > maxY + 1.0E-3) {
                        maxY = y;
                        ticksSinceMax = 0;
                    } else {
                        ticksSinceMax++;
                    }
                    boolean peaked = ticks >= MAX_RISE_TICKS
                            || (ticks >= MIN_RISE_TICKS
                            && (y < maxY - APEX_DROP || ticksSinceMax >= APEX_STALL_TICKS));
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

        // One queue per phase. Every block spends the same time in each phase, so each queue is
        // already sorted by due time and only ever needs its front checked.
        final Deque<WaveBlock> toRaise = new ArrayDeque<>();
        final Deque<WaveBlock> toFall = new ArrayDeque<>();
        final Deque<WaveBlock> toRemove = new ArrayDeque<>();

        new BukkitRunnable() {
            double radius = START_RADIUS;
            int now = 0;

            @Override
            public void run() {
                now++;
                advanceBlocks(now, toRaise, toFall, toRemove);

                if (radius > MAX_RANGE) {
                    // Ring is finished; stay alive only until the last display has settled and been removed.
                    if (toRaise.isEmpty() && toFall.isEmpty() && toRemove.isEmpty()) cancel();
                    return;
                }

                int points = Math.max(8, (int) Math.ceil((2 * Math.PI * radius) / ARC_SPACING));

                // Taller near the centre, tapering off toward the edge of the wave. The pose is the
                // same for the whole ring step, so it is built once per tick instead of once per block.
                double falloff = 1.0 - (radius / MAX_RANGE) * 0.4;
                final Transformation raised = placed(-0.5f + (float) (RISE_HEIGHT * falloff), new Quaternionf());

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
                        toRaise.add(new WaveBlock(spawnBlock(ground), raised, now));
                    }

                    if (candidates == null) candidates = collectCandidates(caster, trust, origin, radius, alreadyHit);
                    if (!candidates.isEmpty()) {
                        launchNearby(caster, origin, candidates, ground, dirX, dirZ, alreadyHit);
                    }
                }

                if ((now & 1) == 0) {
                    world.playSound(origin.clone().add(radius, 0, 0), Sound.BLOCK_STONE_BREAK, 0.5f, 0.7f);
                }
                radius += STEP_PER_TICK;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static void advanceBlocks(int now, Deque<WaveBlock> toRaise, Deque<WaveBlock> toFall,
                                      Deque<WaveBlock> toRemove) {
        WaveBlock b;

        while ((b = toRemove.peekFirst()) != null && now >= b.born() + REMOVE_AT) {
            toRemove.pollFirst();
            if (b.display().isValid()) b.display().remove();
        }

        while ((b = toFall.peekFirst()) != null && now >= b.born() + FALL_AT) {
            toFall.pollFirst();
            if (b.display().isValid()) {
                b.display().setInterpolationDelay(0);
                b.display().setInterpolationDuration(FALL_TICKS);
                b.display().setTransformation(RESTING);
            }
            toRemove.addLast(b);
        }

        while ((b = toRaise.peekFirst()) != null && now >= b.born() + RAISE_AT) {
            toRaise.pollFirst();
            if (b.display().isValid()) {
                b.display().setInterpolationDelay(0);
                b.display().setInterpolationDuration(RISE_TICKS);
                b.display().setTransformation(b.raised());
            }
            toFall.addLast(b);
        }
    }

    private BlockDisplay spawnBlock(Block ground) {
        World w = ground.getWorld();
        BlockData data = ground.getBlockData();

        // Spawned in the air block above the ground so it's lit normally. Its resting
        // pose sits exactly over the real block (translation -1 on Y).
        Location spawn = new Location(w, ground.getX(), ground.getY() + 1, ground.getZ());

        BlockDisplay display = w.spawn(spawn, BlockDisplay.class, d -> {
            d.setBlock(data);
            d.setPersistent(false);
            d.setViewRange(VIEW_RANGE);
            d.setTransformation(RESTING);
        });

        // Checkerboard: dust on every other column only.
        if (((ground.getX() + ground.getZ()) & 1) == 0) {
            w.spawnParticle(Particle.BLOCK, spawn.getX() + 0.5, spawn.getY() + 0.1, spawn.getZ() + 0.5,
                    DUST_COUNT, 0.4, 0.1, 0.4, 0.0, data, true);
        }
        return display;
    }

    private static Transformation placed(float centerY, Quaternionf rotation) {
        float half = CUBE_SCALE / 2f;
        Vector3f rotatedHalf = new Vector3f(half, half, half).rotate(rotation);
        Vector3f translation = new Vector3f(0.5f - rotatedHalf.x, centerY - rotatedHalf.y, 0.5f - rotatedHalf.z);
        return new Transformation(translation, rotation,
                new Vector3f(CUBE_SCALE, CUBE_SCALE, CUBE_SCALE), new Quaternionf());
    }

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

    private void launchNearby(Player caster, Location origin, List<LivingEntity> candidates, Block ground,
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
            TrueDamage.of(damageAt(origin, entity)).attacker(caster).ability(getName()).apply(entity);

            entity.setVelocity(new Vector(outwardX * LAUNCH_OUTWARD, LAUNCH_UP, outwardZ * LAUNCH_OUTWARD));
        }
    }

    private static double damageAt(Location origin, LivingEntity entity) {
        Location at = entity.getLocation();
        double dx = at.getX() - origin.getX();
        double dz = at.getZ() - origin.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double t = Math.min(1.0, distance / MAX_RANGE);
        return DAMAGE + (DAMAGE_AT_MAX_RANGE - DAMAGE) * t;
    }

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