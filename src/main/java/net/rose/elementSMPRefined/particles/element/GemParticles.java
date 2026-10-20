package net.rose.elementSMPRefined.particles.element;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/** Particle effects for the element gem orbit (the ring of gems shown during rerolls). */
public final class GemParticles {

    private GemParticles() {}

    /** Sparkle when the surviving gem settles in front of the player. */
    public static void survivorSettle(World world, Location target) {
        world.spawnParticle(Particle.END_ROD, target, 14, 0.25, 0.25, 0.25, 0.02);
    }

    /** Occasional glint on the held gem. */
    public static void survivorGlint(World world, Location gemLocation) {
        world.spawnParticle(Particle.END_ROD, gemLocation,
                1, 0.2, 0.3, 0.2, 0.0);
    }

    /** Poof when the surviving gem disappears. */
    public static void survivorVanish(World world, Location at) {
        world.spawnParticle(Particle.POOF, at, 12, 0.2, 0.2, 0.2, 0.05);
    }

    /** Poof when an eliminated gem disappears from the ring. */
    public static void gemEliminated(World world, Location at) {
        world.spawnParticle(Particle.POOF, at, 8, 0.15, 0.15, 0.15, 0.03);
    }
}
