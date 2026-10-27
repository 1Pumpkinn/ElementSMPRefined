package net.rose.elementSMPRefined.particles.element;

import net.rose.elementSMPRefined.util.compat.ClientCompat;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Particle effects for the Water element. */
public final class WaterParticles {

    private WaterParticles() {}

    /** Burst of water where the tentacle breaks the ground. */
    public static void graspBurst(Location at) {
        World world = at.getWorld();
        world.spawnParticle(Particle.SPLASH, at.clone().add(0, 0.2, 0), 50, 0.5, 0.2, 0.5, 0.2, null, true);
        world.spawnParticle(Particle.BUBBLE_POP, at.clone().add(0, 0.8, 0), 20, 0.4, 0.6, 0.4, 0.05, null, true);
    }

    /** Small splash where one tentacle breaks the ground. */
    public static void tentacleErupt(Location at) {
        World world = at.getWorld();
        world.spawnParticle(Particle.SPLASH, at.clone().add(0, 0.1, 0), 14, 0.2, 0.1, 0.2, 0.15, null, true);
        world.spawnParticle(Particle.BUBBLE_POP, at.clone().add(0, 0.4, 0), 6, 0.15, 0.3, 0.15, 0.03, null, true);
    }

    /** Bubbles as the tentacle sinks back into the ground. */
    public static void graspRelease(Location body) {
        body.getWorld().spawnParticle(Particle.BUBBLE_POP, body, 40, 0.5, 0.7, 0.5, 0.15, null, true);
        body.getWorld().spawnParticle(Particle.SPLASH, body, 30, 0.5, 0.5, 0.5, 0.2, null, true);
    }

    // ---- Kraken Slam ----

    /**
     * Ground marker for the slam: the outer ring is the hit area, the inner ring closes in on it as
     * the tentacle gets closer ({@code progress} 0 -> 1).
     */
    public static void krakenSlamMarker(Location center, double radius, double progress) {
        ring(center, radius, 28);
        ring(center, Math.max(0.3, radius * Math.min(1.0, progress)), 16);
    }

    private static void ring(Location center, double radius, int points) {
        World world = center.getWorld();
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            Location p = center.clone().add(Math.cos(angle) * radius, 0.15, Math.sin(angle) * radius);
            world.spawnParticle(Particle.SPLASH, p, 2, 0.05, 0.02, 0.05, 0.02, null, true);
            world.spawnParticle(Particle.BUBBLE_POP, p, 1, 0.02, 0.02, 0.02, 0.0, null, true);
        }
    }

    /** The tentacle landing: a big splash at the centre and a ring of spray rolling out to the edge. */
    public static void krakenSlamImpact(Location at, double radius) {
        World world = at.getWorld();
        world.spawnParticle(Particle.SPLASH, at.clone().add(0, 0.3, 0), 120,
                radius * 0.35, 0.3, radius * 0.35, 0.4, null, true);
        world.spawnParticle(Particle.BUBBLE_POP, at.clone().add(0, 0.6, 0), 60,
                radius * 0.3, 0.6, radius * 0.3, 0.15, null, true);
        for (double r = 1.0; r <= radius; r += 1.25) {
            int points = (int) Math.max(12, r * 8);
            for (int i = 0; i < points; i++) {
                double angle = 2 * Math.PI * i / points;
                Location p = at.clone().add(Math.cos(angle) * r, 0.2, Math.sin(angle) * r);
                world.spawnParticle(Particle.SPLASH, p, 4, 0.1, 0.1, 0.1, 0.3, null, true);
            }
        }
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