package net.rose.elementSMPRefined.particles.element;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;

/** Particle effects for the Fire element. */
public final class FireParticles {

    private static final Particle.DustOptions MARKER_DUST = new Particle.DustOptions(Color.fromRGB(255, 120, 20), 1.0f);

    private FireParticles() {}

    /** Explosion plus block debris where a star lands. */
    public static void starFallImpact(World world, Location impact, double impactRadius, Material meteorBlock) {
        world.spawnParticle(Particle.EXPLOSION, impact, 1, 0, 0, 0, 0, null, true);
        world.spawnParticle(Particle.BLOCK, impact.clone().add(0, 0.3, 0), 12,
                impactRadius / 4, 0.2, impactRadius / 4, 0.1, meteorBlock.createBlockData(), true);
    }

    /** Ground marker ring showing where the star will land. */
    public static void starFallMarkerRing(World world, Location center, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2 * i) / points;
            Location p = center.clone().add(Math.cos(a) * radius, 0.15, Math.sin(a) * radius);
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, MARKER_DUST, true);
        }
    }

    /** Lava burst on the target when the chain drags it in. */
    public static void hellChainImpact(Location targetLoc) {
        targetLoc.getWorld().spawnParticle(Particle.LAVA, targetLoc, 15, 0.3, 0.3, 0.3, 0.05);
    }

    /** Lava burst where the chain hook lands. */
    public static void hellChainHook(Location hookLocation, double burnRadius) {
        hookLocation.getWorld().spawnParticle(Particle.LAVA, hookLocation, 20,
                burnRadius / 2, 0.3, burnRadius / 2, 0.05);
    }

    /** Lava burst when the caster mantles up onto a ledge. */
    public static void hellChainMantle(Location at) {
        at.getWorld().spawnParticle(Particle.LAVA, at, 15, 0.4, 0.2, 0.4, 0.05);
    }
}
