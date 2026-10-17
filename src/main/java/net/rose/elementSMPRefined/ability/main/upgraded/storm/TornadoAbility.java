package net.rose.elementSMPRefined.ability.main.upgraded.storm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.util.damage.TrueDamage;
import net.rose.elementSMPRefined.util.visual.model.TornadoVisual;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Storm element ability 2: summons a tornado where the caster is looking. It grows in, then roams
 * for {@value #DURATION_TICKS} ticks - drifting along the ground, meandering, bouncing off walls
 * and lazily curving toward the nearest enemy player - before dying down.
 * <p>
 * Everything near it (players and mobs, except the caster, anyone they trust, tamed pets and
 * armor stands) is sucked toward it, swirling as they go. Anyone who ends up inside the funnel is
 * lifted, spun round its axis and takes true damage every {@value #DAMAGE_INTERVAL_TICKS} ticks.
 * <p>
 * The funnel is the {@code elementsmp:tornado_funnel} resource-pack model drawn by
 * {@link TornadoVisual}; the debris, dust and crackle around it are particles. Damage goes through
 * {@link TrueDamage}, so a Metal Shield counts it as a damaging ability.
 */
public class TornadoAbility extends BaseAbility {

    // ---- Cast / lifetime ----
    private static final int COOLDOWN_SECONDS = 40;
    private static final int DURATION_TICKS = 8 * 20;
    /** Ticks to grow to full size / to shrink away at the end. */
    private static final int GROW_TICKS = 15;
    private static final int FADE_TICKS = 15;
    /** How far away the tornado can be summoned (blocks). */
    private static final double CAST_RANGE = 22.0;
    /** How far down it searches for ground below where you aimed (blocks). */
    private static final double CAST_GROUND_SEARCH = 40.0;

    // ---- Movement ----
    /** Blocks per tick at full size (3.4 blocks/s - slower than a sprinting player). */
    private static final double SPEED = 0.17;
    /** Random wobble added to its heading every tick while wandering (radians, +/-). */
    private static final double WANDER_JITTER = Math.toRadians(3.5);
    /** Fastest it can steer toward a target / back to the caster (radians per tick). */
    private static final double TURN_RATE = Math.toRadians(2.5);
    /** It starts curving toward the nearest enemy player inside this range (blocks). Set to 0 to never chase. */
    private static final double HOMING_RANGE = 18.0;
    /** It curves back toward the caster if it gets further away than this (blocks). */
    private static final double LEASH = 40.0;
    /** Highest ledge it will climb in one step (blocks). Anything taller counts as a wall. */
    private static final double MAX_STEP_UP = 1.2;
    /** Most it will drop to follow the ground each tick (blocks). */
    private static final double MAX_DROP_PER_TICK = 0.4;
    /** How far down it looks for ground while travelling - i.e. the biggest drop it will follow (blocks). */
    private static final double GROUND_SEARCH = 9.0;
    /** How often (ticks) it re-picks a player to chase. */
    private static final int RETARGET_INTERVAL = 5;

    // ---- Suction ----
    /** Sideways reach of the pull (blocks). */
    private static final double PULL_RADIUS = 9.0;
    private static final double PULL_SPEED_FAR = 0.20;    // blocks/tick at the edge of the pull...
    private static final double PULL_SPEED_NEAR = 0.50;   // ...and right next to the funnel
    private static final double PULL_SWIRL = 0.22;        // sideways drift while being pulled in (reads as a spiral)
    private static final double MAX_SPEED = 0.75;

    // ---- Inside the funnel ----
    /** Extra width on the funnel wall that still counts as "in it" (blocks). The base is thin, so this keeps it forgiving. */
    private static final double INSIDE_MARGIN = 0.8;
    private static final double ORBIT_SPEED = 0.45;       // how fast they're whipped round the axis
    private static final double LIFT_SPEED = 0.42;
    /** Highest they're carried above the ground (blocks); they hover and bob around this height. */
    private static final double LIFT_MAX = 5.5;
    private static final double DAMAGE = 1.5;             // true damage per hit (0.75 hearts)
    private static final int DAMAGE_INTERVAL_TICKS = 10;

    // ---- Visuals ----
    private static final int SPIRAL_POINTS = 8;           // cloud particles per tick on the spiral up the funnel
    private static final int DEBRIS_POINTS = 4;           // block-crumb particles per tick flung around inside it

    private final ElementSMPRefined plugin;
    private final Map<UUID, Tornado> tornadoes = new HashMap<>();

    private static final class Tornado {
        final World world;
        /** Ground contact point of the funnel. */
        final Location pos;
        final TornadoVisual visual;
        final TrustManager trust;
        /** Travel direction as an angle: x = cos(heading), z = sin(heading). */
        double heading;
        int ticks = 0;
        /** The player it is currently curving toward, if any. */
        Player chasing;
        /** Tick at which each caught entity may next be hit. */
        final Map<UUID, Integer> nextHit = new HashMap<>();
        BukkitRunnable task;

        Tornado(World world, Location pos, TornadoVisual visual, TrustManager trust, double heading) {
            this.world = world;
            this.pos = pos;
            this.visual = visual;
            this.trust = trust;
            this.heading = heading;
        }
    }

    public TornadoAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("storm_tornado", ElementType.STORM, 2, COOLDOWN_SECONDS, 2, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player caster = context.getPlayer();
        if (caster == null) return false;

        end(caster.getUniqueId(), false); // one tornado per caster

        Location start = findStart(caster);
        if (start == null) {
            caster.sendMessage(Component.text("There's no ground there to summon a tornado on.", NamedTextColor.GRAY));
            return false;
        }

        // It sets off in the direction the caster is facing.
        Location facing = caster.getLocation();
        facing.setPitch(0f);
        Vector look = facing.getDirection();
        double heading = Math.atan2(look.getZ(), look.getX());

        Tornado tornado = new Tornado(start.getWorld(), start, new TornadoVisual(start, caster),
                context.getTrustManager(), heading);
        tornadoes.put(caster.getUniqueId(), tornado);
        setActive(caster, true);

        World world = tornado.world;
        world.playSound(start, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0f, 0.6f);
        world.playSound(start, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4f, 0.7f);

        tornado.task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!caster.isOnline() || tornado.ticks >= DURATION_TICKS) {
                    end(caster.getUniqueId(), tornado.ticks >= DURATION_TICKS);
                    return;
                }
                tick(tornado, caster);
                tornado.ticks++;
            }
        };
        tornado.task.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /**
     * Where the tornado first appears: the block the caster is looking at (or {@value #CAST_RANGE}
     * blocks out if they're looking at open air), dropped down to the ground. Null if there's no
     * ground to be found.
     */
    private Location findStart(Player caster) {
        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection();

        RayTraceResult hit = world.rayTraceBlocks(eye, direction, CAST_RANGE, FluidCollisionMode.NEVER, true);
        Vector point;
        double searchFrom;
        if (hit != null && hit.getHitPosition() != null) {
            point = hit.getHitPosition().clone();
            BlockFace face = hit.getHitBlockFace();
            if (face != null) {
                point.add(face.getDirection().multiply(0.6)); // back off the surface so we start in open air
            }
            searchFrom = point.getY() + (face == BlockFace.DOWN ? -0.2 : 0.5);
        } else {
            point = eye.toVector().add(direction.clone().multiply(CAST_RANGE));
            searchFrom = point.getY();
        }

        Double ground = groundY(world, point.getX(), searchFrom, point.getZ(), CAST_GROUND_SEARCH);
        if (ground == null) return null;
        return new Location(world, point.getX(), ground, point.getZ());
    }

    private void tick(Tornado t, Player caster) {
        double growth = growth(t.ticks);

        steer(t, caster);
        move(t, growth);

        t.visual.moveTo(t.pos);
        t.visual.update(t.ticks, growth);

        suck(t, caster, growth);

        spawnParticles(t, growth);
        if (t.ticks % 10 == 0) {
            t.world.playSound(t.pos, Sound.ITEM_ELYTRA_FLYING, 1.2f, 0.5f);
        }
    }

    /** 0 -> 1 as it grows in, 1 while it roams, 1 -> 0 as it dies down. */
    private static double growth(int ticks) {
        double in = Math.min(1.0, ticks / (double) GROW_TICKS);
        double out = Math.min(1.0, (DURATION_TICKS - ticks) / (double) FADE_TICKS);
        return Math.max(0.02, Math.min(in, out));
    }

    // ------------------------------------------------------------------------------------------
    // Movement
    // ------------------------------------------------------------------------------------------

    /**
     * Decides where it's heading. Priority: back toward the caster if it strayed too far, then
     * curving toward the nearest enemy player, otherwise a random wander (a slow random walk on
     * its heading, which gives a meandering path rather than a straight line).
     */
    private void steer(Tornado t, Player caster) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (caster.getWorld().equals(t.world)) {
            double cx = caster.getLocation().getX() - t.pos.getX();
            double cz = caster.getLocation().getZ() - t.pos.getZ();
            if (cx * cx + cz * cz > LEASH * LEASH) {
                turnToward(t, Math.atan2(cz, cx));
                return;
            }
        }

        if (HOMING_RANGE > 0 && t.ticks % RETARGET_INTERVAL == 0) {
            t.chasing = nearestEnemy(t, caster);
        }
        Player chasing = t.chasing;
        if (chasing != null && chasing.isOnline() && chasing.getWorld().equals(t.world)) {
            double dx = chasing.getLocation().getX() - t.pos.getX();
            double dz = chasing.getLocation().getZ() - t.pos.getZ();
            turnToward(t, Math.atan2(dz, dx));
            return;
        }

        t.heading += (random.nextDouble() - 0.5) * 2.0 * WANDER_JITTER;
    }

    private Player nearestEnemy(Tornado t, Player caster) {
        Player best = null;
        double bestDistance = HOMING_RANGE * HOMING_RANGE;
        for (Player candidate : t.world.getNearbyPlayers(t.pos, HOMING_RANGE)) {
            if (!canAffect(t, caster, candidate)) continue;
            double dx = candidate.getLocation().getX() - t.pos.getX();
            double dz = candidate.getLocation().getZ() - t.pos.getZ();
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /** Rotates the heading toward {@code wanted} by at most {@link #TURN_RATE}, the short way round. */
    private static void turnToward(Tornado t, double wanted) {
        double diff = wanted - t.heading;
        while (diff > Math.PI) diff -= Math.PI * 2;
        while (diff < -Math.PI) diff += Math.PI * 2;
        t.heading += Math.max(-TURN_RATE, Math.min(TURN_RATE, diff));
    }

    /** Slides along the ground in its heading; turns away instead if there's a wall, a cliff or unloaded ground ahead. */
    private void move(Tornado t, double growth) {
        double speed = SPEED * Math.max(0.35, growth); // slow while still forming / fading
        double dx = Math.cos(t.heading) * speed;
        double dz = Math.sin(t.heading) * speed;
        double nx = t.pos.getX() + dx;
        double nz = t.pos.getZ() + dz;

        if (!t.world.isChunkLoaded(((int) Math.floor(nx)) >> 4, ((int) Math.floor(nz)) >> 4)) {
            bounce(t);
            return;
        }

        // Wall check above step height (so 1-block ledges are climbed, not bounced off), a little
        // further than the step so it turns before it clips in.
        Vector ahead = new Vector(dx, 0, dz).normalize();
        Location chest = t.pos.clone().add(0, 1.5, 0);
        if (t.world.rayTraceBlocks(chest, ahead, speed + 0.9, FluidCollisionMode.NEVER, true) != null) {
            bounce(t);
            return;
        }

        Double ground = groundY(t.world, nx, t.pos.getY(), nz, GROUND_SEARCH + 2.0);
        if (ground == null || ground - t.pos.getY() > MAX_STEP_UP) {
            bounce(t); // void/pit with no floor, or a ledge too tall to climb
            return;
        }

        double dy = Math.max(-MAX_DROP_PER_TICK, Math.min(MAX_DROP_PER_TICK, ground - t.pos.getY()));
        t.pos.add(dx, dy, dz);
    }

    /** Blocked: swing round roughly 90 degrees either way and try again next tick. */
    private static void bounce(Tornado t) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double turn = Math.PI / 2 + random.nextDouble() * 0.8;
        t.heading += random.nextBoolean() ? turn : -turn;
    }

    /**
     * Y of the ground (or water surface) below {@code (x, fromY + 2, z)}, searching down
     * {@code depth} blocks. Null if there's nothing there.
     */
    private static Double groundY(World world, double x, double fromY, double z, double depth) {
        Location from = new Location(world, x, fromY + 2.0, z);
        RayTraceResult hit = world.rayTraceBlocks(
                from, new Vector(0, -1, 0), depth + 2.0, FluidCollisionMode.ALWAYS, true);
        if (hit == null || hit.getHitPosition() == null) return null;
        return hit.getHitPosition().getY();
    }

    // ------------------------------------------------------------------------------------------
    // Suction + damage
    // ------------------------------------------------------------------------------------------

    private void suck(Tornado t, Player caster, double growth) {
        Location middle = t.pos.clone().add(0, TornadoVisual.HEIGHT / 2.0, 0);
        double reach = TornadoVisual.HEIGHT / 2.0 + 2.0;

        for (LivingEntity target : t.world.getNearbyLivingEntities(middle, PULL_RADIUS, reach, PULL_RADIUS)) {
            if (!canAffect(t, caster, target)) continue;

            Location at = target.getLocation();
            double dx = t.pos.getX() - at.getX();
            double dz = t.pos.getZ() - at.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > PULL_RADIUS) continue;

            double height = at.getY() - t.pos.getY(); // how far above the tornado's base they are
            if (height < -1.5 || height > TornadoVisual.HEIGHT * growth + 2.0) continue;

            // Unit vector toward the axis, and the one at 90 degrees to it (the direction to swirl in).
            Vector inward = distance > 1.0E-4 ? new Vector(dx / distance, 0, dz / distance) : new Vector();
            Vector swirl = new Vector(-inward.getZ(), 0, inward.getX());

            double funnel = TornadoVisual.radiusAt(Math.max(0.0, height) / TornadoVisual.HEIGHT) * growth;
            boolean inside = distance <= funnel + INSIDE_MARGIN
                    && height <= TornadoVisual.HEIGHT * growth;

            if (inside) {
                caught(t, caster, target, inward, swirl, distance, funnel, height);
            } else {
                pull(target, inward, swirl, distance, growth);
            }
        }
    }

    /** Drags the target toward the funnel, harder the closer they are, curving as they come. */
    private void pull(LivingEntity target, Vector inward, Vector swirl, double distance, double growth) {
        double closeness = 1.0 - Math.min(1.0, distance / PULL_RADIUS);
        double speed = (PULL_SPEED_FAR + (PULL_SPEED_NEAR - PULL_SPEED_FAR) * closeness) * growth;

        // Keep a little of their own momentum so it feels like a current, not a rail.
        Vector velocity = target.getVelocity().multiply(0.5)
                .add(inward.clone().multiply(speed))
                .add(swirl.clone().multiply(PULL_SWIRL * closeness * growth));
        if (velocity.length() > MAX_SPEED) {
            velocity.normalize().multiply(MAX_SPEED);
        }
        // Lift off the floor, otherwise ground friction stalls the pull.
        if (target.isOnGround() && velocity.getY() < 0.12) {
            velocity.setY(0.12);
        }
        target.setVelocity(velocity);
    }

    /**
     * The target is inside the funnel: whip them round the axis, carry them up to {@link #LIFT_MAX}
     * (then hold them there, bobbing) and hit them on a timer.
     */
    private void caught(Tornado t, Player caster, LivingEntity target, Vector inward, Vector swirl,
                        double distance, double funnel, double height) {
        UUID id = target.getUniqueId();

        if (t.ticks >= t.nextHit.getOrDefault(id, 0)) {
            t.nextHit.put(id, t.ticks + DAMAGE_INTERVAL_TICKS);
            TrueDamage.of(DAMAGE).attacker(caster).ignoreIFrames(true).apply(target);

            Location chest = target.getLocation().add(0, 1.0, 0);
            t.world.spawnParticle(Particle.CRIT, chest, 10, 0.3, 0.5, 0.3, 0.2);
            t.world.playSound(chest, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.2f);
        }

        // Orbit around the axis, drifting toward a ring about 60% of the way out to the funnel wall
        // (pushes them back out if they sit right on the axis, so they circle instead of stacking up).
        double ringRadius = Math.max(0.6, funnel * 0.6);
        double radial = (distance - ringRadius) * 0.3;
        Vector horizontal = swirl.clone().multiply(ORBIT_SPEED).add(inward.clone().multiply(radial));

        // Up to LIFT_MAX, then hover. The hover bobs up AND down on purpose: vanilla kicks survival
        // players for "flying" after ~4s of never moving downward, and the dips reset that timer.
        double vertical;
        if (height < LIFT_MAX - 0.5) {
            vertical = LIFT_SPEED;
        } else {
            double bob = Math.sin(t.ticks * 0.2 + (id.hashCode() & 0xFF)) * 0.12;
            vertical = bob + (LIFT_MAX - height) * 0.1;
        }

        // Set after the damage: its knockback would otherwise shove them out of the funnel.
        target.setVelocity(new Vector(horizontal.getX(), vertical, horizontal.getZ()));
        target.setFallDistance(0f); // being carried up isn't falling
    }

    private boolean canAffect(Tornado t, Player caster, LivingEntity target) {
        if (target.equals(caster) || !target.isValid() || target.isDead()) return false;
        if (target instanceof ArmorStand) return false;

        if (target instanceof Player player) {
            GameMode mode = player.getGameMode();
            if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return false;
            return !isTrusted(t, caster, player.getUniqueId());
        }

        // Mobs are fair game, but leave the caster's (and their trusted friends') tamed pets alone.
        if (target instanceof Tameable pet && pet.getOwnerUniqueId() != null) {
            UUID owner = pet.getOwnerUniqueId();
            return !owner.equals(caster.getUniqueId()) && !isTrusted(t, caster, owner);
        }
        return true;
    }

    private boolean isTrusted(Tornado t, Player caster, UUID other) {
        return t.trust != null && t.trust.isTrusted(caster.getUniqueId(), other);
    }

    // ------------------------------------------------------------------------------------------
    // Particles
    // ------------------------------------------------------------------------------------------

    /**
     * Cloud spiralling up the outside of the funnel, ground debris flung around inside it, a dust
     * ring at the base and the odd crackle of lightning - the model is the body, this is the mess
     * around it.
     */
    private void spawnParticles(Tornado t, double growth) {
        World world = t.world;
        Location pos = t.pos;
        double height = TornadoVisual.HEIGHT * growth;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Spiral of cloud winding up the outside of the funnel.
        for (int i = 0; i < SPIRAL_POINTS; i++) {
            double f = ((double) i / SPIRAL_POINTS + t.ticks * 0.035) % 1.0;
            double angle = f * Math.PI * 6 + t.ticks * 0.5;
            double radius = TornadoVisual.radiusAt(f) * growth * 1.1 + 0.2;
            world.spawnParticle(Particle.CLOUD,
                    pos.getX() + Math.cos(angle) * radius, pos.getY() + f * height, pos.getZ() + Math.sin(angle) * radius,
                    1, 0.05, 0.05, 0.05, 0.0);
        }

        // Crumbs of whatever it's standing on, thrown around inside.
        BlockData ground = groundData(t);
        for (int i = 0; i < DEBRIS_POINTS; i++) {
            double f = random.nextDouble();
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = TornadoVisual.radiusAt(f) * growth * (0.3 + random.nextDouble() * 0.65);
            world.spawnParticle(Particle.BLOCK,
                    pos.getX() + Math.cos(angle) * radius, pos.getY() + f * height, pos.getZ() + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0, ground);
        }

        // Dust kicked up at the base.
        world.spawnParticle(Particle.CLOUD, pos.getX(), pos.getY() + 0.2, pos.getZ(),
                3, 1.0 * growth, 0.1, 1.0 * growth, 0.02);

        // Occasional lightning crackle inside the funnel (it's a Storm tornado).
        if (random.nextInt(18) == 0) {
            double f = 0.2 + random.nextDouble() * 0.8;
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = TornadoVisual.radiusAt(f) * growth * 0.7;
            Location spark = new Location(world,
                    pos.getX() + Math.cos(angle) * radius, pos.getY() + f * height, pos.getZ() + Math.sin(angle) * radius);
            world.spawnParticle(Particle.ELECTRIC_SPARK, spark, 10, 0.35, 0.35, 0.35, 0.3);
            world.playSound(spark, Sound.ITEM_TRIDENT_THUNDER, 0.25f, 1.5f);
        }
    }

    /** The block under the tornado, for the debris; plain dirt if it's over air or water. */
    private static BlockData groundData(Tornado t) {
        Block below = t.world.getBlockAt(
                (int) Math.floor(t.pos.getX()), (int) Math.floor(t.pos.getY() - 0.1), (int) Math.floor(t.pos.getZ()));
        Material type = below.getType();
        if (type.isAir() || !type.isSolid()) {
            return Material.DIRT.createBlockData();
        }
        return below.getBlockData();
    }

    // ------------------------------------------------------------------------------------------
    // Ending
    // ------------------------------------------------------------------------------------------

    private void end(UUID casterId, boolean collapsed) {
        Tornado tornado = tornadoes.remove(casterId);
        if (tornado == null) return;

        if (tornado.task != null) tornado.task.cancel();
        tornado.visual.remove();

        Player caster = plugin.getServer().getPlayer(casterId);
        if (caster != null) super.setActive(caster, false);

        if (collapsed) {
            World world = tornado.world;
            Location at = tornado.pos.clone().add(0, 1.0, 0);
            world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 0.7f);
            world.spawnParticle(Particle.CLOUD, at, 25, 1.0, 0.6, 1.0, 0.05);
            world.spawnParticle(Particle.POOF, at, 12, 0.8, 0.4, 0.8, 0.05);
        }
    }

    /** Called by {@code BaseElement.clearEffects} (element change, death etc.) - drops the tornado with the flag. */
    @Override
    public void setActive(Player player, boolean active) {
        super.setActive(player, active);
        if (!active) {
            end(player.getUniqueId(), false);
        }
    }

    @Override
    public String getName() {
        return ChatColor.YELLOW + "Tornado";
    }

    @Override
    public String getDescription() {
        return "Summon a tornado that roams the area, sucking in nearby players and mobs "
                + "and damaging everything caught inside it.";
    }
}
