package hs.elementSMPRefined.util.visual;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

public final class ParticlePatterns {
    public record CircleConfig(
            Location center,
            double radius,
            Particle particle,
            int points,
            boolean raiseAboveGround
    ) {
        public CircleConfig {
            if (radius <= 0) throw new IllegalArgumentException("Radius must be positive");
            if (points <= 0) throw new IllegalArgumentException("Points must be positive");
        }

        public static CircleConfig of(Location center, double radius, Particle particle) {
            return new CircleConfig(center, radius, particle, 36, true);
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

        public static LineConfig of(Location start, Location end, Particle particle) {
            return new LineConfig(start, end, particle, 0.5);
        }
    }

    public record ExpandingRingConfig(
            Location center,
            double startRadius,
            double endRadius,
            int steps,
            Particle particle
    ) {
        public ExpandingRingConfig {
            if (startRadius < 0 || endRadius < 0) {
                throw new IllegalArgumentException("Radii must be non-negative");
            }
            if (steps <= 0) throw new IllegalArgumentException("Steps must be positive");
        }
    }

    public static void spawnCircle(CircleConfig config) {
        World world = config.center().getWorld();
        if (world == null) return;

        double angleStep = 360.0 / config.points();

        for (int i = 0; i < 360; i += angleStep) {
            double rad = Math.toRadians(i);
            double x = Math.cos(rad) * config.radius();
            double z = Math.sin(rad) * config.radius();

            Location particleLoc = config.center().clone().add(x, 0.5, z);

            if (config.raiseAboveGround()) {
                ensureAboveGround(particleLoc);
            }

            world.spawnParticle(config.particle(), particleLoc, 1,
                    0.1, 0.1, 0.1, 0, null, true);
        }
    }

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

    public static void spawnExpandingRing(ExpandingRingConfig config) {
        double radiusIncrement = (config.endRadius() - config.startRadius()) / config.steps();

        for (int step = 0; step < config.steps(); step++) {
            double currentRadius = config.startRadius() + (step * radiusIncrement);

            CircleConfig circleConfig = new CircleConfig(
                    config.center(),
                    currentRadius,
                    config.particle(),
                    36,
                    true
            );

            spawnCircle(circleConfig);
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

