package net.rose.elementSMPRefined.ability.main.mythic.time;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TeamManager;
import net.rose.elementSMPRefined.util.damage.AbilityTargets;
import net.rose.elementSMPRefined.util.visual.model.TimeRingVisual;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Time ability 1 - Time Ring.
 * <p>
 * Draws a ring on the ground where the caster is looking. For as long as it lasts, every enemy
 * standing inside it (players and mobs - not the caster, their team or allies) lives in slow
 * motion: see {@link TimeDilation}. Enemy projectiles that fly into the ring are slowed too and
 * pick their speed back up when they leave it.
 */
public class TimeRingAbility extends BaseAbility {

    private static final double RADIUS = 6.0;
    private static final double ZONE_BELOW = 1.0; // how far under the decal still counts as inside
    private static final double ZONE_ABOVE = 6.0; // ...and how far above it (jumping doesn't escape)
    private static final int DURATION_TICKS = 8 * 20;
    private static final int GROW_TICKS = 12;
    private static final int FADE_TICKS = 12;

    private static final double CAST_RANGE = 24.0;
    private static final double CAST_GROUND_SEARCH = 40.0;
    private static final double MAX_GROUND_DIFFERENCE = 1.5; // the decal is flat: it needs level ground

    private static final double ARROW_GRAVITY = 0.05; // blocks/tick^2, vanilla
    private static final double THROWN_GRAVITY = 0.03;

    private static final double HAND_LENGTH = 0.92; // of the radius
    private static final double HAND_STEP = 0.5; // blocks between particles on the hand
    private static final double HAND_RADIANS_PER_TICK = Math.PI * 2.0 / (6 * 20); // 6s a turn
    private static final Particle.DustOptions HAND_DUST =
            new Particle.DustOptions(Color.fromRGB(127, 227, 220), 1.1f);
    private static final Particle.DustOptions TIP_DUST =
            new Particle.DustOptions(Color.fromRGB(236, 197, 102), 1.4f);

    private static final Map<UUID, Ring> RINGS = new HashMap<>();

    private final ElementSMPRefined plugin;

    private static final class SlowedProjectile {
        final Projectile entity;
        final Vector real; // the velocity it would have at normal speed
        final boolean hadGravity;

        SlowedProjectile(Projectile entity) {
            this.entity = entity;
            this.real = entity.getVelocity().clone();
            this.hadGravity = entity.hasGravity();
        }
    }

    private static final class Ring {
        final UUID casterId;
        final World world;
        final Location centre;
        final TimeRingVisual visual;
        final TeamManager team;
        final Set<UUID> held = new HashSet<>();
        final Map<UUID, SlowedProjectile> projectiles = new HashMap<>();
        int ticks = 0;
        BukkitRunnable task;

        Ring(UUID casterId, Location centre, TimeRingVisual visual, TeamManager team) {
            this.casterId = casterId;
            this.world = centre.getWorld();
            this.centre = centre;
            this.visual = visual;
            this.team = team;
        }

        /** Stops the ring and puts everything it touched back to normal. */
        void close() {
            if (task != null) task.cancel();
            for (UUID id : held) {
                TimeDilation.release(id, this);
            }
            held.clear();
            for (SlowedProjectile slowed : projectiles.values()) {
                free(slowed);
            }
            projectiles.clear();
            visual.remove();
        }
    }

