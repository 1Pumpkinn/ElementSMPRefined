package hs.elementSMPRefined.util.visual;

import hs.elementSMPRefined.ElementSMPRefined;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum ParticlePreset {
    CIRCLE("circle") {
        @Override
        public void play(Player player, ElementSMPRefined plugin) {
            play(player, plugin, PresetOptions.defaults(this));
        }
    },
    RING("ring") {
        @Override
        public void play(Player player, ElementSMPRefined plugin) {
            play(player, plugin, PresetOptions.defaults(this));
        }
    },
    SPIRAL("spiral") {
        @Override
        public void play(Player player, ElementSMPRefined plugin) {
            play(player, plugin, PresetOptions.defaults(this));
        }
    },
    HELIX("helix") {
        @Override
        public void play(Player player, ElementSMPRefined plugin) {
            play(player, plugin, PresetOptions.defaults(this));
        }
    },
    SPHERE("sphere") {
        @Override
        public void play(Player player, ElementSMPRefined plugin) {
            play(player, plugin, PresetOptions.defaults(this));
        }
    },
    BURST("burst") {
        @Override
        public void play(Player player, ElementSMPRefined plugin) {
            play(player, plugin, PresetOptions.defaults(this));
        }
    },
    WAVE("wave") {
        @Override
        public void play(Player player, ElementSMPRefined plugin) {
            play(player, plugin, PresetOptions.defaults(this));
        }
    },
    VORTEX("vortex") {
        @Override
        public void play(Player player, ElementSMPRefined plugin) {
            play(player, plugin, PresetOptions.defaults(this));
        }
    },
    LINE("line") {
        @Override
        public void play(Player player, ElementSMPRefined plugin) {
            play(player, plugin, PresetOptions.defaults(this));
        }
    },
    ORBIT("orbit") {
        @Override
        public void play(Player player, ElementSMPRefined plugin) {
            play(player, plugin, PresetOptions.defaults(this));
        }
    },
    STAR("star") {
        @Override
        public void play(Player player, ElementSMPRefined plugin) {
            play(player, plugin, PresetOptions.defaults(this));
        }
    },
    AURA("aura") {
        @Override
        public void play(Player player, ElementSMPRefined plugin) {
            play(player, plugin, PresetOptions.defaults(this));
        }
    };

    public record PresetOptions(double size, double length, double width, Particle particle, Color color) {
        public static PresetOptions defaults(ParticlePreset preset) {
            return switch (preset) {
                case CIRCLE -> new PresetOptions(1.5, 0.0, 36.0, Particle.END_ROD, null);
                case RING -> new PresetOptions(2.6, 0.0, 12.0, Particle.GLOW, null);
                case SPIRAL -> new PresetOptions(1.5, 2.2, 18.0, Particle.SOUL_FIRE_FLAME, null);
                case HELIX -> new PresetOptions(1.2, 2.8, 18.0, Particle.WAX_OFF, null);
                case SPHERE -> new PresetOptions(1.4, 0.0, 60.0, Particle.DRIPPING_LAVA, null);
                case BURST -> new PresetOptions(2.0, 0.0, 48.0, Particle.EXPLOSION, null);
                case WAVE -> new PresetOptions(1.8, 0.0, 30.0, Particle.SPLASH, null);
                case VORTEX -> new PresetOptions(1.3, 2.5, 16.0, Particle.ENCHANT, null);
                case LINE -> new PresetOptions(3.5, 3.5, 0.25, Particle.REVERSE_PORTAL, null);
                case ORBIT -> new PresetOptions(2.0, 0.8, 48.0, Particle.GLOW, null);
                case STAR -> new PresetOptions(2.5, 0.0, 60.0, Particle.FIREWORK, null);
                case AURA -> new PresetOptions(1.0, 1.4, 40.0, Particle.ENCHANT, null);
            };
        }

        public double sizeOr(double fallback) {
            return size > 0 ? size : fallback;
        }

        public double lengthOr(double fallback) {
            return length > 0 ? length : fallback;
        }

        public double widthOr(double fallback) {
            return width > 0 ? width : fallback;
        }
    }

    private final String key;

    ParticlePreset(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public abstract void play(Player player, ElementSMPRefined plugin);

    public void play(Player player, ElementSMPRefined plugin, PresetOptions options) {
        if (player == null || player.isDead()) {
            return;
        }

        Location origin = player.getLocation().clone().add(0, 1.2, 0);
        Particle particle = options.particle() != null ? options.particle() : getDefaultParticle();
        Color color = options.color();
        double size = options.sizeOr(getDefaultSize());
        double length = options.lengthOr(getDefaultLength());
        double width = options.widthOr(getDefaultWidth());

        switch (this) {
            case CIRCLE -> ParticlePatterns.spawnCircle(new ParticlePatterns.CircleConfig(
                    origin,
                    size,
                    particle,
                    Math.max(12, (int) Math.round(width)),
                    true,
                    0.15
            ));
            case RING -> ParticlePatterns.animateExpandingRing(new ParticlePatterns.ExpandingRingConfig(
                    origin,
                    Math.max(0.1, size * 0.2),
                    size,
                    Math.max(6, (int) Math.round(width)),
                    particle,
                    2L
            ), plugin);
            case SPIRAL -> ParticlePatterns.spawnSpiral(new ParticlePatterns.SpiralConfig(
                    origin,
                    size,
                    length,
                    3,
                    Math.max(8, (int) Math.round(width)),
                    particle,
                    true
            ));
            case HELIX -> ParticlePatterns.spawnHelix(new ParticlePatterns.HelixConfig(
                    origin,
                    size,
                    length,
                    3,
                    Math.max(8, (int) Math.round(width)),
                    particle,
                    true
            ));
            case SPHERE -> ParticlePatterns.spawnSphere(new ParticlePatterns.SphereConfig(
                    origin,
                    size,
                    particle,
                    Math.max(16, (int) Math.round(width)),
                    false
            ));
            case BURST -> ParticlePatterns.createBurst(origin, particle, Math.max(12, (int) Math.round(width)), size);
            case WAVE -> ParticlePatterns.createWave(origin, particle, size, Math.max(12, (int) Math.round(width)));
            case VORTEX -> ParticlePatterns.createVortex(origin, particle, size, length, Math.max(8, (int) Math.round(width)), 6);
            case LINE -> ParticlePatterns.spawnLine(new ParticlePatterns.LineConfig(
                    origin,
                    origin.clone().add(player.getLocation().getDirection().multiply(length)),
                    particle,
                    Math.max(0.1, width),
                    false
            ));
            case ORBIT -> {
                ParticlePatterns.spawnCircle(new ParticlePatterns.CircleConfig(origin, size, particle, Math.max(12, (int) Math.round(width)), true, 0.2));
                ParticlePatterns.spawnCircle(new ParticlePatterns.CircleConfig(origin.clone().add(0, length, 0), size, particle, Math.max(12, (int) Math.round(width)), true, 0.2));
            }
            case STAR -> {
                ParticlePatterns.createBurst(origin, particle, Math.max(18, (int) Math.round(width)), size);
                ParticlePatterns.spawnCircle(new ParticlePatterns.CircleConfig(origin, size * 0.5, particle, 12, true, 0.1));
            }
            case AURA -> {
                ParticlePatterns.spawnSphere(new ParticlePatterns.SphereConfig(origin, size, particle, Math.max(20, (int) Math.round(width)), true));
                ParticlePatterns.createWave(origin, particle, length, Math.max(12, (int) Math.round(width)));
            }
        }

        if (color != null && isColorParticle(particle)) {
            applyColoredFallbackEffect(origin, particle, color, size, length, width);
        }
    }

    private Particle getDefaultParticle() {
        return switch (this) {
            case CIRCLE -> Particle.END_ROD;
            case RING -> Particle.GLOW;
            case SPIRAL -> Particle.SOUL_FIRE_FLAME;
            case HELIX -> Particle.WAX_OFF;
            case SPHERE -> Particle.DRIPPING_LAVA;
            case BURST -> Particle.EXPLOSION;
            case WAVE -> Particle.SPLASH;
            case VORTEX -> Particle.ENCHANT;
            case LINE -> Particle.REVERSE_PORTAL;
            case ORBIT -> Particle.GLOW;
            case STAR -> Particle.FIREWORK;
            case AURA -> Particle.ENCHANT;
        };
    }

    private double getDefaultSize() {
        return switch (this) {
            case CIRCLE -> 1.5;
            case RING -> 2.6;
            case SPIRAL -> 1.5;
            case HELIX -> 1.2;
            case SPHERE -> 1.4;
            case BURST -> 2.0;
            case WAVE -> 1.8;
            case VORTEX -> 1.3;
            case LINE -> 3.5;
            case ORBIT -> 2.0;
            case STAR -> 2.5;
            case AURA -> 1.0;
        };
    }

    private double getDefaultLength() {
        return switch (this) {
            case CIRCLE -> 0.0;
            case RING -> 0.0;
            case SPIRAL -> 2.2;
            case HELIX -> 2.8;
            case SPHERE -> 0.0;
            case BURST -> 0.0;
            case WAVE -> 0.0;
            case VORTEX -> 2.5;
            case LINE -> 3.5;
            case ORBIT -> 0.8;
            case STAR -> 0.0;
            case AURA -> 1.4;
        };
    }

    private double getDefaultWidth() {
        return switch (this) {
            case CIRCLE -> 36.0;
            case RING -> 12.0;
            case SPIRAL -> 18.0;
            case HELIX -> 18.0;
            case SPHERE -> 60.0;
            case BURST -> 48.0;
            case WAVE -> 30.0;
            case VORTEX -> 16.0;
            case LINE -> 0.25;
            case ORBIT -> 48.0;
            case STAR -> 60.0;
            case AURA -> 40.0;
        };
    }

    private boolean isColorParticle(Particle particle) {
        return particle == Particle.DUST;
    }

    private void applyColoredFallbackEffect(Location origin, Particle particle, Color color, double size, double length, double width) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }

        int points = Math.max(12, (int) Math.round(width));
        for (int i = 0; i < points; i++) {
            double angle = (i / (double) points) * Math.PI * 2;
            double x = Math.cos(angle) * size;
            double z = Math.sin(angle) * size;
            world.spawnParticle(particle, origin.clone().add(x, 0.0, z), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(color, 1.0F), true);
        }
    }

    public static Optional<ParticlePreset> fromName(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        String normalized = input.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(preset -> preset.key.equalsIgnoreCase(normalized))
                .findFirst();
    }

    public static List<String> getNames() {
        return Arrays.stream(values())
                .map(ParticlePreset::getKey)
                .toList();
    }

    public static List<String> filterNames(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return getNames();
        }

        String normalized = prefix.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .map(ParticlePreset::getKey)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(normalized))
                .toList();
    }
}
