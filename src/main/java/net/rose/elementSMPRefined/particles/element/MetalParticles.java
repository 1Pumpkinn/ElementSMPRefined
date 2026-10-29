package net.rose.elementSMPRefined.particles.element;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

public final class MetalParticles {

    private static final Particle.DustOptions INFLOW_DUST = new Particle.DustOptions(Color.fromRGB(110, 170, 255), 0.5f);

    private MetalParticles() {}

    private static BlockData iron() {
        return Material.IRON_BLOCK.createBlockData();
    }

    public static void shieldHit(Location at, boolean abilityHit) {
        World world = at.getWorld();
        world.spawnParticle(Particle.BLOCK, at, 20, 0.25, 0.35, 0.25, 0.1,
                iron(), true);
        world.spawnParticle(Particle.CRIT, at, abilityHit ? 24 : 12, 0.3, 0.4, 0.3, 0.2, null, true);
    }

    public static void shieldExpire(Location at) {
        at.getWorld().spawnParticle(Particle.BLOCK, at, 20, 0.6, 0.5, 0.6, 0.05,
                iron(), true);
    }

    public static void magnetarCast(Location centre) {
        centre.getWorld().spawnParticle(Particle.BLOCK, centre, 25, 0.4, 0.4, 0.4, 0.1,
                iron(), true);
    }

    public static void magnetarDamage(Location at) {
        at.getWorld().spawnParticle(Particle.CRIT, at, 12, 0.3, 0.5, 0.3, 0.2);
    }

    public static void magnetarFieldPoint(World world, Location loc, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 0.7f);
        world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, dust);
    }

    public static void magnetarJetPoint(World world, Location loc) {
        world.spawnParticle(Particle.END_ROD, loc, 1, 0, 0, 0, 0);
    }

    public static void magnetarShimmer(World world, Location loc) {
        world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 1, 0, 0, 0, 0);
    }

    public static void magnetarInflowPoint(World world, Location loc) {
        world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, INFLOW_DUST);
    }

    public static void magnetarCollapse(World world, Location centre) {
        world.spawnParticle(Particle.BLOCK, centre, 40, 0.5, 0.5, 0.5, 0.15,
                iron(), true);
        world.spawnParticle(Particle.POOF, centre, 10, 0.4, 0.4, 0.4, 0.05);
    }
}
