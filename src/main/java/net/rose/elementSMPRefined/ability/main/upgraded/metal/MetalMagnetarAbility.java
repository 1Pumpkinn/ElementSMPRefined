package net.rose.elementSMPRefined.ability.main.upgraded.metal;

import net.rose.elementSMPRefined.util.damage.AbilityTargets;
import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.util.sound.SoundUtils;
import net.rose.elementSMPRefined.util.visual.model.MagnetarVisual;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class MetalMagnetarAbility extends BaseAbility {

    private static final double RANGE = 25.0;               // how far away the core can be placed on a surface
    private static final double AIR_RANGE = 12.0;           // how far away it floats when you aim at open sky (so it can't go as high)
    private static final double HOVER = 1.2;                // how far off the surface it floats (about chest height off the floor)
    private static final int DURATION_TICKS = 5 * 20;

    private static final double PULL_RADIUS = 16.0;         // pull range in every direction when the core sits on a surface
    private static final double AIR_PULL_HORIZONTAL = 9.0;  // when it floats in the air the field is tall and narrow instead:
    private static final double AIR_PULL_VERTICAL = 24.0;   // less sideways reach, much more up/down reach
    private static final double PULL_SPEED_FAR = 0.30;      // blocks/tick at the edge of the field...
    private static final double PULL_SPEED_NEAR = 0.80;     // ...and right next to the core
    private static final double MAX_SPEED = 0.9;
    private static final double ORBIT_RADIUS = 2.0;         // captured entities circle the core on a ring this wide (blocks)
    private static final double ORBIT_SPEED = 0.3;          // blocks/tick along the ring
    private static final double ORBIT_RADIAL_GAIN = 0.3;    // fraction of the radial / vertical error corrected per tick
    private static final double CAPTURE_RADIUS = ORBIT_RADIUS + 0.5; // "in" the core: close enough to be swept onto the ring
    private static final double APPROACH_FLOOR = 0.12;      // the pull eases off near the core: speed <= FLOOR + EASE * distance left,
    private static final double APPROACH_EASE = 0.30;       // so they arrive slowly instead of shooting through the core (client lag turns that into an orbit)
    private static final double ORBIT_MAX_SPEED = 0.6;      // blocks/tick cap on the orbit velocity
    private static final double LEASH = 1.5;                // captured entities further than this from their spot on the ring are snapped back onto it
    private static final int SETTLE_TICKS = 8;              // grace after capture before the leash applies, so they glide in rather than teleport in

    // Magnetar visuals: a glowing core wrapped in dipole magnetic field loops (they bunch together at the two
    // poles and balloon out around the equator), and a few faint streams of sparks curling in from the pull field so the suction still reads.
    // The whole field is tilted off vertical and its axis slowly sweeps around, like a pulsar's beam.
    private static final double[] LOOP_RADIUS = {2.0, 3.1, 4.4}; // how far each loop bulges out at the equator (blocks)
    private static final int LOOPS_PER_SHELL = 6;               // field lines spread evenly around the axis
    private static final int LOOP_POINTS = 4;                   // particles per field line per tick
    private static final double LOOP_FLOW = 0.02;               // fraction of a loop the sparks travel per tick (pole to pole)
    private static final double LOOP_SPIN = 0.05;               // radians/tick the loops turn about the magnetic axis
    private static final double FIELD_INNER = 0.8;              // loops start just outside the core model
    private static final double AXIS_TILT = Math.toRadians(25); // magnetic axis tilt off vertical
    private static final double AXIS_PRECESSION = 0.04;         // radians/tick the tilted axis sweeps around
    private static final int SHIMMER_POINTS = 4;                // sparks crawling over the core's surface per tick
    private static final double SHIMMER_RADIUS = 0.7;

    private static final int STREAMS = 6;
    private static final int STREAM_POINTS = 2;             // particles per stream per tick
    private static final double STREAM_MIN_RADIUS = 1.8;
    private static final double STREAM_CURL = 0.30;         // radians of swirl per block of radius
    private static final double STREAM_SWIRL_WIDTH = 0.20;  // how far a stream bows off its straight line (fraction of its radius)
    private static final double STREAM_FLOW = 0.03;         // fraction of the stream that flows inward per tick
    private static final double FIELD_SPIN = 0.03;          // radians/tick the whole stream field turns about the vertical axis

    private static final int[] EQUATOR_RGB = {35, 85, 255};
    private static final int[] POLE_RGB = {90, 150, 255};

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
        final double radiusH;
        final double radiusV;
        final Map<UUID, Integer> captured = new HashMap<>();
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

    private record Placement(Location location, boolean airborne) {
    }

    public MetalMagnetarAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("metal_magnetar_core", ElementType.METAL, 2, Constants.Cooldown.ABILITY_2_SECONDS, 2, configManager);
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
        Set<UUID> held = new HashSet<>();

        for (LivingEntity target : world.getNearbyLivingEntities(core.centre, core.radiusH, core.radiusV, core.radiusH)) {
            if (!isValidTarget(core, caster, target)) continue;

            UUID id = target.getUniqueId();
            Vector toCore = core.centre.toVector().subtract(target.getBoundingBox().getCenter());
            double distance = toCore.length();

            // Already captured: stays captured whatever the field shape or distance (no ping-pong across the
            // capture edge, which is what made players orbit the core or bounce up and down through it).
            Integer capturedAt = core.captured.get(id);

            if (capturedAt == null) {
                // Distance measured in the (possibly stretched) pull field, rescaled so PULL_RADIUS is its edge.
                double fx = toCore.getX() / core.radiusH;
                double fy = toCore.getY() / core.radiusV;
                double fz = toCore.getZ() / core.radiusH;
                double fieldDistance = Math.sqrt(fx * fx + fy * fy + fz * fz) * PULL_RADIUS;
                if (fieldDistance > PULL_RADIUS) continue;

                if (distance > CAPTURE_RADIUS) {
                    pull(target, toCore, fieldDistance);
                    continue;
                }

                capturedAt = core.ticks;
                core.captured.put(id, capturedAt);
            }

            held.add(id);
            capture(core, caster, target, toCore, capturedAt);
        }

        // Anyone who died, logged off, switched to creative, or was teleported out of range is let go.
        core.captured.keySet().retainAll(held);

        spawnSuctionParticles(core);
        if (core.ticks % 20 == 0) {
            world.playSound(core.centre, Sound.BLOCK_BEACON_AMBIENT, 0.8f, 1.6f);
        }
    }

    private void pull(LivingEntity target, Vector toCore, double fieldDistance) {
        double closeness = 1.0 - Math.min(1.0, fieldDistance / PULL_RADIUS);
        double speed = PULL_SPEED_FAR + (PULL_SPEED_NEAR - PULL_SPEED_FAR) * closeness;

        // No carried-over momentum: any sideways speed they had would be kept every tick and turn the
        // pull into an orbit that flings them past the core. Aim directly at the centre, ease off as the
        // core gets close (the client is a few ticks behind the server, so a fast arrival overshoots and
        // gets yanked back), and never move further in one tick than the distance that's left.
        double remaining = toCore.length();
        double eased = APPROACH_FLOOR + remaining * APPROACH_EASE;
        double step = Math.min(Math.min(speed, MAX_SPEED), Math.min(eased, remaining));
        Vector velocity = toCore.clone().normalize().multiply(step);

        // A cobweb zeroes the client's movement every tick, so a velocity push on a webbed entity just
        // rubber-bands it (that is what let webs shrug the pull off). Webbed entities are moved by
        // position instead, which the web can't cancel, so they're dragged straight out of it.
        if (isInCobweb(target)) {
            pullOutOfWeb(target, velocity);
            return;
        }

        // Lift off the floor, otherwise ground friction stalls the pull.
        if (target.isOnGround() && velocity.getY() < 0.1) {
            velocity.setY(0.1);
        }
        target.setVelocity(velocity);
    }

    /**
     * Drags an entity that is stuck in a cobweb by moving its position this tick by {@code step}
     * (blocks), instead of by velocity. Tries the full step first, then horizontal-only and vertical-only
     * if a solid block is in the way, so a webbed player against a wall still slides out along it.
     */
    private static void pullOutOfWeb(LivingEntity target, Vector step) {
        if (step.lengthSquared() < 1.0E-6) return;
        if (snapToCore(target, step)) return;
        Vector horizontal = new Vector(step.getX(), 0, step.getZ());
        if (horizontal.lengthSquared() > 1.0E-6 && snapToCore(target, horizontal)) return;
        snapToCore(target, new Vector(0, step.getY(), 0));
    }

    private void capture(Core core, Player caster, LivingEntity target, Vector toCore, int capturedAt) {
        // No damage: the Magnetar only controls. The orbit is stateless: it is worked out from where they are right now, so lag can't desync it.
        Vector offset = toCore.clone().multiply(-1);          // core -> target
        double vertical = toCore.getY();                      // how far the core is above their body centre
        offset.setY(0);
        double radius = offset.length();
        Vector outward = radius > 1.0E-4 ? offset.clone().multiply(1.0 / radius) : new Vector(1, 0, 0);

        boolean settled = core.ticks - capturedAt >= SETTLE_TICKS;
        // Walking, knockback or a bounce pushed them well off the ring? Put them straight back on it.
        Vector toRing = outward.clone().multiply(ORBIT_RADIUS - radius).setY(vertical);
        boolean snapped = settled && toRing.length() > LEASH && snapToCore(target, toRing);

        if (!snapped) {
            Vector tangent = new Vector(-outward.getZ(), 0, outward.getX()); // counter-clockwise seen from above
            Vector velocity = tangent.multiply(ORBIT_SPEED)
                    .add(outward.multiply((ORBIT_RADIUS - radius) * ORBIT_RADIAL_GAIN))
                    .add(new Vector(0, vertical * ORBIT_RADIAL_GAIN, 0));
            if (velocity.lengthSquared() > ORBIT_MAX_SPEED * ORBIT_MAX_SPEED) {
                velocity.normalize().multiply(ORBIT_MAX_SPEED);
            }
            if (isInCobweb(target)) {
                pullOutOfWeb(target, velocity); // velocity is zeroed by the web, so move them by position
            } else {
                target.setVelocity(velocity);
            }
        }
        target.setFallDistance(0f);
    }

    private static boolean snapToCore(LivingEntity target, Vector toCore) {
        BoundingBox moved = target.getBoundingBox().clone().shift(toCore);
        World world = target.getWorld();
        final double eps = 1.0E-6;
        for (int x = (int) Math.floor(moved.getMinX() + eps); x <= (int) Math.floor(moved.getMaxX() - eps); x++) {
            for (int y = (int) Math.floor(moved.getMinY() + eps); y <= (int) Math.floor(moved.getMaxY() - eps); y++) {
                for (int z = (int) Math.floor(moved.getMinZ() + eps); z <= (int) Math.floor(moved.getMaxZ() - eps); z++) {
                    if (!world.getBlockAt(x, y, z).isPassable()) return false;
                }
            }
        }

        if (!target.teleport(target.getLocation().add(toCore))) return false;
        target.setVelocity(new Vector());
        return true;
    }

    private static boolean isInCobweb(LivingEntity target) {
        BoundingBox box = target.getBoundingBox();
        World world = target.getWorld();
        for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX()); x++) {
            for (int y = (int) Math.floor(box.getMinY()); y <= (int) Math.floor(box.getMaxY()); y++) {
                for (int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(box.getMaxZ()); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.COBWEB) return true;
                }
            }
        }
        return false;
    }

    private boolean isValidTarget(Core core, Player caster, LivingEntity target) {
        if (!AbilityTargets.isValid(caster, target, core.trust)) return false;

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

    private void spawnSuctionParticles(Core core) {
        World world = core.centre.getWorld();
        double precession = core.ticks * AXIS_PRECESSION;
        double loopSpin = core.ticks * LOOP_SPIN;

        spawnFieldLoops(core, world, loopSpin, precession);
        spawnShimmer(core, world);
        spawnInflow(core, world);
    }

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

    private void spawnShimmer(Core core, World world) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < SHIMMER_POINTS; i++) {
            Vector dir = new Vector(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
            if (dir.lengthSquared() < 1.0E-6) continue;
            dir.normalize().multiply(SHIMMER_RADIUS);
            world.spawnParticle(Particle.ELECTRIC_SPARK, core.centre.clone().add(dir), 1, 0, 0, 0, 0);
        }
    }

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

    private static Vector orient(Vector local, double spinAboutAxis, double precession) {
        return local.rotateAroundY(spinAboutAxis).rotateAroundZ(AXIS_TILT).rotateAroundY(precession);
    }

    private static Color fieldColor(double polar) {
        double k = polar * polar; // keep most of the loop blue, the pinched ends just brighten to a lighter blue
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

    @Override
    public void setActive(Player player, boolean active) {
        super.setActive(player, active);
        if (!active) {
            end(player.getUniqueId(), false);
        }
    }

    public void onPlayerQuit(UUID playerUuid) {
        end(playerUuid, false);
    }

    @Override
    public String getName() {
        return ChatColor.GRAY + "Magnetar";
    }

    @Override
    public String getDescription() {
        return "Places a core that pulls in nearby players and mobs (even through cobwebs, which it drags them out of), then spins them around it in orbit until it collapses.";
    }
}