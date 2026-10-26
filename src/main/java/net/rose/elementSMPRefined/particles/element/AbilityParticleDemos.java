package net.rose.elementSMPRefined.particles.element;

import net.rose.elementSMPRefined.ElementSMPRefined;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntConsumer;

/**
 * Previews of every ability's particle effects, for {@code /element particles <effect>}.
 * <p>
 * Each demo plays that ability's effects from the {@code *Particles} classes at (or in front of) the
 * player, using sample values - so tweaking a particle in its class and re-running the command is the
 * whole feedback loop. Nothing here touches gameplay: no damage, no cooldowns, no blocks.
 */
public final class AbilityParticleDemos {

    @FunctionalInterface
    private interface Demo {
        void play(Player player, ElementSMPRefined plugin);
    }

    private static final Map<String, Demo> DEMOS = new LinkedHashMap<>();

    static {
        // Air
        register("air_dash", AbilityParticleDemos::airDash);
        register("air_cutter", AbilityParticleDemos::airCutter);
        register("air_fall_impact", AbilityParticleDemos::airFallImpact);
        // Earth
        register("earth_tunnel", AbilityParticleDemos::earthTunnel);
        register("earth_shockwave", AbilityParticleDemos::earthShockwave);
        // Fire
        register("fire_starfall", AbilityParticleDemos::fireStarFall);
        register("fire_hellchain", AbilityParticleDemos::fireHellChain);
        // Water
        register("kraken_grasp", AbilityParticleDemos::krakenGrasp);
        register("water_dash", AbilityParticleDemos::waterDash);
        // Death
        register("death_backstab", AbilityParticleDemos::deathBackstab);
        register("death_sidestep", AbilityParticleDemos::deathSideStep);
        // Life
        register("life_regen", AbilityParticleDemos::lifeRegen);
        register("life_beam", AbilityParticleDemos::lifeBeam);
        // Frost
        register("frost_spikes", AbilityParticleDemos::frostSpikes);
        register("frost_barrage", AbilityParticleDemos::frostBarrage);
        // Lava
        register("lava_eruption", AbilityParticleDemos::lavaEruption);
        register("lava_magmabeam", AbilityParticleDemos::lavaMagmaBeam);
        // Metal
        register("metal_shield", AbilityParticleDemos::metalShield);
        register("metal_magnetar", AbilityParticleDemos::metalMagnetar);
        // Storm
        register("storm_spear", AbilityParticleDemos::stormSpear);
        // Reroll gem ring
        register("gem_orbit", AbilityParticleDemos::gemOrbit);
    }

    private AbilityParticleDemos() {}

    private static void register(String key, Demo demo) {
        DEMOS.put(key, demo);
    }


    public static boolean exists(String input) {
        return normalize(input) != null && DEMOS.containsKey(normalize(input));
    }

    /** Plays the named demo. Returns the canonical key if one matched. */
    public static Optional<String> play(String input, Player player, ElementSMPRefined plugin) {
        String key = normalize(input);
        if (key == null || player == null || player.isDead()) {
            return Optional.empty();
        }
        Demo demo = DEMOS.get(key);
        if (demo == null) {
            return Optional.empty();
        }
        demo.play(player, plugin);
        return Optional.of(key);
    }

    public static List<String> getNames() {
        return List.copyOf(DEMOS.keySet());
    }

