package net.rose.elementSMPRefined.particles.element;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;

public final class FireParticles {

    private static final Particle.DustOptions MARKER_DUST = new Particle.DustOptions(Color.fromRGB(255, 120, 20), 1.0f);

    private FireParticles() {}

    public static void starFallImpact(World world, Location impact, double impactRadius, Material meteorBlock) {
        world.spawnParticle(Particle.EXPLOSION, impact, 1, 0, 0, 0, 0, null, true);
        world.spawnParticle(Particle.BLOCK, impact.clone().add(0, 0.3, 0), 12,
                impactRadius / 4, 0.2, impactRadius / 4, 0.1, meteorBlock.createBlockData(), true);
    }

    public static void starFallMarkerRing(World world, Location center, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2 * i) / points;
            Location p = center.clone().add(Math.cos(a) * radius, 0.15, Math.sin(a) * radius);
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, MARKER_DUST, true);
        }
    }

    public static void hellChainImpact(Location targetLoc) {
        targetLoc.getWorld().spawnParticle(Particle.LAVA, targetLoc, 15, 0.3, 0.3, 0.3, 0.05);
    }

    public static void hellChainHook(Location hookLocation, double burnRadius) {
        hookLocation.getWorld().spawnParticle(Particle.LAVA, hookLocation, 20,
                burnRadius / 2, 0.3, burnRadius / 2, 0.05);
    }

    public static void hellChainMantle(Location at) {
        at.getWorld().spawnParticle(Particle.LAVA, at, 15, 0.4, 0.2, 0.4, 0.05);
    }
}
