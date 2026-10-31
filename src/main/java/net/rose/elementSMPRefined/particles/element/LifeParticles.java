package net.rose.elementSMPRefined.particles.element;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

public final class LifeParticles {

    private static final Particle.DustOptions RED_DUST = new Particle.DustOptions(Color.RED, 1.0f);

    private static final Particle.DustOptions GREEN_DUST = new Particle.DustOptions(Color.fromRGB(90, 255, 130), 1.0f);

    private static final Particle.DustOptions BEAM_DUST = new Particle.DustOptions(Color.fromRGB(70, 255, 110), 0.9f);

    private LifeParticles() {}

    public static void totemRingPoint(World world, Location loc) {
        world.spawnParticle(Particle.DUST, loc, 1, 0.05, 0.05, 0.05, 0, GREEN_DUST, true);
    }

    public static void regenRingPoint(World world, Location loc) {
        world.spawnParticle(Particle.DUST, loc, 1, 0.1, 0.1, 0.1, 0, RED_DUST, true);
    }

    /** One spark of the lingering energy beam: a green mote and the odd white fleck. */
    public static void energyBlastSpark(World world, Location pt) {
        world.spawnParticle(Particle.DUST, pt, 2, 0.35, 0.35, 0.35, 0, BEAM_DUST, true);
        world.spawnParticle(Particle.END_ROD, pt, 1, 0.3, 0.3, 0.3, 0.01, null, true);
    }

    /** Sparks drawn in towards the charging orb. */
    public static void energyBlastCharge(World world, Location pt) {
        world.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, BEAM_DUST, true);
    }

    public static void energyBlastMuzzle(World world, Location loc) {
        world.spawnParticle(Particle.END_ROD, loc, 14, 0.25, 0.25, 0.25, 0.12, null, true);
        world.spawnParticle(Particle.DUST, loc, 10, 0.3, 0.3, 0.3, 0, BEAM_DUST, true);
    }

    public static void energyBlastImpact(World world, Location loc) {
        world.spawnParticle(Particle.END_ROD, loc, 18, 0.3, 0.3, 0.3, 0.15, null, true);
        world.spawnParticle(Particle.DUST, loc, 16, 0.5, 0.5, 0.5, 0, BEAM_DUST, true);
    }

    public static void energyBlastHit(World world, Location loc) {
        world.spawnParticle(Particle.END_ROD, loc, 10, 0.3, 0.4, 0.3, 0.1, null, true);
        world.spawnParticle(Particle.DUST, loc, 12, 0.4, 0.5, 0.4, 0, BEAM_DUST, true);
    }
}