    public static List<String> filterNames(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return getNames();
        }
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return DEMOS.keySet().stream().filter(name -> name.startsWith(normalized)).toList();
    }

    private static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        return input.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }


    /** A point {@code distance} blocks in front of the player (horizontal), at the player's feet height. */
    private static Location front(Player player, double distance) {
        Vector dir = player.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            dir = new Vector(0, 0, 1);
        }
        return player.getLocation().add(dir.normalize().multiply(distance));
    }

    /** Runs {@code body} once a tick for {@code ticks} ticks (tick index passed in), stopping if the player leaves. */
    private static void repeat(ElementSMPRefined plugin, Player player, int ticks, IntConsumer body) {
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= ticks || !player.isOnline()) {
                    cancel();
                    return;
                }
                body.accept(tick);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static void later(ElementSMPRefined plugin, long delay, Runnable task) {
        new BukkitRunnable() {
            @Override
            public void run() {
                task.run();
            }
        }.runTaskLater(plugin, delay);
    }


    private static void airDash(Player player, ElementSMPRefined plugin) {
        repeat(plugin, player, 10, tick -> {
            AirParticles.dashTrail(player.getLocation());
            if (tick == 5) {
                AirParticles.dashKnockback(front(player, 2).add(0, 0.5, 0));
            }
        });
    }

    private static void airCutter(Player player, ElementSMPRefined plugin) {
        World world = player.getWorld();
        Location origin = player.getLocation().add(0, 1, 0);
        Vector dir = player.getLocation().getDirection().normalize();
        repeat(plugin, player, 8, tick -> {
            Location slice = origin.clone().add(dir.clone().multiply(1.5 * (tick + 1)));
            AirParticles.cutterTrail(world, slice);
            if (tick == 7) {
                AirParticles.cutterHit(slice.clone().add(0, -1, 0));
            }
        });
    }

    private static void airFallImpact(Player player, ElementSMPRefined plugin) {
        World world = player.getWorld();
        Location location = player.getLocation();
        double radius = 4.0;
        int duration = 10;
        repeat(plugin, player, duration, tick -> {
            double progress = (double) tick / duration;
            double currentRadius = radius * (1.0 - progress * 0.7);

            int cloudCount = (int) (20 * (1.0 - progress));
            for (int i = 0; i < cloudCount; i++) {
                double angle = (2 * Math.PI * i) / cloudCount + (progress * Math.PI);
                AirParticles.fallImpactCloud(world, location.clone().add(
                        Math.cos(angle) * currentRadius, 0.3 + progress * 0.5, Math.sin(angle) * currentRadius));
            }

            int smokeCount = (int) (15 * (1.0 - progress));
            for (int i = 0; i < smokeCount; i++) {
                double angle = (2 * Math.PI * i) / smokeCount + (progress * Math.PI * 0.5);
                AirParticles.fallImpactSmoke(world, location.clone().add(
                        Math.cos(angle) * currentRadius * 0.8, 0.1 + progress * 0.3, Math.sin(angle) * currentRadius * 0.8));
            }
        });
    }


    private static void earthTunnel(Player player, ElementSMPRefined plugin) {
        Location mine = front(player, 2).add(0, 1, 0);
        repeat(plugin, player, 6, tick -> EarthParticles.tunnelDebris(mine));
    }

    private static void earthShockwave(Player player, ElementSMPRefined plugin) {
        World world = player.getWorld();
        Location start = player.getLocation();
        BlockData dirt = Material.DIRT.createBlockData();

        EarthParticles.shockwaveLaunch(world, start);
        repeat(plugin, player, 6, tick -> EarthParticles.shockwaveSlamTrail(world, player.getLocation()));
        later(plugin, 8L, () -> {
            Location origin = player.getLocation();
            EarthParticles.shockwaveImpact(world, origin);
            for (int i = 0; i < 16; i++) {
                double a = (Math.PI * 2 * i) / 16;
                EarthParticles.shockwaveRipple(world, origin.clone().add(Math.cos(a) * 3, 0, Math.sin(a) * 3), dirt);
            }
        });
    }


    private static void fireStarFall(Player player, ElementSMPRefined plugin) {
        World world = player.getWorld();
        Location impact = front(player, 5);
        FireParticles.starFallMarkerRing(world, impact, 3.0, 36);
        later(plugin, 20L, () -> FireParticles.starFallImpact(world, impact, 3.0, Material.MAGMA_BLOCK));
    }

    private static void fireHellChain(Player player, ElementSMPRefined plugin) {
        FireParticles.hellChainHook(front(player, 6).add(0, 1, 0), 4.0);
        later(plugin, 10L, () -> FireParticles.hellChainImpact(front(player, 2).add(0, 1, 0)));
        later(plugin, 20L, () -> FireParticles.hellChainMantle(player.getLocation()));
    }


    private static void krakenGrasp(Player player, ElementSMPRefined plugin) {
        Location target = front(player, 6);
        WaterParticles.graspBurst(target);
        WaterParticles.tentacleErupt(target.clone().add(1, 0, 0));
        WaterParticles.tentacleErupt(target.clone().add(-1, 0, 0));
        later(plugin, 35L, () -> WaterParticles.graspRelease(target.clone().add(0, 1, 0)));
    }

    private static void waterDash(Player player, ElementSMPRefined plugin) {
        WaterParticles.surgeBurst(player.getLocation());
        Vector heading = player.getLocation().getDirection().setY(0);
        if (heading.lengthSquared() < 1.0E-4) heading = new Vector(0, 0, 1);
        final Vector dir = heading.normalize();
        repeat(plugin, player, 14, tick -> WaterParticles.surgeWake(player.getLocation(), dir));
        later(plugin, 15L, () -> WaterParticles.surgeEnd(player.getLocation().add(0, 0.5, 0)));
    }


    private static void deathBackstab(Player player, ElementSMPRefined plugin) {
        World world = player.getWorld();
        DeathParticles.shadowBurst(world, player.getLocation().add(0, 1, 0));
        Location behind = front(player, 3).add(0, 1, 0);
        later(plugin, 5L, () -> {
            DeathParticles.shadowBurst(world, behind);
            DeathParticles.backstabHit(world, behind.clone().add(0, 0.1, 0));
        });
    }

    private static void deathSideStep(Player player, ElementSMPRefined plugin) {
        World world = player.getWorld();
        Location origin = player.getLocation().add(0, 1, 0);
        Location destination = front(player, 5).add(0, 1, 0);
        Vector path = destination.toVector().subtract(origin.toVector());

        DeathParticles.shadowBurst(world, origin);
        for (double t = 0.15; t < 1.0; t += 0.15) {
            DeathParticles.shadowTrail(world, origin.clone().add(path.clone().multiply(t)));
        }
        DeathParticles.shadowBurst(world, destination);
    }


    private static void lifeRegen(Player player, ElementSMPRefined plugin) {
        World world = player.getWorld();
        double radius = 5.0;
        repeat(plugin, player, 30, tick -> {
            Location center = player.getLocation();
            for (int i = 0; i < 360; i += 15) {
                double rad = Math.toRadians(i);
                LifeParticles.regenRingPoint(world, center.clone().add(Math.cos(rad) * radius, 0.5, Math.sin(rad) * radius));
            }
        });
    }

    private static void lifeBeam(Player player, ElementSMPRefined plugin) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        for (double d = 2.0; d <= 20; d += 1.0) {
            LifeParticles.healingBeamPoint(world, eye.clone().add(dir.clone().multiply(d)));
        }
        LifeParticles.healingBeamHit(world, eye.clone().add(dir.clone().multiply(10)));
    }


    private static void frostSpikes(Player player, ElementSMPRefined plugin) {
        World world = player.getWorld();
        Location feet = player.getLocation();

        repeat(plugin, player, 12, tick -> {
            double shrink = 1.0 - (tick / 12.0);
            double r = 2.0 * shrink + 0.3;
            for (int i = 0; i < 6; i++) {
                double angle = (Math.PI * 2 * i / 6) + tick * 0.5;
                FrostParticles.iceSpikesWindup(world, feet.clone().add(Math.cos(angle) * r, 0.15, Math.sin(angle) * r));
            }
        });
        later(plugin, 14L, () -> {
            FrostParticles.iceSpikesStomp(world, feet);
            Location spike = front(player, 3);
            FrostParticles.iceSpikeRise(world, spike.getX(), spike.getY(), spike.getZ());
            FrostParticles.icicleSpawn(world, front(player, 4));
            FrostParticles.iceSpikesHit(front(player, 3));
            later(plugin, 25L, () -> {
                FrostParticles.iceSpikeShatter(world, spike.getX(), spike.getY(), spike.getZ());
                FrostParticles.icicleShatter(world, front(player, 4));
            });
        });
    }

    private static void frostBarrage(Player player, ElementSMPRefined plugin) {
        World world = player.getWorld();
        Location orb = player.getEyeLocation().add(player.getLocation().getDirection().multiply(1.5));
        Vector dir = player.getLocation().getDirection().normalize();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Charge: sparks gather around the orb.
        repeat(plugin, player, 16, tick -> {
            double progress = tick / 16.0;
            if (tick % 2 == 0) {
                double r = 1.8 * (1.0 - progress) + 0.4;
                for (int i = 0; i < 6; i++) {
                    Vector offset = Vector.getRandom().subtract(new Vector(0.5, 0.5, 0.5)).normalize().multiply(r);
                    FrostParticles.barrageChargeSpark(world, orb.clone().add(offset));
                }
            }
            if (tick % 4 == 0) {
                FrostParticles.barrageRingAura(world, orb);
                FrostParticles.barrageIciclePlaced(world, orb.clone().add(random.nextDouble(-1, 1), random.nextDouble(-0.5, 0.5), random.nextDouble(-1, 1)));
            }
        });
        // Fire: icicle flies out, sticks, and hits something.
        later(plugin, 18L, () -> {
            FrostParticles.barrageLaunch(world, orb);
            repeat(plugin, player, 10, tick -> FrostParticles.barrageTrail(world, orb.clone().add(dir.clone().multiply(1.2 * (tick + 1)))));
            later(plugin, 12L, () -> {
                Location end = orb.clone().add(dir.clone().multiply(13));
                FrostParticles.barrageStick(world, end);
                FrostParticles.barrageHit(world, end);
            });
        });
    }


    private static void lavaEruption(Player player, ElementSMPRefined plugin) {
        World world = player.getWorld();
        Location center = front(player, 5);
        double radius = 5.0;
        int cracks = 9;
        int warning = 24;
        double[] crackAngle = new double[cracks];
        double[] crackPhase = new double[cracks];
        for (int c = 0; c < cracks; c++) {
            crackAngle[c] = (Math.PI * 2 * c) / cracks;
            crackPhase[c] = c * 1.3;
        }

        // Warning: glowing cracks, a tightening ring, and rising heat.
        repeat(plugin, player, warning, tick -> {
            double progress = (double) tick / warning;
            if (tick % 2 == 0) {
                double reach = radius * (0.3 + 0.8 * progress);
                for (int c = 0; c < cracks; c++) {
                    double dirX = Math.cos(crackAngle[c]);
                    double dirZ = Math.sin(crackAngle[c]);
                    double perpX = -dirZ;
                    double perpZ = dirX;
                    for (double d = 0.25; d <= reach; d += 0.25) {
                        double wobble = Math.sin(d * 4.5 + crackPhase[c]) * 0.18 * Math.min(1.0, d);
                        LavaParticles.eruptionCrackPoint(world, center.clone().add(dirX * d + perpX * wobble, 0.08, dirZ * d + perpZ * wobble));
                    }
                    LavaParticles.eruptionCrackEnd(world, center.clone().add(dirX * reach, 0.12, dirZ * reach));
                }
                double ringRadius = radius * (1.0 - 0.2 * progress);
                int ringPoints = (int) Math.round(radius * 11);
                for (int i = 0; i < ringPoints; i++) {
                    double angle = i * (Math.PI * 2 / ringPoints);
                    LavaParticles.eruptionWarningRingPoint(world, center.clone().add(Math.cos(angle) * ringRadius, 0.1, Math.sin(angle) * ringRadius));
                }
            }
            LavaParticles.eruptionHeat(world, center, radius);
            if (tick % 3 == 0) {
                LavaParticles.eruptionHeatPulse(world, center, radius);
            }
        });

        // Eruption: blast, shockwave ring, core column, droplets, spike tips, embers.
        later(plugin, warning + 2L, () -> {
            Location mid = center.clone().add(0, 1.0, 0);
            LavaParticles.eruptionBlast(world, center, mid);

            int ringPoints = (int) Math.round(radius * 11);
            double columnRadius = 1.2;
            double columnHeight = 3.2;
            ThreadLocalRandom random = ThreadLocalRandom.current();

            repeat(plugin, player, 18, t -> {
                if (t < 7) {
                    double ringRadius = radius * (t + 1) / 7.0;
                    for (int i = 0; i < ringPoints; i++) {
                        double a = i * (Math.PI * 2 / ringPoints);
                        double cos = Math.cos(a);
                        double sin = Math.sin(a);
                        Location p = center.clone().add(cos * ringRadius, 0.12, sin * ringRadius);
                        LavaParticles.eruptionShockwavePoint(world, p);
                        if (i % 5 == 0) LavaParticles.eruptionShockwaveFlame(world, p);
                        if (t > 1 && i % 8 == 0) {
                            LavaParticles.eruptionShockwaveSmoke(world, center.clone().add(cos * ringRadius * 0.7, 0.2, sin * ringRadius * 0.7));
                        }
                    }
                }

                double rise = Math.min(1.0, (t + 1) / 5.0);
                double height = columnHeight * rise;
                double fade = t < 9 ? 1.0 : Math.max(0.0, 1.0 - (t - 9) / 6.0);
                if (fade > 0.0) {
                    for (double y = 0; y <= height; y += 0.35) {
                        double taper = 1.0 - 0.7 * (y / columnHeight);
                        double r = columnRadius * taper;
                        Location level = center.clone().add(0, y, 0);
                        LavaParticles.eruptionColumnLevel(world, level, fade, r);
                        double a = t * 0.8 + y * 2.4;
                        LavaParticles.eruptionColumnHelix(world, level.clone().add(Math.cos(a) * r * 1.4, 0, Math.sin(a) * r * 1.4));
                    }
                    if (t % 2 == 0) {
                        LavaParticles.eruptionColumnFlames(world, center, height, columnRadius);
                    }
                }

                // A few sample droplets on parabolas.
                for (int i = 0; i < 6; i++) {
                    double angle = i * (Math.PI * 2 / 6);
                    double tau = t * 0.6 + 0.2;
                    double y = 0.3 + 1.6 * tau - 0.5 * 0.5 * tau * tau;
                    double d = 1.2 * tau;
                    Location p = center.clone().add(Math.cos(angle) * d, Math.max(0.1, y), Math.sin(angle) * d);
                    if (y <= 0.05) {
                        LavaParticles.eruptionDropletSplash(world, p);
                    } else {
                        LavaParticles.eruptionDroplet(world, p);
                        if (1.6 - 0.5 * tau < 0 && (t + i) % 3 == 0) LavaParticles.eruptionDropletFall(world, p);
                    }
                }

                if (t >= 1 && t <= 14) {
                    for (int i = 0; i < 8; i++) {
                        double a = i * (Math.PI * 2 / 8);
                        LavaParticles.eruptionSpikeTip(world, center.clone().add(Math.cos(a) * radius * 0.7, 2.0, Math.sin(a) * radius * 0.7), t);
                    }
                }
                if (t < 16) {
                    LavaParticles.eruptionEmbers(world, center, radius);
                }
            });
        });
    }

    private static void lavaMagmaBeam(Player player, ElementSMPRefined plugin) {
        World world = player.getWorld();
        Location start = player.getEyeLocation();
        Vector dir = start.getDirection().normalize();
        Location orb = start.clone().add(dir.clone().multiply(0.4));

        // Charge.
        repeat(plugin, player, 12, tick -> {
            double progress = tick / 12.0;
            double radius = 2.6 * (1.0 - progress) + 0.3;
            for (int i = 0; i < 6; i++) {
                Vector offset = Vector.getRandom().subtract(new Vector(0.5, 0.5, 0.5)).normalize().multiply(radius);
                LavaParticles.magmaChargeSpark(world, orb.clone().add(offset));
            }
            LavaParticles.magmaChargeOrb(world, orb);
        });

        // Beam.
        later(plugin, 14L, () -> repeat(plugin, player, 12, beamTick -> {
            Location from = player.getEyeLocation();
            Vector d = from.getDirection().normalize();
            double length = 14;
            for (double dist = 0; dist < length; dist += 0.4) {
                Location p = from.clone().add(d.clone().multiply(dist));
                LavaParticles.magmaBeamSheath(world, p, ((int) (dist / 0.4) + beamTick) % 3 == 0);
            }
            // Double helix around the beam.
            Vector side = d.clone().crossProduct(new Vector(0, 1, 0));
            if (side.lengthSquared() < 1.0E-4) side = new Vector(1, 0, 0);
            side.normalize();
            Vector up = side.clone().crossProduct(d).normalize();
            for (double dist = 0; dist < length; dist += 0.5) {
                for (int strand = 0; strand < 2; strand++) {
                    double a = dist * 1.4 + beamTick * 0.6 + strand * Math.PI;
                    Location p = from.clone().add(d.clone().multiply(dist))
                            .add(side.clone().multiply(Math.cos(a) * 0.5)).add(up.clone().multiply(Math.sin(a) * 0.5));
                    LavaParticles.magmaBeamHelix(world, p);
                }
            }
            Location end = from.clone().add(d.clone().multiply(length));
            LavaParticles.magmaBeamImpact(world, end);
            if (beamTick % 3 == 0) {
                LavaParticles.magmaBeamBurn(world, end.clone().add(0, -1, 0));
            }
        }));
    }


    private static void metalShield(Player player, ElementSMPRefined plugin) {
        Location at = player.getLocation().add(0, 1, 0);
        MetalParticles.shieldHit(at, false);
        later(plugin, 15L, () -> MetalParticles.shieldHit(at, true));
        later(plugin, 30L, () -> MetalParticles.shieldExpire(at));
    }

    private static void metalMagnetar(Player player, ElementSMPRefined plugin) {
        World world = player.getWorld();
        Location centre = front(player, 4).add(0, 1.5, 0);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        MetalParticles.magnetarCast(centre);
        repeat(plugin, player, 40, tick -> {
            // Field loops (a few simple rings tilted around the axis).
            for (int shell = 0; shell < 3; shell++) {
                double reach = 1.0 + shell * 0.6;
                for (int i = 0; i < 16; i++) {
                    double a = (Math.PI * 2 * i / 16) + tick * 0.15;
                    double polar = Math.abs(Math.cos(a));
                    Vector local = new Vector(Math.cos(a) * reach, Math.sin(a) * reach * 0.6, 0).rotateAroundY(tick * 0.1 + shell);
                    Color color = Color.fromRGB(
                            (int) (60 + 195 * polar * polar),
                            (int) (120 + 135 * polar * polar),
                            255);
                    MetalParticles.magnetarFieldPoint(world, centre.clone().add(local), color);
                }
            }
            // Jets out of both poles.
            for (int sign = -1; sign <= 1; sign += 2) {
                for (int i = 0; i < 6; i++) {
                    double distance = 0.5 + ((i / 6.0 + tick * 0.05) % 1.0) * 2.5;
                    MetalParticles.magnetarJetPoint(world, centre.clone().add(0, sign * distance, 0)
                            .add(random.nextDouble(-0.05, 0.05), 0, random.nextDouble(-0.05, 0.05)));
                }
            }
            // Shimmer on the core surface.
            for (int i = 0; i < 6; i++) {
                Vector dir = new Vector(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
                if (dir.lengthSquared() < 1.0E-6) continue;
                MetalParticles.magnetarShimmer(world, centre.clone().add(dir.normalize().multiply(0.5)));
            }
            // Inflow streams.
            for (int s = 0; s < 4; s++) {
                for (int i = 0; i < 8; i++) {
                    double f = ((double) i / 8 - tick * 0.03 + s * 0.25 + 2.0) % 1.0;
                    double radius = 0.8 + f * f * 4.0;
                    double a = radius * 1.5 + s * Math.PI / 2;
                    MetalParticles.magnetarInflowPoint(world, centre.clone().add(Math.cos(a) * radius, Math.sin(a * 0.7) * radius * 0.6, Math.sin(a) * radius));
                }
            }
            if (tick % 10 == 0) {
                MetalParticles.magnetarDamage(centre.clone().add(1, 0, 0));
            }
        });
        later(plugin, 42L, () -> MetalParticles.magnetarCollapse(world, centre));
    }


    private static void stormSpear(Player player, ElementSMPRefined plugin) {
        World world = player.getWorld();
        Location start = player.getEyeLocation();
        Vector dir = start.getDirection().normalize();
        double spearLength = 2.5;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        repeat(plugin, player, 16, tick -> {
            Location head = start.clone().add(dir.clone().multiply(1.5 + tick * 1.2));
            StormParticles.spearTip(world, head);
            Location tail = head.clone().subtract(dir.clone().multiply(spearLength));
            for (double d = 0; d <= spearLength; d += 0.75) {
                StormParticles.spearTrail(world, tail.clone().subtract(dir.clone().multiply(d)));
            }
            for (int i = 0; i < 3; i++) {
                StormParticles.spearArc(world, head.clone().subtract(dir.clone().multiply(random.nextDouble() * spearLength)));
            }
        });
        later(plugin, 17L, () -> {
            Location end = start.clone().add(dir.clone().multiply(1.5 + 16 * 1.2));
            StormParticles.spearFizzle(world, end);
            StormParticles.spearShock(end.clone().add(0, -1, 0));
            StormParticles.spearAreaShock(end.clone().add(1.5, -1, 0));
        });
    }

    private static void gemOrbit(Player player, ElementSMPRefined plugin) {
        World world = player.getWorld();
        Location gem = front(player, 2).add(0, 1.3, 0);
        GemParticles.survivorSettle(world, gem);
        repeat(plugin, player, 16, tick -> {
            if (tick % 4 == 0) GemParticles.survivorGlint(world, gem);
        });
        later(plugin, 8L, () -> GemParticles.gemEliminated(world, front(player, 2).add(1, 1.3, 0)));
        later(plugin, 18L, () -> GemParticles.survivorVanish(world, gem));
    }
}
