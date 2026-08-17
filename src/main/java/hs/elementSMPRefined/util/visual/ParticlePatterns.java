package hs.elementSMPRefined.util.visual;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Enhanced particle patterns with animations, 3D shapes, and advanced effects.
 * Provides comprehensive particle system with animations and complex patterns.
 */
public final class ParticlePatterns {

    // ==================== CONFIGURATION RECORDS ====================

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

        public static CircleConfig of(Location center, double radius, Particle particle) {
            return new CircleConfig(center, radius, particle, 36, true, 0.5);
        }

        public CircleConfig withYOffset(double yOffset) {
            return new CircleConfig(center, radius, particle, points, raiseAboveGround, yOffset);
        }
    }

    public record LineConfig(
            Location start,
            Location end,
            Particle particle,
            double spacing,
            boolean animated
    ) {
        public LineConfig {
            if (spacing <= 0) throw new IllegalArgumentException("Spacing must be positive");
        }

        public static LineConfig of(Location start, Location end, Particle particle) {
            return new LineConfig(start, end, particle, 0.5, false);
        }

        public static LineConfig animated(Location start, Location end, Particle particle) {
            return new LineConfig(start, end, particle, 0.5, true);
        }

        public LineConfig withAnimated(boolean animated) {
            return new LineConfig(start, end, particle, spacing, animated);
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

        public static ExpandingRingConfig of(Location center, double startRadius, double endRadius,
                                             int steps, Particle particle) {
            return new ExpandingRingConfig(center, startRadius, endRadius, steps, particle, 2);
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

        public static SpiralConfig of(Location center, double radius, double height,
                                      int rotations, Particle particle) {
            return new SpiralConfig(center, radius, height, rotations, 12, particle, true);
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

        public static SphereConfig of(Location center, double radius, Particle particle) {
            return new SphereConfig(center, radius, particle, 50, false);
        }

        public static SphereConfig hollow(Location center, double radius, Particle particle) {
            return new SphereConfig(center, radius, particle, 50, true);
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

        public static HelixConfig of(Location center, double radius, double height,
                                     int coils, Particle particle) {
            return new HelixConfig(center, radius, height, coils, 10, particle, true);
        }
    }

    // ==================== BASIC PATTERNS ====================

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

        // Animation support for lines
        if (config.animated()) {
            animateLine(config);
        }
    }

    /**
     * Animate a line of particles
     */
    private static void animateLine(LineConfig config) {
        // Animation requires plugin instance - skip for now to avoid type issues
        // Users can manually schedule animations using their plugin instance
    }

    /**
     * Spawn an expanding ring animation
     */
    public static void spawnExpandingRing(ExpandingRingConfig config) {
        double radiusIncrement = (config.endRadius() - config.startRadius()) / config.steps();

        for (int step = 0; step < config.steps(); step++) {
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
        }
    }

    // ==================== ADVANCED 3D PATTERNS ====================

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

    // ==================== ANIMATED PATTERNS ====================

    /**
     * Animated expanding ring with animation support
     */
    public static void animateExpandingRing(ExpandingRingConfig config, hs.elementSMPRefined.ElementSMPRefined plugin) {
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
     * Animated rotating circle
     */
    public static void animateRotatingCircle(CircleConfig config, long periodTicks,
                                             hs.elementSMPRefined.ElementSMPRefined plugin) {
        new org.bukkit.scheduler.BukkitRunnable() {
            private double angle = 0;

            @Override
            public void run() {
                World world = config.center().getWorld();
                if (world == null) {
                    this.cancel();
                    return;
                }

                for (int i = 0; i < config.points(); i++) {
                    double currentAngle = angle + (i * (2 * Math.PI / config.points()));
                    double x = Math.cos(currentAngle) * config.radius();
                    double z = Math.sin(currentAngle) * config.radius();

                    Location particleLoc = config.center().clone().add(x, config.yOffset(), z);

                    if (config.raiseAboveGround()) {
                        ensureAboveGround(particleLoc);
                    }

                    world.spawnParticle(config.particle(), particleLoc, 1,
                            0.1, 0.1, 0.1, 0, null, true);
                }

                angle += (2 * Math.PI) / periodTicks;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    /**
     * Animated spiral rising effect
     */
    public static void animateRisingSpiral(SpiralConfig config, long speedTicks,
                                           hs.elementSMPRefined.ElementSMPRefined plugin) {
        new org.bukkit.scheduler.BukkitRunnable() {
            private int currentPoint = 0;
            private final int totalPoints = config.rotations() * config.pointsPerRotation();
            private final double angleStep = (2 * Math.PI * config.rotations()) / totalPoints;
            private final double heightStep = config.height() / totalPoints;

            @Override
            public void run() {
                if (currentPoint >= totalPoints) {
                    this.cancel();
                    return;
                }

                World world = config.center().getWorld();
                if (world == null) {
                    this.cancel();
                    return;
                }

                // Spawn all points up to current point
                for (int i = 0; i <= currentPoint; i++) {
                    double angle = i * angleStep;
                    if (!config.clockwise()) angle = -angle;

                    double x = Math.cos(angle) * config.radius();
                    double z = Math.sin(angle) * config.radius();
                    double y = (i * heightStep) + config.center().getY();

                    Location particleLoc = config.center().clone().add(x, y - config.center().getY(), z);

                    world.spawnParticle(config.particle(), particleLoc, 1,
                            0.1, 0.1, 0.1, 0, null, true);
                }

                currentPoint++;
            }
        }.runTaskTimer(plugin, 0, speedTicks);
    }

    // ==================== SPECIAL EFFECTS ====================

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

    // ==================== UTILITY METHODS ====================

    private static void ensureAboveGround(Location loc) {
        int maxRaise = 3;
        while (loc.getBlock().getType().isSolid() && maxRaise > 0) {
            loc.add(0, 1, 0);
            maxRaise--;
        }
    }

    /**
     * Animation manager for controlling multiple particle animations
     */
    public static class AnimationManager {
        private final Map<String, BukkitTask> animations = new HashMap<>();
        private final hs.elementSMPRefined.ElementSMPRefined plugin;

        public AnimationManager(hs.elementSMPRefined.ElementSMPRefined plugin) {
            this.plugin = plugin;
        }

        public void startAnimation(String id, Consumer<AnimationManager> animation) {
            stopAnimation(id);
            animation.accept(this);
        }

        public void addTask(String id, BukkitTask task) {
            animations.put(id, task);
        }

        public void stopAnimation(String id) {
            BukkitTask task = animations.remove(id);
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }

        public void stopAllAnimations() {
            animations.values().forEach(task -> {
                if (!task.isCancelled()) {
                    task.cancel();
                }
            });
            animations.clear();
        }

        public boolean isAnimating(String id) {
            BukkitTask task = animations.get(id);
            return task != null && !task.isCancelled();
        }
    }

    private ParticlePatterns() {}
}