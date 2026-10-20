package net.rose.elementSMPRefined.particles.element;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/** Particle effects for the Life element. */
public final class LifeParticles {

    private static final Particle.DustOptions RED_DUST = new Particle.DustOptions(Color.RED, 1.0f);

    private LifeParticles() {}

    /** One point of the regeneration ring around the caster. */
    public static void regenRingPoint(World world, Location loc) {
        world.spawnParticle(Particle.DUST, loc, 1, 0.1, 0.1, 0.1, 0, RED_DUST, true);
    }

    /** Hearts where the healing beam hits an ally. */
    public static void healingBeamHit(World world, Location hitLoc) {
        world.spawnParticle(Particle.HEART, hitLoc, 3, 0.2, 0.2, 0.2, 0.0, null, true);
    }

    /** One heart along the healing beam. */
    public static void healingBeamPoint(World world, Location pt) {
        world.spawnParticle(Particle.HEART, pt, 1, 0.05, 0.05, 0.05, 0, null, true);
    }
}
