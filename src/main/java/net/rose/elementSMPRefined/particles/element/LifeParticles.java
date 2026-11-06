package net.rose.elementSMPRefined.particles.element;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

public final class LifeParticles {

    private static final Particle.DustOptions RED_DUST = new Particle.DustOptions(Color.RED, 1.0f);

    private static final Particle.DustOptions GREEN_DUST =
            new Particle.DustOptions(Color.fromRGB(90, 255, 130), 1.0f);

    private static final Particle.DustOptions POISON_DUST =
            new Particle.DustOptions(Color.fromRGB(120, 200, 60), 1.2f);

    private LifeParticles() {}

    public static void totemRingPoint(World world, Location loc) {
        world.spawnParticle(Particle.DUST, loc, 1, 0.05, 0.05, 0.05, 0, GREEN_DUST, true);
    }

    public static void regenRingPoint(World world, Location loc) {
        world.spawnParticle(Particle.DUST, loc, 1, 0.1, 0.1, 0.1, 0, RED_DUST, true);
    }

    /** Burst of a spore blossom. {@code generation} 0 is the big one; each later generation is smaller. */
    public static void sporeBurst(World world, Location loc, int generation) {
        double spread = 1.1 - 0.3 * generation;
        int spores = 22 - 6 * generation;
        if (generation != 0) {
            world.spawnParticle(Particle.POOF, loc, 4, 0.2, 0.2, 0.2, 0.04, null, true);
        }
        world.spawnParticle(
                Particle.SPORE_BLOSSOM_AIR,
                loc,
                spores,
                spread,
                spread * 0.8,
                spread,
                0,
                null,
                true);
        world.spawnParticle(
                Particle.FALLING_SPORE_BLOSSOM,
                loc,
                spores / 2,
                spread,
                spread * 0.8,
                spread,
                0,
                null,
                true);
        world.spawnParticle(
                Particle.DUST,
                loc,
                spores,
                spread * 0.8,
                spread * 0.6,
                spread * 0.8,
                0,
                POISON_DUST,
                true);
    }
}
