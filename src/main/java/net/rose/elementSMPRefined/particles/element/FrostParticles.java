package net.rose.elementSMPRefined.particles.element;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/** Particle effects for the Frost element. */
public final class FrostParticles {

    private static final BlockData PACKED_ICE_DATA = Material.PACKED_ICE.createBlockData();
    private static final BlockData ICE_DATA = Material.ICE.createBlockData();

    private FrostParticles() {}

    // ---- Ice spikes ability ----

    /** Snowflake at one point of the stomp wind-up ring. */
    public static void iceSpikesWindup(World world, Location point) {
        world.spawnParticle(Particle.SNOWFLAKE, point, 1, 0.05, 0.05, 0.05, 0.0, null, true);
    }

    /** Impact burst of the stomp. */
    public static void iceSpikesStomp(World world, Location origin) {
        Location fx = origin.clone();
        fx.setY(origin.getY() + 0.3);
        world.spawnParticle(Particle.SNOWFLAKE, fx, 60, 1.0, 0.2, 1.0, 0.1, null, true);
        world.spawnParticle(Particle.CLOUD, fx, 20, 0.8, 0.1, 0.8, 0.05, null, true);
        world.spawnParticle(Particle.BLOCK, origin.clone().add(0, 0.2, 0), 30, 0.8, 0.1, 0.8, 0.0,
                PACKED_ICE_DATA, true);
    }

    /** Frost on an entity hit by a spike. */
    public static void iceSpikesHit(Location entityLocation) {
        entityLocation.getWorld().spawnParticle(Particle.SNOWFLAKE, entityLocation.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05, null, true);
    }

    // ---- Icicle barrage ability ----

    /** Random sparks gathering around the orb while charging. */
    public static void barrageChargeSpark(World world, Location sparkLocation) {
        world.spawnParticle(Particle.SNOWFLAKE, sparkLocation, 1, 0, 0, 0, 0.0, null, true);
    }

    /** Light frost haze while the loaded ring waits to fire. */
    public static void barrageRingAura(World world, Location orb) {
        world.spawnParticle(Particle.SNOWFLAKE, orb, 1, 0.3, 0.3, 0.3, 0.0, null, true);
    }

    /** Sparkle when an icicle is placed in the ring. */
    public static void barrageIciclePlaced(World world, Location pos) {
        world.spawnParticle(Particle.SNOWFLAKE, pos, 2, 0.1, 0.1, 0.1, 0.02, null, true);
    }

    /** Burst when an icicle is launched. */
    public static void barrageLaunch(World world, Location pos) {
        world.spawnParticle(Particle.CLOUD, pos, 3, 0.15, 0.15, 0.15, 0.04, null, true);
        world.spawnParticle(Particle.BLOCK, pos, 4, 0.15, 0.15, 0.15, 0.0, PACKED_ICE_DATA, true);
    }

    /** Per-tick trail of a flying icicle. */
    public static void barrageTrail(World world, Location pos) {
        world.spawnParticle(Particle.SNOWFLAKE, pos, 1, 0.05, 0.05, 0.05, 0.0, null, true);
    }

    /** Ice chips where an icicle sticks into a block. */
    public static void barrageStick(World world, Location puff) {
        world.spawnParticle(Particle.BLOCK, puff, 6, 0.15, 0.15, 0.15, 0.0, ICE_DATA, true);
    }

    /** Frost burst where an icicle hits an entity. */
    public static void barrageHit(World world, Location spot) {
        world.spawnParticle(Particle.SNOWFLAKE, spot, 14, 0.25, 0.25, 0.25, 0.05, null, true);
        world.spawnParticle(Particle.BLOCK, spot, 8, 0.2, 0.2, 0.2, 0.0, ICE_DATA, true);
    }

    // ---- Ice visuals (display-entity models) ----

    /** Ground burst when an icicle model segment spawns. */
    public static void icicleSpawn(World world, Location base) {
        world.spawnParticle(Particle.BLOCK, base.clone().add(0, 0.1, 0), 6, 0.3, 0.1, 0.3, 0.0,
                PACKED_ICE_DATA, true);
        world.spawnParticle(Particle.SNOWFLAKE, base.clone().add(0, 0.5, 0), 6, 0.3, 0.4, 0.3, 0.02, null, true);
    }

    /** Ice chips when an icicle model segment shatters. */
    public static void icicleShatter(World world, Location base) {
        world.spawnParticle(Particle.BLOCK, base.clone().add(0, 0.5, 0), 10, 0.4, 0.5, 0.4, 0.0,
                ICE_DATA, true);
    }

    /** Ground burst when an ice spike model rises. */
    public static void iceSpikeRise(World world, double x, double y, double z) {
        Location fx = new Location(world, x, y + 0.1, z);
        world.spawnParticle(Particle.BLOCK, fx, 6, 0.3, 0.1, 0.3, 0.0,
                PACKED_ICE_DATA, true);
        fx.setY(y + 0.5);
        world.spawnParticle(Particle.SNOWFLAKE, fx, 6, 0.3, 0.4, 0.3, 0.02, null, true);
    }

    /** Ice chips when an ice spike model is removed. */
    public static void iceSpikeShatter(World world, double x, double y, double z) {
        world.spawnParticle(Particle.BLOCK, new Location(world, x, y + 0.5, z),
                10, 0.4, 0.5, 0.4, 0.0, ICE_DATA, true);
    }
}
