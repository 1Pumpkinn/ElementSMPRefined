package net.rose.elementSMPRefined.util.visual;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * Enhanced particle patterns with animations, 3D shapes, and advanced effects.
 * Provides comprehensive particle system with animations and complex patterns.
 */
public final class ParticlePatterns {

    public record CircleConfig(
            Location center,
            double radius,
            Particle particle,
            int points,
            boolean raiseAboveGround,
            double yOffset
    ) {
        public CircleConfig {
            if (radius <= 0) throw new IllegalArgumentException("Radius must be positive");
            if (points <= 0) throw new IllegalArgumentException("Points must be positive");
        }
    }

    public record LineConfig(
            Location start,
            Location end,
            Particle particle,
            double spacing
    ) {
        public LineConfig {
            if (spacing <= 0) throw new IllegalArgumentException("Spacing must be positive");
        }
    }

    public record ExpandingRingConfig(
            Location center,
            double startRadius,
            double endRadius,
            int steps,
            Particle particle,
            long delayBetweenSteps
    ) {
        public ExpandingRingConfig {
            if (startRadius < 0 || endRadius < 0) {
                throw new IllegalArgumentException("Radii must be non-negative");
            }
            if (steps <= 0) throw new IllegalArgumentException("Steps must be positive");
        }
    }

    public record SpiralConfig(
            Location center,
            double radius,
            double height,
            int rotations,
            int pointsPerRotation,
            Particle particle,
            boolean clockwise
    ) {
        public SpiralConfig {
            if (radius <= 0) throw new IllegalArgumentException("Radius must be positive");
            if (rotations <= 0) throw new IllegalArgumentException("Rotations must be positive");
            if (pointsPerRotation <= 0) throw new IllegalArgumentException("Points per rotation must be positive");
        }
    }

    public record SphereConfig(
            Location center,
            double radius,
            Particle particle,
            int points,
            boolean hollow
    ) {
        public SphereConfig {
            if (radius <= 0) throw new IllegalArgumentException("Radius must be positive");
            if (points <= 0) throw new IllegalArgumentException("Points must be positive");
        }
    }

    public record HelixConfig(
            Location center,
            double radius,
            double height,
            int coils,
            int pointsPerCoil,
            Particle particle,
            boolean clockwise
    ) {
        public HelixConfig {
            if (radius <= 0) throw new IllegalArgumentException("Radius must be positive");
            if (coils <= 0) throw new IllegalArgumentException("Coils must be positive");
            if (pointsPerCoil <= 0) throw new IllegalArgumentException("Points per coil must be positive");
        }
    }

    /**
     * Spawn a circle of particles
     */
    public static void spawnCircle(CircleConfig config) {
        World world = config.center().getWorld();
        if (world == null) return;

        double angleStepRad = (2 * Math.PI) / config.points();

        for (int i = 0; i < config.points(); i++) {
            double rad = i * angleStepRad;
            double x = Math.cos(rad) * config.radius();
            double z = Math.sin(rad) * config.radius();

            Location particleLoc = config.center().clone().add(x, config.yOffset(), z);

            if (config.raiseAboveGround()) {
                ensureAboveGround(particleLoc);
            }

            world.spawnParticle(config.particle(), particleLoc, 1,
                    0.1, 0.1, 0.1, 0, null, true);
        }
    }

    /**
     * Spawn a line of particles between two points
     */
    public static void spawnLine(LineConfig config) {
        World world = config.start().getWorld();
        if (world == null || !world.equals(config.end().getWorld())) return;

        double distance = config.start().distance(config.end());
        int points = (int) (distance / config.spacing());

        Vector direction = config.end().toVector()
                .subtract(config.start().toVector());

        for (int i = 0; i <= points; i++) {
            double t = i / (double) points;
            Location point = config.start().clone()
                    .add(direction.clone().multiply(t));

            world.spawnParticle(config.particle(), point, 1,
                    0.05, 0.05, 0.05, 0, null, true);
        }
    }

    /**
     * Spawn a vertical spiral of particles
     */
    public static void spawnSpiral(SpiralConfig config) {
        World world = config.center().getWorld();
        if (world == null) return;

        int totalPoints = config.rotations() * config.pointsPerRotation();
        double angleStep = (2 * Math.PI * config.rotations()) / totalPoints;
        double heightStep = config.height() / totalPoints;

        for (int i = 0; i < totalPoints; i++) {
            double angle = i * angleStep;
            if (!config.clockwise()) angle = -angle;

            double x = Math.cos(angle) * config.radius();
            double z = Math.sin(angle) * config.radius();
            double y = (i * heightStep) + config.center().getY();

            Location particleLoc = config.center().clone().add(x, y - config.center().getY(), z);

            world.spawnParticle(config.particle(), particleLoc, 1,
                    0.1, 0.1, 0.1, 0, null, true);
        }
    }

