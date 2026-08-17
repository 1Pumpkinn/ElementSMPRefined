package hs.elementSMPRefined.util.visual;

import hs.elementSMPRefined.ElementSMPRefined;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum ParticlePreset {
    CIRCLE("circle") {
        @Override
        public void play(Player player, ElementSMPRefined plugin) {
            Location origin = player.getLocation().clone().add(0, 1.2, 0);
            ParticlePatterns.spawnCircle(new ParticlePatterns.CircleConfig(
                    origin,
                    1.5,
                    Particle.END_ROD,
                    36,
                    true,
                    0.15
            ));
        }
    },
    RING("ring") {
        @Override
        public void play(Player player, ElementSMPRefined plugin) {
            Location origin = player.getLocation().clone().add(0, 1.2, 0);
            ParticlePatterns.animateExpandingRing(new ParticlePatterns.ExpandingRingConfig(
                    origin,
                    0.5,
                    2.6,
                    12,
                    Particle.GLOW,
                    2L
            ), plugin);
        }
    },
    SPIRAL("spiral") {
        @Override
        public void play(Player player, ElementSMPRefined plugin) {
            Location origin = player.getLocation().clone().add(0, 1.2, 0);
            ParticlePatterns.spawnSpiral(new ParticlePatterns.SpiralConfig(
                    origin,
                    1.5,
                    2.2,
                    3,
                    18,
                    Particle.SOUL_FIRE_FLAME,
                    true
            ));
        }
    },
    HELIX("helix") {
        @Override
        public void play(Player player, ElementSMPRefined plugin) {
            Location origin = player.getLocation().clone().add(0, 1.2, 0);
            ParticlePatterns.spawnHelix(new ParticlePatterns.HelixConfig(
                    origin,
                    1.2,
                    2.8,
                    3,
                    18,
                    Particle.WAX_OFF,
                    true
            ));
        }
    },
    SPHERE("sphere") {
        @Override
        public void play(Player player, ElementSMPRefined plugin) {
            Location origin = player.getLocation().clone().add(0, 1.2, 0);
            ParticlePatterns.spawnSphere(new ParticlePatterns.SphereConfig(
                    origin,
                    1.4,
                    Particle.DRIPPING_LAVA,
                    60,
                    false
            ));
        }
    },
    BURST("burst") {
        @Override
        public void play(Player player, ElementSMPRefined plugin) {
            Location origin = player.getLocation().clone().add(0, 1.2, 0);
            ParticlePatterns.createBurst(origin, Particle.EXPLOSION, 48, 2.0);
        }
    },
    WAVE("wave") {
        @Override
        public void play(Player player, ElementSMPRefined plugin) {
            Location origin = player.getLocation().clone().add(0, 1.2, 0);
            ParticlePatterns.createWave(origin, Particle.SPLASH, 1.8, 30);
        }
    },
    VORTEX("vortex") {
        @Override
        public void play(Player player, ElementSMPRefined plugin) {
            Location origin = player.getLocation().clone().add(0, 1.2, 0);
            ParticlePatterns.createVortex(origin, Particle.ENCHANT, 1.3, 2.5, 16, 6);
        }
    },
    LINE("line") {
        @Override
        public void play(Player player, ElementSMPRefined plugin) {
            Location origin = player.getLocation().clone().add(0, 1.2, 0);
            ParticlePatterns.spawnLine(new ParticlePatterns.LineConfig(
                    origin,
                    origin.clone().add(player.getLocation().getDirection().multiply(3.5)),
                    Particle.REVERSE_PORTAL,
                    0.25,
                    false
            ));
        }
    },
    ORBIT("orbit") {
        @Override
        public void play(Player player, ElementSMPRefined plugin) {
            Location origin = player.getLocation().clone().add(0, 1.2, 0);
            ParticlePatterns.spawnCircle(new ParticlePatterns.CircleConfig(
                    origin,
                    2.0,
                    Particle.GLOW,
                    48,
                    true,
                    0.2
            ));
            ParticlePatterns.spawnCircle(new ParticlePatterns.CircleConfig(
                    origin.clone().add(0, 0.8, 0),
                    2.0,
                    Particle.END_ROD,
                    48,
                    true,
                    0.2
            ));
        }
    },
    STAR("star") {
        @Override
        public void play(Player player, ElementSMPRefined plugin) {
            Location origin = player.getLocation().clone().add(0, 1.2, 0);
            ParticlePatterns.createBurst(origin, Particle.FIREWORK, 60, 2.5);
            ParticlePatterns.spawnCircle(new ParticlePatterns.CircleConfig(
                    origin,
                    1.3,
                    Particle.FIREWORK,
                    12,
                    true,
                    0.1
            ));
        }
    },
    AURA("aura") {
        @Override
        public void play(Player player, ElementSMPRefined plugin) {
            Location origin = player.getLocation().clone().add(0, 1.2, 0);
            ParticlePatterns.spawnSphere(new ParticlePatterns.SphereConfig(
                    origin,
                    1.0,
                    Particle.ENCHANT,
                    40,
                    true
            ));
            ParticlePatterns.createWave(origin, Particle.ENCHANT, 1.4, 22);
        }
    };

    private final String key;

    ParticlePreset(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public abstract void play(Player player, ElementSMPRefined plugin);

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
