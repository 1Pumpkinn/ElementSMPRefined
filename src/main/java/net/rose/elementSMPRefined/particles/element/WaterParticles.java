package net.rose.elementSMPRefined.particles.element;

import net.rose.elementSMPRefined.util.compat.ClientCompat;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Particle effects for the Water element. */
public final class WaterParticles {

    private WaterParticles() {}

    private static final Particle.DustOptions ROPE_DUST =
            new Particle.DustOptions(Color.fromRGB(70, 165, 255), 1.1f);

    /** Burst of water where the tentacle erupts around the target. */
    public static void tetherGrab(Location at) {
        World world = at.getWorld();
        world.spawnParticle(Particle.SPLASH, at.clone().add(0, 0.2, 0), 50, 0.5, 0.2, 0.5, 0.2, null, true);
        world.spawnParticle(Particle.BUBBLE_POP, at.clone().add(0, 0.8, 0), 20, 0.4, 0.6, 0.4, 0.05, null, true);
    }

    /**
     * The link between caster and target: a water-blue rope that sways while slack and runs
     * straight when the tentacle is yanking. {@code time} (ticks) drives the sway.
     */
    public static void tetherRope(Location from, Location to, boolean taut, int time) {
        World world = from.getWorld();
        Vector line = to.toVector().subtract(from.toVector());
        double length = line.length();
        if (length < 0.5) return;
        Vector dir = line.clone().multiply(1.0 / length);

        // Two vectors perpendicular to the rope to sway along.
        Vector side = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() < 0.0001) side = new Vector(1, 0, 0);
        side.normalize();
        Vector up = side.clone().crossProduct(dir).normalize();

        double step = 0.5;
        for (double d = 0; d <= length; d += step) {
            double fade = Math.sin(Math.PI * (d / length)); // anchored at both ends
            double amp = taut ? 0.0 : 0.25 * fade;
            double phase = d * 1.4 - time * 0.5;
            Location p = from.clone().add(dir.clone().multiply(d))
                    .add(side.clone().multiply(Math.sin(phase) * amp))
                    .add(up.clone().multiply(Math.cos(phase) * amp));
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, ROPE_DUST, true);
            if (((int) (d / step) + time) % 6 == 0) {
                world.spawnParticle(Particle.BUBBLE_POP, p, 1, 0.05, 0.05, 0.05, 0.0, null, true);
            }
        }
    }

    /** Spray on a target being yanked back to the leash. */
    public static void tetherYank(Location body) {
        World world = body.getWorld();
        world.spawnParticle(Particle.SPLASH, body, 25, 0.3, 0.4, 0.3, 0.2, null, true);
        world.spawnParticle(Particle.BUBBLE, body, 10, 0.3, 0.4, 0.3, 0.05, null, true);
    }

    /** Bubbles when the tentacle lets go. */
    public static void tetherRelease(Location body) {
        body.getWorld().spawnParticle(Particle.BUBBLE_POP, body, 40, 0.5, 0.7, 0.5, 0.15, null, true);
        body.getWorld().spawnParticle(Particle.SPLASH, body, 30, 0.5, 0.5, 0.5, 0.2, null, true);
    }

    /** Spray at the geyser launch point. */
    public static void geyserSpray(Location sprayOrigin) {
        World world = sprayOrigin.getWorld();
        world.spawnParticle(Particle.SPLASH, sprayOrigin, 25,
                0.3, 0.3, 0.3, 0.15, null, true);
        world.spawnParticle(Particle.BUBBLE_POP, sprayOrigin, 15,
                0.3, 0.2, 0.3, 0.1, null, true);
        world.spawnParticle(Particle.BUBBLE, sprayOrigin, 20,
                0.4, 0.2, 0.4, 0.02, null, true);
    }

    /**
     * Vanilla geyser plume; {@code waterBlocks} drives its height. {@code Particle.GEYSER} is
     * 26.2-only and crashes older clients joining via ViaBackwards, so it's sent per-viewer:
     * native clients get the real plume, translated clients get a splash/bubble fallback.
     */
    public static void geyserEruption(Location origin, int waterBlocks) {
        Particle.Geyser data = new Particle.Geyser(waterBlocks);
        for (Player viewer : origin.getNearbyPlayers(64)) {
            if (ClientCompat.supportsNewParticles(viewer)) {
                viewer.spawnParticle(Particle.GEYSER, origin, 0, 0, 0, 0, data);
            } else {
                Location base = origin.clone().add(0, 1, 0);
                viewer.spawnParticle(Particle.SPLASH, base, 80, 0.3, 1.5, 0.3, 0.3);
                viewer.spawnParticle(Particle.BUBBLE_POP, base, 40, 0.3, 1.2, 0.3, 0.2);
            }
        }
    }

    /** Per-tick trail behind the player riding the geyser. */
    public static void geyserTrail(Location playerLocation) {
        World world = playerLocation.getWorld();
        world.spawnParticle(Particle.SPLASH, playerLocation, 6, 0.25, 0.25, 0.25, 0.02, null, true);
        world.spawnParticle(Particle.BUBBLE, playerLocation, 4, 0.2, 0.2, 0.2, 0.01, null, true);
    }

    // ---- Tidal Surge (water dash). ----

    /** Burst at the start of the dash. */
    public static void surgeBurst(Location at) {
        World world = at.getWorld();
        world.spawnParticle(Particle.SPLASH, at.clone().add(0, 0.9, 0), 40, 0.4, 0.5, 0.4, 0.2, null, true);
        world.spawnParticle(Particle.BUBBLE_POP, at.clone().add(0, 0.9, 0), 20, 0.4, 0.5, 0.4, 0.1, null, true);
    }

    /** Hit effect on something the dash ran into. */
    public static void surgeImpact(Location at) {
        World world = at.getWorld();
        world.spawnParticle(Particle.SPLASH, at, 30, 0.4, 0.5, 0.4, 0.25, null, true);
        world.spawnParticle(Particle.BUBBLE, at, 12, 0.3, 0.4, 0.3, 0.05, null, true);
    }

    /** Per-tick wake behind the dashing player, spread wide across the heading. */
    public static void surgeWake(Location at, Vector heading) {
        World world = at.getWorld();
        Vector dir = heading.clone().setY(0);
        if (dir.lengthSquared() < 1.0E-4) dir = new Vector(0, 0, 1);
        dir.normalize();
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());

        Location trail = at.clone().add(0, 0.6, 0).subtract(dir.clone().multiply(0.8));
        for (int i = -1; i <= 1; i++) {
            Location p = trail.clone().add(side.clone().multiply(i * 0.6));
            world.spawnParticle(Particle.SPLASH, p, 6, 0.2, 0.3, 0.2, 0.05, null, true);
            world.spawnParticle(Particle.BUBBLE, p, 3, 0.2, 0.2, 0.2, 0.02, null, true);
        }
    }

    /** Puff when the dash ends. */
    public static void surgeEnd(Location at) {
        at.getWorld().spawnParticle(Particle.BUBBLE_POP, at.clone().add(0, 0.5, 0), 25, 0.4, 0.4, 0.4, 0.1, null, true);
    }
}