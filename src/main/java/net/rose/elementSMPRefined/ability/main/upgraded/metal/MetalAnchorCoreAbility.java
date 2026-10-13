package net.rose.elementSMPRefined.ability.main.upgraded.metal;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.util.damage.TrueDamage;
import net.rose.elementSMPRefined.util.sound.SoundUtils;
import net.rose.elementSMPRefined.util.visual.model.AnchorCoreVisual;
import org.bukkit.ChatColor;
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

/**
 * Metal element ability 2: plants an Anchor Core ({@link AnchorCoreVisual}) where the caster is
 * looking. For {@value #DURATION_TICKS} ticks it drags every nearby living entity - players and
 * mobs alike, except the caster, anyone they trust, their tamed pets and armor stands - toward
 * it, pulling harder the closer they get. Anything that ends up fully inside the core
 * ({@value #CAPTURE_RADIUS} blocks from its centre) is pinned there and takes true damage every
 * {@value #DAMAGE_INTERVAL_TICKS} ticks until the core collapses or it breaks free.
 * <p>
 * Damage goes through {@link TrueDamage}, so a Metal Shield counts it as a damaging ability.
 */
public class MetalAnchorCoreAbility extends BaseAbility {

    private static final double RANGE = 25.0;               // how far away the core can be placed on a surface
    private static final double AIR_RANGE = 12.0;           // how far away it floats when you aim at open sky (so it can't go as high)
    private static final double HOVER = 1.2;                // how far off the surface it floats (about chest height off the floor)
    private static final int DURATION_TICKS = 6 * 20;
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

    // Suction visuals: streams of sparks spiralling in from every direction (evenly spread over a sphere,
    // so it looks the same from any angle) + a small spinning gyroscope halo around the core.
    private static final int STREAMS = 10;
    private static final int STREAM_POINTS = 3;             // particles per stream per tick
    private static final double STREAM_MIN_RADIUS = 1.8;
    private static final double STREAM_CURL = 0.30;         // radians of swirl per block of radius
    private static final double STREAM_SWIRL_WIDTH = 0.20;  // how far a stream bows off its straight line (fraction of its radius)
    private static final double STREAM_FLOW = 0.03;         // fraction of the stream that flows inward per tick
    private static final double FIELD_SPIN = 0.03;          // radians/tick the whole field turns about the vertical axis
    private static final int HALO_POINTS = 5;               // per ring
    private static final double HALO_RADIUS = 1.5;

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
        final AnchorCoreVisual visual;
        final TrustManager trust;
        /** Pull reach sideways / up-and-down. Equal on a surface, tall and narrow in the air. */
        final double radiusH;
        final double radiusV;
        /** Tick at which each captured player may next be hit. */
        final Map<UUID, Integer> nextHit = new HashMap<>();
        int ticks = 0;
        BukkitRunnable task;

        Core(Location centre, AnchorCoreVisual visual, TrustManager trust, double radiusH, double radiusV) {
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

    public MetalAnchorCoreAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("metal_anchor_core", ElementType.METAL, 2, COOLDOWN_SECONDS, 2, configManager);
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
        Core core = new Core(centre, new AnchorCoreVisual(centre), context.getTrustManager(),
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
            TrueDamage.of(DAMAGE).attacker(caster).ignoreIFrames(true).apply(target);

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
     * Streams of sparks spiralling in toward the core from all directions, plus a small halo of three
     * perpendicular rings around it - no "good side" to view it from.
     */
    private void spawnSuctionParticles(Core core) {
        World world = core.centre.getWorld();
        double flow = (core.ticks * STREAM_FLOW) % 1.0;
        double spin = core.ticks * FIELD_SPIN;
        // stretch the streams to match the pull field (tall and narrow when the core is in the air)
        Vector fieldScale = new Vector(core.radiusH / PULL_RADIUS, core.radiusV / PULL_RADIUS, core.radiusH / PULL_RADIUS);

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
                world.spawnParticle(Particle.END_ROD, core.centre.clone().add(offset), 1, 0, 0, 0, 0);
            }
        }

        for (int plane = 0; plane < 3; plane++) {
            for (int i = 0; i < HALO_POINTS; i++) {
                double angle = core.ticks * 0.35 + i * (Math.PI * 2 / HALO_POINTS);
                Vector offset = onPlane(plane, Math.cos(angle) * HALO_RADIUS, 0, Math.sin(angle) * HALO_RADIUS);
                world.spawnParticle(Particle.ELECTRIC_SPARK, core.centre.clone().add(offset), 1, 0, 0, 0, 0);
            }
        }
    }

    /** Maps disc-local coordinates (a, b = thickness, c) onto the XZ (0), XY (1) or YZ (2) plane. */
    private static Vector onPlane(int plane, double a, double b, double c) {
        return switch (plane) {
            case 0 -> new Vector(a, b, c);
            case 1 -> new Vector(a, c, b);
            default -> new Vector(b, a, c);
        };
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
        return ChatColor.GRAY + "Anchor Core";
    }

    @Override
    public String getDescription() {
        return "Plant an anchor core that pulls players and mobs in and crushes them once they're fully inside";
    }
}