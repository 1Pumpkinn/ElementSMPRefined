package net.rose.elementSMPRefined.particles;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/** Particle effects for the Storm element. */
public final class StormParticles {

    private static final Particle.DustOptions CORE_DUST = new Particle.DustOptions(Color.fromRGB(255, 244, 150), 1.3f);
    private static final Particle.DustOptions EDGE_DUST = new Particle.DustOptions(Color.fromRGB(250, 236, 160), 1.0f);

    private StormParticles() {}

    // ---- Lightning spear ----

    /** Sparks when the spear runs out of range and fizzles. */
    public static void spearFizzle(World world, Location position) {
        world.spawnParticle(Particle.ELECTRIC_SPARK, position, 25, 0.4, 0.4, 0.4, 0.3, null, true);
    }

    /** Glowing tip of the spear. */
    public static void spearTip(World world, Location head) {
        world.spawnParticle(Particle.END_ROD, head, 2, 0.05, 0.05, 0.05, 0.0, null, true);
        world.spawnParticle(Particle.DUST, head, 2, 0.1, 0.1, 0.1, 0.0, EDGE_DUST, true);
    }

    /** One point of the energy streak behind the spear. */
    public static void spearTrail(World world, Location point) {
        world.spawnParticle(Particle.DUST, point, 1, 0.03, 0.03, 0.03, 0.0, CORE_DUST, true);
    }

    /** One loose arc of electricity around the spear shaft. */
    public static void spearArc(World world, Location point) {
        world.spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0.35, 0.35, 0.35, 0.15, null, true);
    }

    /** Sparks on an entity caught in the impact's shock radius. */
    public static void spearAreaShock(Location entityLocation) {
        entityLocation.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, entityLocation.clone().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.3, null, true);
    }

    /** Sparks on an entity that gets shocked (stun / slow). */
    public static void spearShock(Location entityLocation) {
        entityLocation.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, entityLocation.clone().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.4, null, true);
    }

    // ---- Tornado ----

    /** Crit sparks on an entity damaged by the tornado. */
    public static void tornadoDamage(World world, Location chest) {
        world.spawnParticle(Particle.CRIT, chest, 10, 0.3, 0.5, 0.3, 0.2);
    }

    /** One point of the cloud spiral winding up the funnel. */
    public static void tornadoSpiral(World world, double x, double y, double z) {
        world.spawnParticle(Particle.CLOUD,
                x, y, z,
                1, 0.05, 0.05, 0.05, 0.0);
    }

    /** One crumb of ground debris thrown around inside the funnel. */
    public static void tornadoDebris(World world, double x, double y, double z, BlockData ground) {
        world.spawnParticle(Particle.BLOCK,
                x, y, z,
                1, 0.0, 0.0, 0.0, 0.0, ground);
    }

    /** Dust kicked up at the base of the funnel. */
    public static void tornadoBase(World world, double x, double y, double z, double growth) {
        world.spawnParticle(Particle.CLOUD, x, y + 0.2, z,
                3, 1.0 * growth, 0.1, 1.0 * growth, 0.02);
    }

    /** Occasional lightning crackle inside the funnel. */
    public static void tornadoCrackle(World world, Location spark) {
        world.spawnParticle(Particle.ELECTRIC_SPARK, spark, 10, 0.35, 0.35, 0.35, 0.3);
    }

    /** Cloud and poof when the tornado collapses. */
    public static void tornadoCollapse(World world, Location at) {
        world.spawnParticle(Particle.CLOUD, at, 25, 1.0, 0.6, 1.0, 0.05);
        world.spawnParticle(Particle.POOF, at, 12, 0.8, 0.4, 0.8, 0.05);
    }
}
