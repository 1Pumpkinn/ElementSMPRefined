package net.rose.elementSMPRefined.particles.element;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

public final class DeathParticles {

    private static final Particle.DustOptions BLACK_DUST =
            new Particle.DustOptions(Color.fromRGB(5, 5, 5), 1.4F);

    private DeathParticles() {}

    public static void shadowBurst(World world, Location center) {
        world.spawnParticle(Particle.DUST, center, 40, 0.35, 0.5, 0.35, 0, BLACK_DUST, true);
        world.spawnParticle(Particle.SQUID_INK, center, 6, 0.3, 0.4, 0.3, 0.01, null, true);
    }

    public static void shadowTrail(World world, Location point) {
        world.spawnParticle(Particle.DUST, point, 3, 0.1, 0.2, 0.1, 0, BLACK_DUST, true);
    }

    public static void backstabHit(World world, Location hitLoc) {
        world.spawnParticle(Particle.SWEEP_ATTACK, hitLoc, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.CRIT, hitLoc, 20, 0.3, 0.4, 0.3, 0.2, null, true);
    }
}