    /**
     * Spawn a sphere of particles
     */
    public static void spawnSphere(SphereConfig config) {
        World world = config.center().getWorld();
        if (world == null) return;

        // Use Fibonacci sphere algorithm for even distribution
        double phi = Math.PI * (3 - Math.sqrt(5)); // Golden angle

        for (int i = 0; i < config.points(); i++) {
            double y = 1 - (i / (double) (config.points() - 1)) * 2; // y goes from 1 to -1
            double radiusAtY = Math.sqrt(1 - y * y); // Radius at y

            double theta = phi * i; // Golden angle increment

            double x = Math.cos(theta) * radiusAtY;
            double z = Math.sin(theta) * radiusAtY;

            // Scale by radius
            x *= config.radius();
            y *= config.radius();
            z *= config.radius();

            // For hollow sphere, only use surface points
            if (config.hollow()) {
                double distance = Math.sqrt(x*x + y*y + z*z);
                if (Math.abs(distance - config.radius()) > 0.1) continue;
            }

            Location particleLoc = config.center().clone().add(x, y, z);

            world.spawnParticle(config.particle(), particleLoc, 1,
                    0.1, 0.1, 0.1, 0, null, true);
        }
    }

    /**
     * Spawn a DNA helix pattern
     */
    public static void spawnHelix(HelixConfig config) {
        World world = config.center().getWorld();
        if (world == null) return;

        int totalPoints = config.coils() * config.pointsPerCoil();
        double angleStep = (2 * Math.PI * config.coils()) / totalPoints;
        double heightStep = config.height() / totalPoints;

        for (int i = 0; i < totalPoints; i++) {
            double angle = i * angleStep;
            if (!config.clockwise()) angle = -angle;

            // First strand
            double x1 = Math.cos(angle) * config.radius();
            double z1 = Math.sin(angle) * config.radius();
            double y1 = (i * heightStep) + config.center().getY();

            Location loc1 = config.center().clone().add(x1, y1 - config.center().getY(), z1);
            world.spawnParticle(config.particle(), loc1, 1, 0.1, 0.1, 0.1, 0, null, true);

            // Second strand (opposite side)
            double x2 = Math.cos(angle + Math.PI) * config.radius();
            double z2 = Math.sin(angle + Math.PI) * config.radius();
            double y2 = y1;

            Location loc2 = config.center().clone().add(x2, y2 - config.center().getY(), z2);
            world.spawnParticle(config.particle(), loc2, 1, 0.1, 0.1, 0.1, 0, null, true);
        }
    }

    /**
     * Animated expanding ring with animation support
     */
    public static void animateExpandingRing(ExpandingRingConfig config, net.rose.elementSMPRefined.ElementSMPRefined plugin) {
        new org.bukkit.scheduler.BukkitRunnable() {
            private int step = 0;
            private final double radiusIncrement = (config.endRadius() - config.startRadius()) / config.steps();

            @Override
            public void run() {
                if (step >= config.steps()) {
                    this.cancel();
                    return;
                }

                double currentRadius = config.startRadius() + (step * radiusIncrement);

                CircleConfig circleConfig = new CircleConfig(
                        config.center(),
                        currentRadius,
                        config.particle(),
                        36,
                        true,
                        0.5
                );

                spawnCircle(circleConfig);
                step++;
            }
        }.runTaskTimer(plugin, 0, config.delayBetweenSteps());
    }

    /**
     * Create a burst explosion effect
     */
    public static void createBurst(Location center, Particle particle, int particles, double radius) {
        World world = center.getWorld();
        if (world == null) return;

        for (int i = 0; i < particles; i++) {
            double theta = Math.random() * 2 * Math.PI;
            double phi = Math.random() * Math.PI;

            double x = radius * Math.sin(phi) * Math.cos(theta);
            double y = radius * Math.sin(phi) * Math.sin(theta);
            double z = radius * Math.cos(phi);

            Location particleLoc = center.clone().add(x, y, z);

            world.spawnParticle(particle, particleLoc, 1,
                    0.1, 0.1, 0.1, 0, null, true);
        }
    }

    /**
     * Create a vortex/tornado effect
     */
    public static void createVortex(Location center, Particle particle, double radius, double height,
                                    int pointsPerLevel, int levels) {
        World world = center.getWorld();
        if (world == null) return;

        for (int level = 0; level < levels; level++) {
            double currentRadius = radius * (1 - (level / (double) levels));
            double currentHeight = (level / (double) levels) * height;

            for (int i = 0; i < pointsPerLevel; i++) {
                double angle = (i / (double) pointsPerLevel) * 2 * Math.PI;

                double x = Math.cos(angle) * currentRadius;
                double z = Math.sin(angle) * currentRadius;

                Location particleLoc = center.clone().add(x, currentHeight, z);

                world.spawnParticle(particle, particleLoc, 1,
                        0.1, 0.1, 0.1, 0, null, true);
            }
        }
    }

    /**
     * Create a wave effect
     */
    public static void createWave(Location center, Particle particle, double radius, int points) {
        World world = center.getWorld();
        if (world == null) return;

        for (int i = 0; i < points; i++) {
            double angle = (i / (double) points) * 2 * Math.PI;

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            // Add wave height variation
            double y = Math.sin(angle * 3) * 0.5;

            Location particleLoc = center.clone().add(x, y, z);

            world.spawnParticle(particle, particleLoc, 1,
                    0.1, 0.1, 0.1, 0, null, true);
        }
    }

    private static void ensureAboveGround(Location loc) {
        int maxRaise = 3;
        while (loc.getBlock().getType().isSolid() && maxRaise > 0) {
            loc.add(0, 1, 0);
            maxRaise--;
        }
    }

    private ParticlePatterns() {}
}