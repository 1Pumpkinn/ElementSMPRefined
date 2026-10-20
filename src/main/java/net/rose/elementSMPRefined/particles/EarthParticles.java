package net.rose.elementSMPRefined.particles;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/** Particle effects for the Earth element. */
public final class EarthParticles {

    private EarthParticles() {}

    /** Debris at the block being mined by the tunnel ability. */
    public static void tunnelDebris(Location mineLocation) {
        mineLocation.getWorld().spawnParticle(Particle.BLOCK, mineLocation, 10, 0.5, 0.5, 0.5, 0.1, Material.STONE.createBlockData(), true);
    }

    /** Dirt kicked up when the shockwave caster launches. */
    public static void shockwaveLaunch(World world, Location start) {
        world.spawnParticle(Particle.BLOCK, start.clone().add(0, 0.2, 0), 30, 0.6, 0.1, 0.6, 0.0,
                Material.DIRT.createBlockData(), true);
    }

    /** Per-tick stone trail while slamming down. */
    public static void shockwaveSlamTrail(World world, Location casterLocation) {
        world.spawnParticle(Particle.BLOCK, casterLocation.clone().add(0, 1, 0), 6, 0.3, 0.6, 0.3, 0.0,
                Material.STONE.createBlockData(), true);
    }

    /** Explosion at the slam impact point. */
    public static void shockwaveImpact(World world, Location origin) {
        world.spawnParticle(Particle.EXPLOSION, origin, 3, 0.5, 0.2, 0.5, 0.0);
    }

    /** Debris where a ripple block rises out of the ground. */
    public static void shockwaveRipple(World world, Location spawn, BlockData data) {
        world.spawnParticle(Particle.BLOCK, spawn.clone().add(0.5, 0.1, 0.5), 4, 0.4, 0.1, 0.4, 0.0, data, true);
    }
}
