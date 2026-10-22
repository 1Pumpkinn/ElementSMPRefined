package net.rose.elementSMPRefined.ability.main.upgraded.metal;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.util.damage.TrueDamage;
import net.rose.elementSMPRefined.util.sound.SoundUtils;
import net.rose.elementSMPRefined.util.visual.model.MagnetarVisual;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
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
 * Metal element ability 2: plants an Magnetar Core ({@link MagnetarVisual}) where the caster is
 * looking. For {@value #DURATION_TICKS} ticks it drags every nearby living entity - players and
 * mobs alike, except the caster, anyone they trust, their tamed pets and armor stands - toward
 * it, pulling harder the closer they get. Anything that ends up fully inside the core
 * ({@value #CAPTURE_RADIUS} blocks from its centre) is pinned there and takes true damage every
 * {@value #DAMAGE_INTERVAL_TICKS} ticks until the core collapses or it breaks free.
 * <p>
 * Damage goes through {@link TrueDamage}, so a Metal Shield counts it as a damaging ability.
 */
public class MetalMagnetarAbility extends BaseAbility {

    private static final double RANGE = 25.0;               // how far away the core can be placed on a surface
    private static final double AIR_RANGE = 12.0;           // how far away it floats when you aim at open sky (so it can't go as high)
    private static final double HOVER = 1.2;                // how far off the surface it floats (about chest height off the floor)
    private static final int DURATION_TICKS = 5 * 20;
    private static final int COOLDOWN_SECONDS = 40;

    private static final double PULL_RADIUS = 16.0;         // pull range in every direction when the core sits on a surface
    private static final double AIR_PULL_HORIZONTAL = 9.0;  // when it floats in the air the field is tall and narrow instead:
    private static final double AIR_PULL_VERTICAL = 24.0;   // less sideways reach, much more up/down reach
    private static final double PULL_SPEED_FAR = 0.30;      // blocks/tick at the edge of the field...
    private static final double PULL_SPEED_NEAR = 0.80;     // ...and right next to the core
    private static final double MAX_SPEED = 0.9;
    private static final double CAPTURE_RADIUS = 1.3;       // "fully in" the core

    private static final double DAMAGE = 1.0;               // per hit while captured (half a heart)
    private static final int DAMAGE_INTERVAL_TICKS = 10;

    // Magnetar visuals: a glowing core wrapped in dipole magnetic field loops (they bunch together at the two
    // poles and balloon out around the equator), twin jets shooting out of the poles along the magnetic
    // axis, and a few faint streams of sparks curling in from the pull field so the suction still reads.
    // The whole field is tilted off vertical and its axis slowly sweeps around, like a pulsar's beam.
    private static final double[] LOOP_RADIUS = {2.0, 3.1, 4.4}; // how far each loop bulges out at the equator (blocks)
    private static final int LOOPS_PER_SHELL = 6;               // field lines spread evenly around the axis
    private static final int LOOP_POINTS = 4;                   // particles per field line per tick
    private static final double LOOP_FLOW = 0.02;               // fraction of a loop the sparks travel per tick (pole to pole)
    private static final double LOOP_SPIN = 0.05;               // radians/tick the loops turn about the magnetic axis
    private static final double FIELD_INNER = 0.8;              // loops start just outside the core model
    private static final double AXIS_TILT = Math.toRadians(25); // magnetic axis tilt off vertical
    private static final double AXIS_PRECESSION = 0.04;         // radians/tick the tilted axis sweeps around
    private static final int JET_POINTS = 4;                    // per jet per tick
    private static final double JET_LENGTH = 7.0;
    private static final double JET_FLOW = 0.08;                // fraction of the jet travelled outward per tick
    private static final int SHIMMER_POINTS = 4;                // sparks crawling over the core's surface per tick
    private static final double SHIMMER_RADIUS = 0.7;

    private static final int STREAMS = 6;
    private static final int STREAM_POINTS = 2;             // particles per stream per tick
    private static final double STREAM_MIN_RADIUS = 1.8;
    private static final double STREAM_CURL = 0.30;         // radians of swirl per block of radius
    private static final double STREAM_SWIRL_WIDTH = 0.20;  // how far a stream bows off its straight line (fraction of its radius)
    private static final double STREAM_FLOW = 0.03;         // fraction of the stream that flows inward per tick
    private static final double FIELD_SPIN = 0.03;          // radians/tick the whole stream field turns about the vertical axis

    /** Field line colours: deep blue out at the equator, hot white-cyan where the lines pinch together at the poles. */
    private static final int[] EQUATOR_RGB = {35, 85, 255};
    private static final int[] POLE_RGB = {215, 248, 255};

    /** Stream directions (spread evenly over a sphere) and a perpendicular pair for each, for the swirl. */
    private static final Vector[] STREAM_DIR = new Vector[STREAMS];
    private static final Vector[] STREAM_U = new Vector[STREAMS];
    private static final Vector[] STREAM_V = new Vector[STREAMS];

    static {
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < STREAMS; i++) {
            double y = 1.0 - 2.0 * (i + 0.5) / STREAMS;
            double ring = Math.sqrt(1.0 - y * y);
            double theta = i * goldenAngle;
            Vector dir = new Vector(Math.cos(theta) * ring, y, Math.sin(theta) * ring);
            Vector helper = Math.abs(y) > 0.9 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
            Vector u = dir.clone().crossProduct(helper).normalize();
            Vector v = dir.clone().crossProduct(u).normalize();
            STREAM_DIR[i] = dir;
            STREAM_U[i] = u;
            STREAM_V[i] = v;
        }
    }

    private final ElementSMPRefined plugin;
    private final Map<UUID, Core> cores = new HashMap<>();

    private static final class Core {
        final Location centre;
        final MagnetarVisual visual;
        final TrustManager trust;
        /** Pull reach sideways / up-and-down. Equal on a surface, tall and narrow in the air. */
        final double radiusH;
        final double radiusV;
        /** Tick at which each captured player may next be hit. */
        final Map<UUID, Integer> nextHit = new HashMap<>();
        int ticks = 0;
        BukkitRunnable task;

        Core(Location centre, MagnetarVisual visual, TrustManager trust, double radiusH, double radiusV) {
            this.centre = centre;
            this.visual = visual;
            this.trust = trust;
            this.radiusH = radiusH;
            this.radiusV = radiusV;
        }
    }

    /** Where the core goes, and whether it's floating in open air (true) or hovering off a surface (false). */
    private record Placement(Location location, boolean airborne) {
    }

    public MetalMagnetarAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("metal_magnetar_core", ElementType.METAL, 2, COOLDOWN_SECONDS, 2, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player caster = context.getPlayer();
        if (caster == null) return false;

        end(caster.getUniqueId(), false); // one core per caster

        Placement placement = findPlacement(caster);
        Location centre = placement.location();
        boolean air = placement.airborne();
        Core core = new Core(centre, new MagnetarVisual(centre), context.getTrustManager(),
                air ? AIR_PULL_HORIZONTAL : PULL_RADIUS,
                air ? AIR_PULL_VERTICAL : PULL_RADIUS);
        cores.put(caster.getUniqueId(), core);
        setActive(caster, true);

        SoundUtils.playTo(caster, SoundUtils.Element.METAL);
        centre.getWorld().playSound(centre, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
        centre.getWorld().spawnParticle(Particle.BLOCK, centre, 25, 0.4, 0.4, 0.4, 0.1,
                Material.IRON_BLOCK.createBlockData(), true);

        core.task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!caster.isOnline() || core.ticks >= DURATION_TICKS) {
                    end(caster.getUniqueId(), core.ticks >= DURATION_TICKS);
                    return;
                }
                core.visual.update(core.ticks);
                tick(core, caster);
                core.ticks++;
            }
        };
        core.task.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /** Where the caster is looking (up to RANGE), floated off the surface it hit; AIR_RANGE blocks out in the air if nothing is hit (then the pull field is tall instead of round). */
    private Placement findPlacement(Player caster) {
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection();

        RayTraceResult hit = caster.getWorld().rayTraceBlocks(
                eye, direction, RANGE, FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitPosition() == null) {
            Location far = eye.clone().add(direction.clone().multiply(AIR_RANGE));
            far.setYaw(0f); // the visual keeps entity rotation at 0
            far.setPitch(0f);
            return new Placement(far, true);
        }

        Vector normal = hit.getHitBlockFace() != null
                ? hit.getHitBlockFace().getDirection()
                : new Vector(0, 1, 0);
        return new Placement(hit.getHitPosition().clone().add(normal.multiply(HOVER)).toLocation(caster.getWorld()), false);
    }


    private void tick(Core core, Player caster) {
        World world = core.centre.getWorld();

        for (LivingEntity target : world.getNearbyLivingEntities(core.centre, core.radiusH, core.radiusV, core.radiusH)) {
            if (!isValidTarget(core, caster, target)) continue;

            Vector toCore = core.centre.toVector().subtract(target.getBoundingBox().getCenter());
            double distance = toCore.length();

            // Distance measured in the (possibly stretched) pull field, rescaled so PULL_RADIUS is its edge.
            double fx = toCore.getX() / core.radiusH;
            double fy = toCore.getY() / core.radiusV;
            double fz = toCore.getZ() / core.radiusH;
            double fieldDistance = Math.sqrt(fx * fx + fy * fy + fz * fz) * PULL_RADIUS;
            if (fieldDistance > PULL_RADIUS) continue;

            if (distance <= CAPTURE_RADIUS) {
                capture(core, caster, target, toCore);
            } else {
                pull(target, toCore, fieldDistance);
            }
        }

        spawnSuctionParticles(core);
        if (core.ticks % 20 == 0) {
            world.playSound(core.centre, Sound.BLOCK_BEACON_AMBIENT, 0.8f, 1.6f);
        }
    }

    /** Drags the target toward the core, harder the closer they are. */
    private void pull(LivingEntity target, Vector toCore, double distance) {
        double closeness = 1.0 - Math.min(1.0, distance / PULL_RADIUS);
        double speed = PULL_SPEED_FAR + (PULL_SPEED_NEAR - PULL_SPEED_FAR) * closeness;

        // Keep a little of their own momentum so it feels like a current, not a rail.
        Vector velocity = target.getVelocity().multiply(0.4).add(toCore.normalize().multiply(speed));
        if (velocity.length() > MAX_SPEED) {
            velocity.normalize().multiply(MAX_SPEED);
        }
        // Lift off the floor, otherwise ground friction stalls the pull.
        if (target.isOnGround() && velocity.getY() < 0.1) {
            velocity.setY(0.1);
        }
        target.setVelocity(velocity);
    }

    /** The target is fully inside the core: hold them at its centre and crush them on a timer. */
    private void capture(Core core, Player caster, LivingEntity target, Vector toCore) {
        UUID id = target.getUniqueId();

        if (core.ticks >= core.nextHit.getOrDefault(id, 0)) {
            core.nextHit.put(id, core.ticks + DAMAGE_INTERVAL_TICKS);
            TrueDamage.of(DAMAGE).attacker(caster).ability(getName()).ignoreIFrames(true).apply(target);

            Location at = target.getLocation().add(0, 1.0, 0);
            at.getWorld().spawnParticle(Particle.CRIT, at, 12, 0.3, 0.5, 0.3, 0.2);
            at.getWorld().playSound(at, Sound.ENTITY_IRON_GOLEM_DAMAGE, 0.8f, 0.8f);
        }

        // Set after the damage: its knockback would otherwise shove them out of the core.
        target.setVelocity(toCore.multiply(0.4));
    }

    private boolean isValidTarget(Core core, Player caster, LivingEntity target) {
        if (target.equals(caster) || !target.isValid() || target.isDead()) return false;
        if (target instanceof ArmorStand) return false;

        if (target instanceof Player player) {
            GameMode mode = player.getGameMode();
            if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return false;
            return !isTrusted(core, caster, player.getUniqueId());
        }

        // Mobs are fair game, but leave the caster's (and their trusted friends') tamed pets alone.
        if (target instanceof Tameable pet && pet.getOwnerUniqueId() != null) {
            UUID owner = pet.getOwnerUniqueId();
            return !owner.equals(caster.getUniqueId()) && !isTrusted(core, caster, owner);
        }
        return true;
    }

    private boolean isTrusted(Core core, Player caster, UUID other) {
        return core.trust != null && core.trust.isTrusted(caster.getUniqueId(), other);
    }

    /**
     * Draws the core as a magnetar: dipole field loops, polar jets, a shimmering surface and (faintly)
     * the inward streams that show where the pull is coming from.
     */
    private void spawnSuctionParticles(Core core) {
        World world = core.centre.getWorld();
        double precession = core.ticks * AXIS_PRECESSION;
        double loopSpin = core.ticks * LOOP_SPIN;

        spawnFieldLoops(core, world, loopSpin, precession);
        spawnJets(core, world, precession);
        spawnShimmer(core, world);
        spawnInflow(core, world);
    }

    /**
     * Dipole field lines: r = L * sin^2(theta) measured from the magnetic axis, so every line leaves one
     * pole, swings out to a radius of L at the equator and dives back into the opposite pole. Sparks flow
     * along each line, hot near the poles and cool at the equator.
     */
    private void spawnFieldLoops(Core core, World world, double loopSpin, double precession) {
        double flow = (core.ticks * LOOP_FLOW) % 1.0;

        for (int shell = 0; shell < LOOP_RADIUS.length; shell++) {
            double reach = LOOP_RADIUS[shell];
            double thetaMin = Math.asin(Math.sqrt(FIELD_INNER / reach)); // where the line meets the core

            for (int line = 0; line < LOOPS_PER_SHELL; line++) {
                // stagger shells around the axis so the loops don't sit on top of each other
                double phi = (line + shell * 0.5) * (Math.PI * 2 / LOOPS_PER_SHELL);

                for (int i = 0; i < LOOP_POINTS; i++) {
                    double t = ((double) i / LOOP_POINTS + flow + shell * 0.31 + line * 0.17) % 1.0;
                    double theta = thetaMin + t * (Math.PI - 2 * thetaMin);
                    double sin = Math.sin(theta);

                    Vector local = new Vector(
                            reach * sin * sin * sin * Math.cos(phi),
                            reach * sin * sin * Math.cos(theta),
                            reach * sin * sin * sin * Math.sin(phi));
                    Vector offset = orient(local, loopSpin, precession);

                    Particle.DustOptions dust = new Particle.DustOptions(
                            fieldColor(Math.abs(Math.cos(theta))), 0.7f);
                    world.spawnParticle(Particle.DUST, core.centre.clone().add(offset), 1, 0, 0, 0, 0, dust);
                }
            }
        }
    }

    /** Thin beams of light blasting out of both magnetic poles, widening very slightly with distance. */
    private void spawnJets(Core core, World world, double precession) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vector axis = orient(new Vector(0, 1, 0), 0, precession);
        double flow = (core.ticks * JET_FLOW) % 1.0;

        for (int sign = -1; sign <= 1; sign += 2) {
            for (int i = 0; i < JET_POINTS; i++) {
                double f = ((double) i / JET_POINTS + flow) % 1.0;
                double distance = FIELD_INNER + f * (JET_LENGTH - FIELD_INNER);
                double spread = 0.02 + distance * 0.025;

                Vector offset = axis.clone().multiply(sign * distance).add(new Vector(
                        random.nextDouble(-spread, spread),
                        random.nextDouble(-spread, spread),
                        random.nextDouble(-spread, spread)));
                world.spawnParticle(Particle.END_ROD, core.centre.clone().add(offset), 1, 0, 0, 0, 0);
            }
        }
    }

    /** Crackling sparks over the surface of the star itself. */
    private void spawnShimmer(Core core, World world) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < SHIMMER_POINTS; i++) {
            Vector dir = new Vector(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
            if (dir.lengthSquared() < 1.0E-6) continue;
            dir.normalize().multiply(SHIMMER_RADIUS);
            world.spawnParticle(Particle.ELECTRIC_SPARK, core.centre.clone().add(dir), 1, 0, 0, 0, 0);
        }
    }

    /** Faint streams spiralling in from every direction, stretched to match the pull field. */
    private void spawnInflow(Core core, World world) {
        double flow = (core.ticks * STREAM_FLOW) % 1.0;
        double spin = core.ticks * FIELD_SPIN;
        Vector fieldScale = new Vector(core.radiusH / PULL_RADIUS, core.radiusV / PULL_RADIUS, core.radiusH / PULL_RADIUS);
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(110, 170, 255), 0.5f);

        for (int s = 0; s < STREAMS; s++) {
            double phase = (s * 0.37) % 1.0; // stagger the streams so they don't pulse in unison
            for (int i = 0; i < STREAM_POINTS; i++) {
                // fraction along the stream, drifting inward over time; squared so points bunch up near the core
                double f = ((double) i / STREAM_POINTS - flow + phase + 2.0) % 1.0;
                double radius = STREAM_MIN_RADIUS + f * f * (PULL_RADIUS - STREAM_MIN_RADIUS);

                double swirl = radius * STREAM_CURL + s * 0.5;
                double bow = radius * STREAM_SWIRL_WIDTH;
                Vector offset = STREAM_DIR[s].clone().multiply(radius)
                        .add(STREAM_U[s].clone().multiply(Math.cos(swirl) * bow))
                        .add(STREAM_V[s].clone().multiply(Math.sin(swirl) * bow))
                        .rotateAroundY(spin)
                        .multiply(fieldScale);
                world.spawnParticle(Particle.DUST, core.centre.clone().add(offset), 1, 0, 0, 0, 0, dust);
            }
        }
    }

    /** Spins a magnetic-axis-local vector about the axis (local Y), tips the axis over, then sweeps it around. */
    private static Vector orient(Vector local, double spinAboutAxis, double precession) {
        return local.rotateAroundY(spinAboutAxis).rotateAroundZ(AXIS_TILT).rotateAroundY(precession);
    }

    /** {@code polar} is 1 right at a pole and 0 on the equator. */
    private static Color fieldColor(double polar) {
        double k = polar * polar; // keep most of the loop blue, only the pinched ends go white-hot
        return Color.fromRGB(
                (int) (EQUATOR_RGB[0] + (POLE_RGB[0] - EQUATOR_RGB[0]) * k),
                (int) (EQUATOR_RGB[1] + (POLE_RGB[1] - EQUATOR_RGB[1]) * k),
                (int) (EQUATOR_RGB[2] + (POLE_RGB[2] - EQUATOR_RGB[2]) * k));
    }


    private void end(UUID casterId, boolean collapsed) {
        Core core = cores.remove(casterId);
        if (core == null) return;

        if (core.task != null) core.task.cancel();
        core.visual.remove();

        Player caster = plugin.getServer().getPlayer(casterId);
        if (caster != null) super.setActive(caster, false);

        if (collapsed) {
            World world = core.centre.getWorld();
            world.playSound(core.centre, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.2f);
            world.spawnParticle(Particle.BLOCK, core.centre, 40, 0.5, 0.5, 0.5, 0.15,
                    Material.IRON_BLOCK.createBlockData(), true);
            world.spawnParticle(Particle.POOF, core.centre, 10, 0.4, 0.4, 0.4, 0.05);
        }
    }

    /** Called by {@code BaseElement.clearEffects} (element change etc.) - drops the core with the flag. */
    @Override
    public void setActive(Player player, boolean active) {
        super.setActive(player, active);
        if (!active) {
            end(player.getUniqueId(), false);
        }
    }

    /** Caster left the server: the core goes with them. */
    public void onPlayerQuit(UUID playerUuid) {
        end(playerUuid, false);
    }

    @Override
    public String getName() {
        return ChatColor.GRAY + "Magnetar";
    }

    @Override
    public String getDescription() {
        return "Places a core that pulls in nearby players and mobs and hurts them once they are inside it.";
    }
}