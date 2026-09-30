package net.rose.elementSMPRefined.util.sound;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Sound presets and a single helper to play them to a player.
 * Only presets that are actually used live here - add new ones as needed.
 */
public final class SoundUtils {
    private SoundUtils() {}

    public record SoundConfig(Sound sound, float volume, float pitch) {
        public SoundConfig {
            if (volume < 0 || volume > 2) {
                throw new IllegalArgumentException("Volume must be between 0 and 2");
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
    }

    public static final class Element {
        public static final SoundConfig AIR = SoundConfig.of(Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.5f);
        public static final SoundConfig WATER = SoundConfig.of(Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0f, 1.2f);
        public static final SoundConfig METAL = SoundConfig.of(Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.8f);

        private Element() {}
    }

    public static final class UI {
        public static final SoundConfig CLICK = SoundConfig.of(Sound.UI_BUTTON_CLICK, 0.5f);
        public static final SoundConfig SUCCESS = SoundConfig.of(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        public static final SoundConfig ROLL = SoundConfig.of(Sound.UI_TOAST_IN, 1.0f, 1.2f);

        private UI() {}
    }

    public static final class Combat {
        public static final SoundConfig HIT = SoundConfig.of(Sound.ENTITY_PLAYER_HURT, 0.8f, 1.0f);

        private Combat() {}
    }

    public static void playTo(Player player, SoundConfig config) {
        player.playSound(player.getLocation(), config.sound(), config.volume(), config.pitch());
    }
}
