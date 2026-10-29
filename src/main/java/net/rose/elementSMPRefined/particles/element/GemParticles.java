package net.rose.elementSMPRefined.particles.element;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

public final class GemParticles {

    private GemParticles() {}

    public static void survivorSettle(World world, Location target) {
        world.spawnParticle(Particle.END_ROD, target, 14, 0.25, 0.25, 0.25, 0.02);
    }

    public static void survivorGlint(World world, Location gemLocation) {
        world.spawnParticle(Particle.END_ROD, gemLocation,
                1, 0.2, 0.3, 0.2, 0.0);
    }

    public static void survivorVanish(World world, Location at) {
        world.spawnParticle(Particle.POOF, at, 12, 0.2, 0.2, 0.2, 0.05);
    }

    public static void gemEliminated(World world, Location at) {
        world.spawnParticle(Particle.POOF, at, 8, 0.15, 0.15, 0.15, 0.03);
    }
}
