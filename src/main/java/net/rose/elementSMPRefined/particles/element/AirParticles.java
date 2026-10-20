package net.rose.elementSMPRefined.particles.element;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/** Particle effects for the Air element (abilities and passives). */
public final class AirParticles {

    private AirParticles() {}

    /** Per-tick cloud trail while dashing. */
    public static void dashTrail(Location loc) {
        loc.getWorld().spawnParticle(Particle.CLOUD, loc, 5, 0.3, 0.3, 0.3, 0.05, null, true);
    }

    /** Puff on an entity knocked back by the dash. */
    public static void dashKnockback(Location at) {
        at.getWorld().spawnParticle(Particle.CLOUD, at, 10, 0.3, 0.3, 0.3, 0.05, null, true);
    }

    /** Faint wind trail behind the cutter blade. */
    public static void cutterTrail(World world, Location slice) {
        world.spawnParticle(Particle.CLOUD, slice, 2, 0.5, 0.05, 0.5, 0.0, null, true);
    }

    /** Slash mark on an entity hit by the cutter. */
    public static void cutterHit(Location entityLocation) {
        entityLocation.getWorld().spawnParticle(Particle.SWEEP_ATTACK, entityLocation.clone().add(0, 1, 0), 1, 0, 0, 0, 0, null, true);
    }

    /** One cloud puff in the expanding ring after a fall impact. */
    public static void fallImpactCloud(World world, Location loc) {
        world.spawnParticle(Particle.CLOUD, loc, 1, 0, 0.1, 0, 0.02);
    }

    /** One smoke puff in the inner ring after a fall impact. */
    public static void fallImpactSmoke(World world, Location loc) {
        world.spawnParticle(Particle.SMOKE, loc, 1, 0, 0.05, 0, 0.01);
    }
}