    public TimeRingAbility(JavaPlugin plugin, ConfigManager configManager) {
        super(
                "time_ring",
                ElementType.TIME,
                1,
                Constants.Cooldown.ABILITY_1_SECONDS,
                1,
                configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player caster = context.getPlayer();
        if (caster == null) return false;

        end(caster.getUniqueId(), false); // one ring per caster

        Location centre = findCentre(caster);
        if (centre == null) {
            caster.sendMessage(
                    Component.text(
                            "There's no ground there to draw a time ring on.", NamedTextColor.GRAY));
            return false;
        }
        if (!isLevel(centre)) {
            caster.sendMessage(
                    Component.text(
                            "The ground there is too uneven for a time ring.", NamedTextColor.GRAY));
            return false;
        }

        Ring ring =
                new Ring(
                        caster.getUniqueId(),
                        centre,
                        new TimeRingVisual(centre, RADIUS),
                        context.getTeamManager());
        RINGS.put(caster.getUniqueId(), ring);
        setActive(caster, true);

        ring.world.playSound(centre, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.6f);
        ring.world.playSound(centre, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.5f);

        ring.task =
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!caster.isOnline() || ring.ticks >= DURATION_TICKS) {
                            end(caster.getUniqueId(), ring.ticks >= DURATION_TICKS);
                            return;
                        }
                        tick(ring, caster);
                        ring.ticks++;
                    }
                };
        ring.task.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    // ---- placing the ring ---------------------------------------------------------------------

    private Location findCentre(Player caster) {
        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection();

        RayTraceResult hit =
                world.rayTraceBlocks(eye, direction, CAST_RANGE, FluidCollisionMode.NEVER, true);
        Vector point;
        double searchFrom;
        if (hit != null && hit.getHitPosition() != null) {
            point = hit.getHitPosition().clone();
            BlockFace face = hit.getHitBlockFace();
            if (face != null) {
                point.add(face.getDirection().multiply(0.6)); // back off the surface into open air
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

    /**
     * The decal is flat, so on a slope part of it would sink into the terrain and part would hover.
     * Sample the ground around the ring and only allow it where it stays within a block and a half
     * of the centre.
     */
    private boolean isLevel(Location centre) {
        World world = centre.getWorld();
        double[] rings = {RADIUS * 0.5, RADIUS * 0.95};
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0;
            for (double r : rings) {
                Double ground =
                        groundY(
                                world,
                                centre.getX() + Math.cos(angle) * r,
                                centre.getY(),
                                centre.getZ() + Math.sin(angle) * r,
                                MAX_GROUND_DIFFERENCE + 2.0);
                if (ground == null || Math.abs(ground - centre.getY()) > MAX_GROUND_DIFFERENCE) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Double groundY(World world, double x, double fromY, double z, double depth) {
        Location from = new Location(world, x, fromY + 2.0, z);
        RayTraceResult hit =
                world.rayTraceBlocks(
                        from, new Vector(0, -1, 0), depth + 2.0, FluidCollisionMode.ALWAYS, true);
        if (hit == null || hit.getHitPosition() == null) return null;
        return hit.getHitPosition().getY();
    }

    // ---- every tick ---------------------------------------------------------------------------

    private void tick(Ring ring, Player caster) {
        double growth = growth(ring.ticks);
        ring.visual.update(ring.ticks, growth);

        double radius = RADIUS * growth; // the zone grows and shrinks with the decal
        slowCreatures(ring, caster, radius);
        slowProjectiles(ring, caster, radius);
        ambience(ring, radius);
    }

    private static double growth(int ticks) {
        double in = Math.min(1.0, ticks / (double) GROW_TICKS);
        double out = Math.min(1.0, (DURATION_TICKS - ticks) / (double) FADE_TICKS);
        return Math.max(0.02, Math.min(in, out));
    }

    private void slowCreatures(Ring ring, Player caster, double radius) {
        Location middle = zoneMiddle(ring);
        double halfHeight = (ZONE_ABOVE + ZONE_BELOW) / 2.0;
        double radiusSq = radius * radius;

        Set<UUID> inside = new HashSet<>();
        for (LivingEntity target :
                ring.world.getNearbyLivingEntities(middle, radius, halfHeight, radius)) {
            if (!AbilityTargets.isValid(caster, target, ring.team)) continue;

            Location at = target.getLocation();
            double dx = at.getX() - ring.centre.getX();
            double dz = at.getZ() - ring.centre.getZ();
            if (dx * dx + dz * dz > radiusSq) continue;

            UUID id = target.getUniqueId();
            inside.add(id);
            if (ring.held.add(id) && target instanceof Player) {
                ring.world.playSound(at, Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.6f);
            }
            // Every tick, not just on entering: it is cheap, and it re-applies the slow to a
            // player who died and respawned inside the ring.
            TimeDilation.hold(target, ring);
        }

        Iterator<UUID> it = ring.held.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            if (inside.contains(id)) continue;
            TimeDilation.release(id, ring);
            it.remove();
        }
    }

    private void slowProjectiles(Ring ring, Player caster, double radius) {
        Location middle = zoneMiddle(ring);
        double halfHeight = (ZONE_ABOVE + ZONE_BELOW) / 2.0;
        double radiusSq = radius * radius;

        Set<UUID> inside = new HashSet<>();
        for (Entity entity : ring.world.getNearbyEntities(middle, radius, halfHeight, radius)) {
            if (!(entity instanceof AbstractArrow) && !(entity instanceof ThrowableProjectile)) {
                continue;
            }
            Projectile projectile = (Projectile) entity;
            if (!projectile.isValid() || isStuck(projectile)) continue;
            if (isFriendly(ring, projectile)) continue;

            Location at = projectile.getLocation();
            double dx = at.getX() - ring.centre.getX();
            double dz = at.getZ() - ring.centre.getZ();
            if (dx * dx + dz * dz > radiusSq) continue;

            UUID id = projectile.getUniqueId();
            inside.add(id);

            SlowedProjectile slowed = ring.projectiles.get(id);
            if (slowed == null || slowed.entity != projectile) {
                slowed = new SlowedProjectile(projectile);
                ring.projectiles.put(id, slowed);
                // Gravity is applied by hand below, scaled to the slowed clock.
                projectile.setGravity(false);
            }

            double gravity = projectile instanceof AbstractArrow ? ARROW_GRAVITY : THROWN_GRAVITY;
            slowed.real.setY(slowed.real.getY() - gravity * TimeDilation.TIME_SCALE);
            projectile.setVelocity(slowed.real.clone().multiply(TimeDilation.TIME_SCALE));
        }

        Iterator<Map.Entry<UUID, SlowedProjectile>> it = ring.projectiles.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, SlowedProjectile> entry = it.next();
            if (inside.contains(entry.getKey())) continue;
            free(entry.getValue());
            it.remove();
        }
    }

    private static boolean isStuck(Projectile projectile) {
        return projectile instanceof AbstractArrow arrow
                && (arrow.isInBlock() || arrow.isOnGround());
    }

    /** The caster's, their team's and their allies' projectiles fly through at normal speed. */
    private static boolean isFriendly(Ring ring, Projectile projectile) {
        if (!(projectile.getShooter() instanceof Player shooter)) return false;
        UUID shooterId = shooter.getUniqueId();
        return shooterId.equals(ring.casterId)
                || (ring.team != null && ring.team.isFriendly(ring.casterId, shooterId));
    }

    /** Gives a projectile its gravity back, and its full speed if it is still flying. */
    private static void free(SlowedProjectile slowed) {
        Projectile projectile = slowed.entity;
        if (!projectile.isValid()) return;
        projectile.setGravity(slowed.hadGravity);
        if (!isStuck(projectile)) {
            projectile.setVelocity(slowed.real);
        }
    }

    private static Location zoneMiddle(Ring ring) {
        return ring.centre.clone().add(0, (ZONE_ABOVE - ZONE_BELOW) / 2.0, 0);
    }

    // ---- looks and sounds ---------------------------------------------------------------------

    private void ambience(Ring ring, double radius) {
        World world = ring.world;

        // A clock hand sweeping round the ring, with a gold tip.
        if (ring.ticks % 2 == 0) {
            double angle = ring.ticks * HAND_RADIANS_PER_TICK;
            double dirX = Math.cos(angle);
            double dirZ = Math.sin(angle);
            double length = radius * HAND_LENGTH;
            double y = ring.centre.getY() + 0.12;
            for (double d = HAND_STEP; d < length; d += HAND_STEP) {
                Location at =
                        new Location(
                                world,
                                ring.centre.getX() + dirX * d,
                                y,
                                ring.centre.getZ() + dirZ * d);
                world.spawnParticle(Particle.DUST, at, 1, 0.02, 0.0, 0.02, 0.0, HAND_DUST, true);
            }
            Location tip =
                    new Location(
                            world,
                            ring.centre.getX() + dirX * length,
                            y,
                            ring.centre.getZ() + dirZ * length);
            world.spawnParticle(Particle.DUST, tip, 2, 0.05, 0.0, 0.05, 0.0, TIP_DUST, true);
        }

        // Tick, tock.
        if (ring.ticks % 10 == 0 && ring.ticks < DURATION_TICKS - FADE_TICKS) {
            float pitch = (ring.ticks / 10) % 2 == 0 ? 0.7f : 0.55f;
            world.playSound(ring.centre, Sound.BLOCK_COMPARATOR_CLICK, 0.6f, pitch);
        }
    }

    // ---- ending -------------------------------------------------------------------------------

    private void end(UUID casterId, boolean collapsed) {
        Ring ring = RINGS.remove(casterId);
        if (ring == null) return;
        ring.close();

        Player caster = plugin.getServer().getPlayer(casterId);
        if (caster != null) super.setActive(caster, false);

        if (collapsed) {
            ring.world.playSound(ring.centre, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.8f);
        }
    }

    /** Ends every ring and speeds everything back up. Called when the plugin shuts down. */
    public static void removeAll() {
        for (Ring ring : new ArrayList<>(RINGS.values())) {
            ring.close();
        }
        RINGS.clear();
        TimeDilation.releaseAll();
        TimeRingVisual.removeAll();
    }

    @Override
    public void setActive(Player player, boolean active) {
        super.setActive(player, active);
        if (!active) {
            end(player.getUniqueId(), false);
        }
    }

    @Override
    public String getName() {
        return ChatColor.LIGHT_PURPLE + "Time Ring";
    }

    @Override
    public String getDescription() {
        return "Draws a time ring on the ground. Enemies inside it are slowed: they move, attack,"
                + " mine and fall slower, and projectiles crawl through it.";
    }
}
