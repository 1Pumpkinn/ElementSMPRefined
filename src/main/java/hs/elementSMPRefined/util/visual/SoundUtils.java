package hs.elementSMPRefined.util.visual;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Enhanced sound utilities with more categories and basic sound management.
 * Note: Advanced sound effects requiring scheduling should use TaskScheduler.
 */
public final class SoundUtils {

    /**
     * Sound configuration with volume and pitch
     */
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

        public static SoundConfig of(Sound sound) {
            return new SoundConfig(sound, 1.0f, 1.0f);
        }

        public SoundConfig withVolume(float volume) {
            return new SoundConfig(sound, volume, pitch);
        }

        public SoundConfig withPitch(float pitch) {
            return new SoundConfig(sound, volume, pitch);
        }

        public SoundConfig withRandomPitch() {
            float randomPitch = 0.5f + (float) Math.random() * 1.0f;
            return new SoundConfig(sound, volume, randomPitch);
        }
    }

    /**
     * Ability-related sounds
     */
    public static final class Ability {
        public static final SoundConfig ACTIVATE = SoundConfig.of(Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        public static final SoundConfig SUCCESS = SoundConfig.of(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        public static final SoundConfig FAIL = SoundConfig.of(Sound.ENTITY_VILLAGER_NO, 0.7f, 0.8f);
        public static final SoundConfig COOLDOWN = SoundConfig.of(Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
        public static final SoundConfig CHARGE = SoundConfig.of(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.8f);
        public static final SoundConfig RELEASE = SoundConfig.of(Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.0f);
        public static final SoundConfig IMPACT = SoundConfig.of(Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.0f);

        private Ability() {}
    }

    /**
     * Element-specific sounds
     */
    public static final class Element {
        public static final SoundConfig AIR = SoundConfig.of(Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.5f);
        public static final SoundConfig WATER = SoundConfig.of(Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0f, 1.2f);
        public static final SoundConfig FIRE = SoundConfig.of(Sound.ENTITY_BLAZE_SHOOT, 0.8f, 1.2f);
        public static final SoundConfig EARTH = SoundConfig.of(Sound.BLOCK_STONE_BREAK, 1.0f, 0.8f);
        public static final SoundConfig LIFE = SoundConfig.of(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
        public static final SoundConfig DEATH = SoundConfig.of(Sound.ENTITY_WITHER_SHOOT, 0.8f, 1.2f);
        public static final SoundConfig METAL = SoundConfig.of(Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.8f);
        public static final SoundConfig FROST = SoundConfig.of(Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
        public static final SoundConfig LIGHTNING = SoundConfig.of(Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 1.0f);
        public static final SoundConfig NATURE = SoundConfig.of(Sound.BLOCK_GRASS_BREAK, 0.8f, 1.0f);

        private Element() {}
    }

    /**
     * UI interaction sounds
     */
    public static final class UI {
        public static final SoundConfig CLICK = SoundConfig.of(Sound.UI_BUTTON_CLICK, 0.5f);
        public static final SoundConfig SUCCESS = SoundConfig.of(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        public static final SoundConfig ERROR = SoundConfig.of(Sound.ENTITY_VILLAGER_NO, 0.7f);
        public static final SoundConfig ROLL = SoundConfig.of(Sound.UI_TOAST_IN, 1.0f, 1.2f);
        public static final SoundConfig HOVER = SoundConfig.of(Sound.UI_BUTTON_CLICK, 0.3f, 1.0f);
        public static final SoundConfig OPEN = SoundConfig.of(Sound.BLOCK_CHEST_OPEN, 0.6f, 1.0f);
        public static final SoundConfig CLOSE = SoundConfig.of(Sound.BLOCK_CHEST_CLOSE, 0.6f, 1.0f);
        public static final SoundConfig SELECT = SoundConfig.of(Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);

        private UI() {}
    }

    /**
     * Combat sounds
     */
    public static final class Combat {
        public static final SoundConfig HIT = SoundConfig.of(Sound.ENTITY_PLAYER_HURT, 0.8f, 1.0f);
        public static final SoundConfig CRITICAL = SoundConfig.of(Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.0f);
        public static final SoundConfig SWEEP = SoundConfig.of(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.0f);
        public static final SoundConfig BLOCK = SoundConfig.of(Sound.ENTITY_PLAYER_HURT, 0.6f, 1.0f);
        public static final SoundConfig PARRY = SoundConfig.of(Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.8f, 1.2f);
        public static final SoundConfig DODGE = SoundConfig.of(Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.2f);

        private Combat() {}
    }

    /**
     * Ambient sounds
     */
    public static final class Ambient {
        public static final SoundConfig WIND = SoundConfig.of(Sound.AMBIENT_CAVE, 0.3f, 1.0f);
        public static final SoundConfig MAGIC = SoundConfig.of(Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.4f, 1.0f);
        public static final SoundConfig MYSTERY = SoundConfig.of(Sound.BLOCK_PORTAL_AMBIENT, 0.5f, 1.0f);
        public static final SoundConfig POWER = SoundConfig.of(Sound.BLOCK_BEACON_AMBIENT, 0.4f, 1.0f);

        private Ambient() {}
    }

    /**
     * Movement sounds
     */
    public static final class Movement {
        public static final SoundConfig TELEPORT = SoundConfig.of(Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f);
        public static final SoundConfig LEVITATE = SoundConfig.of(Sound.ENTITY_SHULKER_AMBIENT, 0.6f, 1.0f);
        public static final SoundConfig LAND = SoundConfig.of(Sound.ENTITY_HORSE_LAND, 0.8f, 1.0f);
        public static final SoundConfig STEP = SoundConfig.of(Sound.BLOCK_GRASS_STEP, 0.4f, 1.0f);

        private Movement() {}
    }

    /**
     * Notification sounds
     */
    public static final class Notification {
        public static final SoundConfig INFO = SoundConfig.of(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.0f);
        public static final SoundConfig WARNING = SoundConfig.of(Sound.ENTITY_BLAZE_AMBIENT, 0.7f, 0.8f);
        public static final SoundConfig ALERT = SoundConfig.of(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.0f);
        public static final SoundConfig ACHIEVEMENT = SoundConfig.of(Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);

        private Notification() {}
    }

    /**
     * Play sound at location
     */
    public static void playAt(Location location, SoundConfig config) {
        World world = location.getWorld();
        if (world != null) {
            world.playSound(location, config.sound(), config.volume(), config.pitch());
        }
    }

    /**
     * Play sound to specific player
     */
    public static void playTo(Player player, SoundConfig config) {
        player.playSound(player.getLocation(), config.sound(), config.volume(), config.pitch());
    }

    /**
     * Play sound to players in range
     */
    public static void playNearby(Location location, double range, SoundConfig config) {
        World world = location.getWorld();
        if (world != null) {
            world.getNearbyPlayers(location, range).forEach(p -> playTo(p, config));
        }
    }

    /**
     * Play sound to all players in world
     */
    public static void playToWorld(World world, SoundConfig config) {
        world.getPlayers().forEach(p -> playTo(p, config));
    }

    /**
     * Play sound with distance-based volume falloff
     */
    public static void playWithFalloff(Location location, SoundConfig config, double maxDistance) {
        World world = location.getWorld();
        if (world == null) return;

        world.getNearbyPlayers(location, maxDistance).forEach(player -> {
            double distance = player.getLocation().distance(location);
            float falloff = 1.0f - (float) (distance / maxDistance);
            float adjustedVolume = config.volume() * Math.max(0, falloff);

            player.playSound(player.getLocation(), config.sound(), adjustedVolume, config.pitch());
        });
    }

    /**
     * Play directional sound (left/right panning simulation)
     */
    public static void playDirectional(Player player, Location soundLocation, SoundConfig config) {
        double dx = soundLocation.getX() - player.getLocation().getX();
        double dz = soundLocation.getZ() - player.getLocation().getZ();

        // Calculate angle and adjust pitch slightly for direction
        float angle = (float) Math.atan2(dz, dx);
        float pitchVariation = (float) Math.sin(angle) * 0.1f;

        SoundConfig directionalConfig = new SoundConfig(config.sound(), config.volume(),
                Math.max(0.5f, Math.min(2.0f, config.pitch() + pitchVariation)));

        playTo(player, directionalConfig);
    }

    /**
     * Play sound with random variation
     */
    public static void playWithVariation(Player player, SoundConfig config, float volumeVariation, float pitchVariation) {
        float randomVolume = config.volume() + ((float) Math.random() - 0.5f) * volumeVariation;
        float randomPitch = config.pitch() + ((float) Math.random() - 0.5f) * pitchVariation;

        // Clamp values
        randomVolume = Math.max(0, Math.min(2, randomVolume));
        randomPitch = Math.max(0.5f, Math.min(2.0f, randomPitch));

        SoundConfig variedConfig = new SoundConfig(config.sound(), randomVolume, randomPitch);
        playTo(player, variedConfig);
    }

    private SoundUtils() {}
}