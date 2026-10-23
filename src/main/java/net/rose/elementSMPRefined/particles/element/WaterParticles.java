package net.rose.elementSMPRefined.particles.element;

import net.rose.elementSMPRefined.util.compat.ClientCompat;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Particle effects for the Water element. */
public final class WaterParticles {

    private WaterParticles() {}

    /** Splash when the dome is raised. */
    public static void domeCast(World world, Location center, double domeRadius) {
        world.spawnParticle(Particle.SPLASH, center.clone().add(0, 0.5, 0), 60,
                domeRadius / 2, 0.3, domeRadius / 2, 0.1, null, true);
    }

    /** Splash where a projectile is destroyed by the dome. */
    public static void domeProjectileBlocked(World world, Location at) {
        world.spawnParticle(Particle.SPLASH, at, 10, 0.2, 0.2, 0.2, 0.05, null, true);
    }

    /** Bubbles on an entity pushed out of the dome. */
    public static void domePush(World world, Location body) {
        world.spawnParticle(Particle.BUBBLE_POP, body, 4, 0.3, 0.4, 0.3, 0.05, null, true);
    }

    /** Bubbles on a victim whose hit was blocked by the dome. */
    public static void domeHitBlocked(Location victimLocation) {
        victimLocation.getWorld().spawnParticle(Particle.BUBBLE_POP, victimLocation.clone().add(0, 1, 0), 10,
                0.4, 0.5, 0.4, 0.05, null, true);
    }

    /** Bubble burst when the dome shatters or fades. */
    public static void domeBreak(World world, Location mid, double domeRadius) {
        world.spawnParticle(Particle.BUBBLE_POP, mid, 60, domeRadius / 2, domeRadius / 3, domeRadius / 2, 0.15, null, true);
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
}
