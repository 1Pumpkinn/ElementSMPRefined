package net.rose.elementSMPRefined.particles.element;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/** Particle effects for the Lava element. */
public final class LavaParticles {

    private static final BlockData MAGMA = Material.MAGMA_BLOCK.createBlockData();
    private static final BlockData BASALT = Material.BASALT.createBlockData();

    private static final Particle.DustOptions CORE_DUST = new Particle.DustOptions(Color.fromRGB(255, 130, 25), 1.5f);
    private static final Particle.DustOptions EDGE_DUST = new Particle.DustOptions(Color.fromRGB(210, 45, 0), 1.0f);
    private static final Particle.DustOptions DROP_DUST = new Particle.DustOptions(Color.fromRGB(255, 160, 40), 1.1f);
    private static final Particle.DustTransition CRACK_DUST =
            new Particle.DustTransition(Color.fromRGB(255, 150, 30), Color.fromRGB(120, 15, 0), 0.9f);
    private static final Particle.DustTransition RISING_DUST =
            new Particle.DustTransition(Color.fromRGB(255, 175, 45), Color.fromRGB(110, 20, 0), 1.4f);
    private static final Particle.DustTransition TIP_DUST =
            new Particle.DustTransition(Color.fromRGB(255, 200, 80), Color.fromRGB(200, 40, 0), 1.0f);

    private LavaParticles() {}

    // Eruption: warning phase

