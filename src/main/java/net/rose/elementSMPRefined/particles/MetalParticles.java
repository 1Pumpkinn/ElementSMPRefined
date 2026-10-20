package net.rose.elementSMPRefined.particles;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/** Particle effects for the Metal element. */
public final class MetalParticles {

    private static final Particle.DustOptions INFLOW_DUST = new Particle.DustOptions(Color.fromRGB(110, 170, 255), 0.5f);

    private MetalParticles() {}

    private static BlockData iron() {
        return Material.IRON_BLOCK.createBlockData();
    }

    // ---- Metal shield ----

    /** Iron chips and crits where a shield segment absorbs a hit. */
    public static void shieldHit(Location at, boolean abilityHit) {
        World world = at.getWorld();
        world.spawnParticle(Particle.BLOCK, at, 20, 0.25, 0.35, 0.25, 0.1,
                iron(), true);
        world.spawnParticle(Particle.CRIT, at, abilityHit ? 24 : 12, 0.3, 0.4, 0.3, 0.2, null, true);
    }

    /** Iron chips when the shield expires. */
    public static void shieldExpire(Location at) {
        at.getWorld().spawnParticle(Particle.BLOCK, at, 20, 0.6, 0.5, 0.6, 0.05,
                iron(), true);
    }

    // ---- Magnetar ----

    /** Iron burst when the magnetar core is placed. */
    public static void magnetarCast(Location centre) {
        centre.getWorld().spawnParticle(Particle.BLOCK, centre, 25, 0.4, 0.4, 0.4, 0.1,
                iron(), true);
    }

    /** Crit sparks on an entity damaged by the core. */
    public static void magnetarDamage(Location at) {
        at.getWorld().spawnParticle(Particle.CRIT, at, 12, 0.3, 0.5, 0.3, 0.2);
    }

    /** One point on a dipole field loop; {@code color} fades from blue to white towards the poles. */
    public static void magnetarFieldPoint(World world, Location loc, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 0.7f);
        world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, dust);
    }

    /** One point of the light beams blasting out of the magnetic poles. */
    public static void magnetarJetPoint(World world, Location loc) {
        world.spawnParticle(Particle.END_ROD, loc, 1, 0, 0, 0, 0);
    }

    /** Crackling spark on the core's surface. */
    public static void magnetarShimmer(World world, Location loc) {
        world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 1, 0, 0, 0, 0);
    }

    /** One point of the inward-spiralling pull streams. */
    public static void magnetarInflowPoint(World world, Location loc) {
        world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, INFLOW_DUST);
    }

    /** Iron burst and poof when the core collapses. */
    public static void magnetarCollapse(World world, Location centre) {
        world.spawnParticle(Particle.BLOCK, centre, 40, 0.5, 0.5, 0.5, 0.15,
                iron(), true);
        world.spawnParticle(Particle.POOF, centre, 10, 0.4, 0.4, 0.4, 0.05);
    }
}
