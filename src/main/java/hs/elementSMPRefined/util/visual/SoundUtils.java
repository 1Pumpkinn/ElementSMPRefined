package hs.elementSMPRefined.util.visual;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class SoundUtils {
    public record SoundConfig(Sound sound, float volume, float pitch) {
        public SoundConfig {
            if (volume < 0 || volume > 1) {
                throw new IllegalArgumentException("Volume must be between 0 and 1");
            }
            if (pitch < 0.5 || pitch > 2.0) {
                throw new IllegalArgumentException("Pitch must be between 0.5 and 2.0");
            }
        }

        public static SoundConfig of(Sound sound, float volume, float pitch) {
            return new SoundConfig(sound, volume, pitch);
        }

        public static SoundConfig of(Sound sound, float volume) {
            return new SoundConfig(sound, volume, 1.0f);
        }

        public SoundConfig withVolume(float volume) {
            return new SoundConfig(sound, volume, pitch);
        }

        public SoundConfig withPitch(float pitch) {
            return new SoundConfig(sound, volume, pitch);
        }
    }

    public static final class Ability {
        public static final SoundConfig ACTIVATE = SoundConfig.of(Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        public static final SoundConfig SUCCESS = SoundConfig.of(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        public static final SoundConfig FAIL = SoundConfig.of(Sound.ENTITY_VILLAGER_NO, 0.7f, 0.8f);
        public static final SoundConfig COOLDOWN = SoundConfig.of(Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);

        private Ability() {}
    }

    public static final class Element {
        public static final SoundConfig AIR = SoundConfig.of(Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.5f);
        public static final SoundConfig WATER = SoundConfig.of(Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0f, 1.2f);
        public static final SoundConfig FIRE = SoundConfig.of(Sound.ENTITY_BLAZE_SHOOT, 0.8f, 1.2f);
        public static final SoundConfig EARTH = SoundConfig.of(Sound.BLOCK_STONE_BREAK, 1.0f, 0.8f);
        public static final SoundConfig LIFE = SoundConfig.of(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
        public static final SoundConfig DEATH = SoundConfig.of(Sound.ENTITY_WITHER_SHOOT, 0.8f, 1.2f);
        public static final SoundConfig METAL = SoundConfig.of(Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.8f);
        public static final SoundConfig FROST = SoundConfig.of(Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);

        private Element() {}
    }

    public static final class UI {
        public static final SoundConfig CLICK = SoundConfig.of(Sound.UI_BUTTON_CLICK, 0.5f);
        public static final SoundConfig SUCCESS = SoundConfig.of(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        public static final SoundConfig ERROR = SoundConfig.of(Sound.ENTITY_VILLAGER_NO, 0.7f);
        public static final SoundConfig ROLL = SoundConfig.of(Sound.UI_TOAST_IN, 1.0f, 1.2f);

        private UI() {}
    }

    public static void playAt(Location location, SoundConfig config) {
        World world = location.getWorld();
        if (world != null) {
            world.playSound(location, config.sound(), config.volume(), config.pitch());
        }
    }

    public static void playTo(Player player, SoundConfig config) {
        player.playSound(player.getLocation(), config.sound(), config.volume(), config.pitch());
    }

    public static void playNearby(Location location, double range, SoundConfig config) {
        World world = location.getWorld();
        if (world != null) {
            world.getNearbyPlayers(location, range).forEach(p -> playTo(p, config));
        }
    }

    private SoundUtils() {}
}