    /** One point of a glowing crack creeping out from the centre. */
    public static void eruptionCrackPoint(World world, Location p) {
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, p, 1, 0.02, 0.0, 0.02, 0.0, CRACK_DUST, true);
    }

    /** Flame at the leading end of a crack. */
    public static void eruptionCrackEnd(World world, Location end) {
        world.spawnParticle(Particle.SMALL_FLAME, end, 1, 0.03, 0.03, 0.03, 0.0, null, true);
    }

    /** One point of the ring marking the blast radius. */
    public static void eruptionWarningRingPoint(World world, Location p) {
        world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0.0, EDGE_DUST, true);
    }

    /** Ash rising out of the cracked ground (every tick of the warning). */
    public static void eruptionHeat(World world, Location center, double radius) {
        world.spawnParticle(Particle.ASH, center.clone().add(0, 0.3, 0), 3, radius * 0.5, 0.3, radius * 0.5, 0.0, null, true);
    }

    /** Lava pops and magma crumbs (every few ticks of the warning). */
    public static void eruptionHeatPulse(World world, Location center, double radius) {
        world.spawnParticle(Particle.LAVA, center.clone().add(0, 0.2, 0), 2, radius * 0.5, 0.1, radius * 0.5, 0.0, null, true);
        world.spawnParticle(Particle.BLOCK, center.clone().add(0, 0.15, 0), 6, radius * 0.4, 0.05, radius * 0.4, 0.0, MAGMA, true);
    }

    // Eruption: blast

    /** The initial blast burst. */
    public static void eruptionBlast(World world, Location center, Location mid) {
        world.spawnParticle(Particle.EXPLOSION, center.clone().add(0, 0.4, 0), 1, 0, 0, 0, 0.0, null, true);
        world.spawnParticle(Particle.LAVA, mid, 25, 0.7, 0.8, 0.7, 0.0, null, true);
        world.spawnParticle(Particle.LARGE_SMOKE, mid, 14, 0.6, 1.0, 0.6, 0.03, null, true);
        world.spawnParticle(Particle.BLOCK, mid, 45, 0.9, 1.0, 0.9, 0.0, MAGMA, true);
        world.spawnParticle(Particle.BLOCK, mid, 25, 0.9, 1.0, 0.9, 0.0, BASALT, true);
    }

    /** One point of the bright shockwave ring along the ground. */
    public static void eruptionShockwavePoint(World world, Location p) {
        world.spawnParticle(Particle.DUST, p, 1, 0.04, 0.03, 0.04, 0.0, EDGE_DUST, true);
    }

    /** Small flame sprinkled along the shockwave ring. */
    public static void eruptionShockwaveFlame(World world, Location p) {
        world.spawnParticle(Particle.SMALL_FLAME, p, 1, 0, 0.03, 0, 0.01, null, true);
    }

    /** Trailing smoke inside the shockwave ring. */
    public static void eruptionShockwaveSmoke(World world, Location inner) {
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, inner, 0, 0, 0.06, 0, 1.0, null, true);
    }

    /** One level of the core column, with a brighter core while it hasn't faded much. */
    public static void eruptionColumnLevel(World world, Location level, double fade, double r) {
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, level, Math.max(1, (int) Math.round(2 * fade)),
                r * 0.4, 0.12, r * 0.4, 0.0, RISING_DUST, true);
        if (fade > 0.4) {
            world.spawnParticle(Particle.DUST, level, 1, r * 0.2, 0.08, r * 0.2, 0.0, CORE_DUST, true);
        }
    }

    /** One point of the helix winding around the core column. */
    public static void eruptionColumnHelix(World world, Location s) {
        world.spawnParticle(Particle.DUST, s, 1, 0, 0, 0, 0.0, CORE_DUST, true);
    }

    /** Flames licking through the middle of the core column. */
    public static void eruptionColumnFlames(World world, Location center, double height, double columnRadius) {
        world.spawnParticle(Particle.FLAME, center.clone().add(0, height * 0.5, 0), 2,
                columnRadius * 0.3, height * 0.4, columnRadius * 0.3, 0.03, null, true);
    }

    /** Lava and flames where a droplet lands. */
    public static void eruptionDropletSplash(World world, Location splash) {
        world.spawnParticle(Particle.LAVA, splash, 1, 0.05, 0.0, 0.05, 0.0, null, true);
        world.spawnParticle(Particle.SMALL_FLAME, splash, 2, 0.08, 0.02, 0.08, 0.02, null, true);
    }

    /** One point along a droplet's flight. */
    public static void eruptionDroplet(World world, Location p) {
        world.spawnParticle(Particle.DUST, p, 1, 0.02, 0.02, 0.02, 0.0, DROP_DUST, true);
    }

    /** Falling lava from a droplet past its apex. */
    public static void eruptionDropletFall(World world, Location p) {
        world.spawnParticle(Particle.FALLING_LAVA, p, 1, 0, 0, 0, 0.0, null, true);
    }

    /** Glowing tip and lava drip on a standing spike; {@code t} is the eruption tick. */
    public static void eruptionSpikeTip(World world, Location tip, int t) {
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, tip, 2, 0.08, 0.08, 0.08, 0.0, TIP_DUST, true);
        if (t % 2 == 0) world.spawnParticle(Particle.SMALL_FLAME, tip, 1, 0.05, 0.05, 0.05, 0.01, null, true);
        if (t % 4 == 0) world.spawnParticle(Particle.DRIPPING_LAVA, tip.clone().add(0, -0.3, 0), 1, 0.1, 0.0, 0.1, 0.0, null, true);
    }

    /** Embers drifting up over the blast area. */
    public static void eruptionEmbers(World world, Location center, double radius) {
        world.spawnParticle(Particle.ASH, center.clone().add(0, 1.0, 0), 4, radius * 0.8, 1.0, radius * 0.8, 0.0, null, true);
    }

    // Magma beam

    /** One spark gathering around the orb while charging. */
    public static void magmaChargeSpark(World world, Location loc) {
        world.spawnParticle(Particle.FLAME, loc, 1, 0, 0, 0, 0.0, null, true);
    }

    /** Lava glow at the charging orb. */
    public static void magmaChargeOrb(World world, Location orb) {
        world.spawnParticle(Particle.LAVA, orb, 1, 0.2, 0.2, 0.2, 0.0, null, true);
    }

    /** One point of the double helix around the beam. */
    public static void magmaBeamHelix(World world, Location p) {
        world.spawnParticle(Particle.FLAME, p, 1, 0, 0, 0, 0.0, null, true);
    }

    /** One point of the beam sheath, optionally with a lava spit. */
    public static void magmaBeamSheath(World world, Location p, boolean lava) {
        world.spawnParticle(Particle.FLAME, p, 1, 0.12, 0.12, 0.12, 0.0, null, true);
        if (lava) {
            world.spawnParticle(Particle.LAVA, p, 1, 0.2, 0.2, 0.2, 0.0, null, true);
        }
    }

    /** Lava and smoke where the beam hits a block. */
    public static void magmaBeamImpact(World world, Location impact) {
        world.spawnParticle(Particle.LAVA, impact, 2, 0.2, 0.2, 0.2, 0.0, null, true);
        world.spawnParticle(Particle.SMOKE, impact, 3, 0.2, 0.2, 0.2, 0.02, null, true);
    }

    /** Flames on an entity burned by the beam. */
    public static void magmaBeamBurn(World world, Location entityLocation) {
        world.spawnParticle(Particle.FLAME, entityLocation.clone().add(0, 1, 0), 8, 0.3, 0.4, 0.3, 0.03, null, true);
    }
}
