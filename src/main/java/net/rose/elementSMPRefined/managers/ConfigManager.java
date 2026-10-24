package net.rose.elementSMPRefined.managers;

import net.kyori.adventure.bossbar.BossBar;
import net.rose.elementSMPRefined.core.Constants;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.logging.Level;

/**
 * Typed access to config.yml. Every getter goes through {@link #getIntSafe}
 * or {@link #getBooleanSafe} so a malformed config.yml value falls back to
 * a sane default and logs a warning instead of taking the plugin down.
 */
public class ConfigManager {

    // Defaults - named so they're not scattered as unexplained numbers
    // through every getter below.
    private static final boolean DEFAULT_ADVANCED_REROLLER_RECIPE_ENABLED = true;
    private static final boolean DEFAULT_NETHER_DISABLED = Constants.Dimension.DEFAULT_NETHER_DISABLED;
    private static final boolean DEFAULT_END_DISABLED = Constants.Dimension.DEFAULT_END_DISABLED;
    private static final int DEFAULT_GRACE_PERIOD_DURATION_SECONDS = Constants.GracePeriod.DEFAULT_DURATION_SECONDS;
    private static final int DEFAULT_GRACE_PERIOD_HUNGER_PROTECTION_SECONDS = Constants.GracePeriod.DEFAULT_HUNGER_PROTECTION_SECONDS;
    private static final boolean DEFAULT_GRACE_PERIOD_AUTO_START = Constants.GracePeriod.DEFAULT_AUTO_START;

    private final JavaPlugin plugin;
    private FileConfiguration config;

    // The config.yml bundled inside the plugin jar, kept as the single source of
    // truth for "reset to default" - loaded once and reused rather than a second
    // hardcoded copy of every default drifting out of sync with config.yml.
    private FileConfiguration defaultConfig;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        try {
            loadFromDisk();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load plugin configuration", e);
            throw new RuntimeException("Could not load plugin configuration", e);
        }
    }

    private void loadFromDisk() {
        this.config = plugin.getConfig();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public void reload() {
        try {
            plugin.reloadConfig();
            loadFromDisk();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to reload configuration", e);
        }
    }

    /**
     * The shipped defaults from the jar's bundled config.yml, lazily loaded once.
     * Independent of the live on-disk config, so editing/resetting the live file
     * never affects what "default" means.
     */
    private FileConfiguration getDefaultConfig() {
        if (defaultConfig == null) {
            try (InputStream stream = plugin.getResource("config.yml")) {
                if (stream != null) {
                    defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
                } else {
                    defaultConfig = new YamlConfiguration();
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Could not load bundled default config.yml", e);
                defaultConfig = new YamlConfiguration();
            }
        }
        return defaultConfig;
    }

    /** The shipped default value for a dotted config path, or null if the path has no default. */
    public Object getDefaultValue(String path) {
        return getDefaultConfig().get(path);
    }

    /**
     * Resets a single dotted config path back to its shipped default and persists it.
     * Returns false (no-op) if the path has no default to reset to.
     */
    public boolean resetToDefault(String path) {
        FileConfiguration defaults = getDefaultConfig();
        if (!defaults.contains(path)) {
            return false;
        }
        config.set(path, defaults.get(path));
        plugin.saveConfig();
        reload();
        return true;
    }

    /** Resets the entire live config.yml back to the shipped defaults, in place. */
    public void resetAllToDefault() {
        FileConfiguration defaults = getDefaultConfig();
        Set<String> existingKeys = config.getKeys(true);
        for (String key : existingKeys) {
            if (config.isConfigurationSection(key)) continue;
            config.set(key, null);
        }
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) continue;
            config.set(key, defaults.get(key));
        }
        plugin.saveConfig();
        reload();
    }

    private int getIntSafe(String path, int fallback) {
        try {
            return config.getInt(path, fallback);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error reading " + path + " from config, using default value " + fallback, e);
            return fallback;
        }
    }

    private double getDoubleSafe(String path, double fallback) {
        try {
            return config.getDouble(path, fallback);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error reading " + path + " from config, using default value " + fallback, e);
            return fallback;
        }
    }

    private <E extends Enum<E>> E getEnumSafe(String path, Class<E> type, String fallback) {
        String raw = config.getString(path, fallback);
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Invalid value '" + raw + "' for " + path + " in config, using default value " + fallback);
            return Enum.valueOf(type, fallback);
        }
    }

    /** Current value at a dotted path (falling back to the shipped default), for display in command output. */
    public Object getValue(String path) {
        Object value = config.get(path);
        return value != null ? value : getDefaultValue(path);
    }

    /** Sets any dotted config path and persists it immediately. */
    public void setValue(String path, Object value) {
        config.set(path, value);
        plugin.saveConfig();
    }

    private boolean getBooleanSafe(String path, boolean fallback) {
        try {
            return config.getBoolean(path, fallback);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error reading " + path + " from config, using default value " + fallback, e);
            return fallback;
        }
    }

    public boolean isAdvancedRerollerRecipeEnabled() {
        return getBooleanSafe("recipes.advanced_reroller_enabled", DEFAULT_ADVANCED_REROLLER_RECIPE_ENABLED);
    }

    public void setAdvancedRerollerRecipeEnabled(boolean enabled) {
        config.set("recipes.advanced_reroller_enabled", enabled);
        plugin.saveConfig();
    }

    // Dimension settings - each dimension can be individually enabled/disabled,
    // and it's persisted, so this survives restarts/toggling via command.
    public boolean isNetherDisabled() {
        return getBooleanSafe("dimensions.nether_disabled", DEFAULT_NETHER_DISABLED);
    }

    public boolean isEndDisabled() {
        return getBooleanSafe("dimensions.end_disabled", DEFAULT_END_DISABLED);
    }

    public void setNetherDisabled(boolean disabled) {
        config.set("dimensions.nether_disabled", disabled);
        plugin.saveConfig();
    }

    public void setEndDisabled(boolean disabled) {
        config.set("dimensions.end_disabled", disabled);
        plugin.saveConfig();
    }

    // Grace period settings - these are just the defaults/config-driven
    // fallback; the grace period itself is started/stopped via the /grace
    // command (see GraceCommand), not automatically, unless auto_start is on.
    public boolean isGracePeriodAutoStart() {
        return getBooleanSafe("grace_period.auto_start", DEFAULT_GRACE_PERIOD_AUTO_START);
    }

    /** Default total length of the grace period, in seconds, used when /grace start is run with no argument. */
    public int getGracePeriodDurationSeconds() {
        return getIntSafe("grace_period.duration_seconds", DEFAULT_GRACE_PERIOD_DURATION_SECONDS);
    }

    /**
     * Default hunger-protection window, in seconds, used when /grace start is run with no argument.
     * Clamped to the total duration so a misconfigured value can't outlast the grace period itself.
     */
    public int getGracePeriodHungerProtectionSeconds() {
        int hungerSeconds = getIntSafe("grace_period.hunger_protection_seconds", DEFAULT_GRACE_PERIOD_HUNGER_PROTECTION_SECONDS);
        return Math.min(hungerSeconds, getGracePeriodDurationSeconds());
    }

    // Extra dimension options - how the block behaves, not just whether it's on.
    public boolean isDimensionWarnPlayers() {
        return getBooleanSafe("dimensions.warn_players", Constants.Dimension.DEFAULT_WARN_PLAYERS);
    }

    public int getDimensionWarningCooldownSeconds() {
        return Math.max(0, getIntSafe("dimensions.warning_cooldown_seconds", Constants.Dimension.DEFAULT_WARNING_COOLDOWN_SECONDS));
    }

    public boolean isDimensionPushbackEnabled() {
        return getBooleanSafe("dimensions.pushback_enabled", Constants.Dimension.DEFAULT_PUSHBACK_ENABLED);
    }

    public double getDimensionPushbackStrength() {
        return Math.max(0.0, getDoubleSafe("dimensions.pushback_strength", Constants.Dimension.PORTAL_PUSHBACK_STRENGTH));
    }

    /** Whether non-player entities (boats, pearls, items...) are also blocked from using disabled portals. */
    public boolean isDimensionBlockEntities() {
        return getBooleanSafe("dimensions.block_entities", Constants.Dimension.DEFAULT_BLOCK_ENTITIES);
    }

    /** Whether players with element.admin can still use disabled portals. */
    public boolean isDimensionAdminBypass() {
        return getBooleanSafe("dimensions.admin_bypass", Constants.Dimension.DEFAULT_ADMIN_BYPASS);
    }

    // Extra grace period options.
    public boolean isGracePeriodPvpProtection() {
        return getBooleanSafe("grace_period.pvp_protection", Constants.GracePeriod.DEFAULT_PVP_PROTECTION);
    }

    public boolean isGracePeriodHungerProtection() {
        return getBooleanSafe("grace_period.hunger_protection", Constants.GracePeriod.DEFAULT_HUNGER_PROTECTION);
    }

    public boolean isGracePeriodBroadcastStart() {
        return getBooleanSafe("grace_period.broadcast_start", Constants.GracePeriod.DEFAULT_BROADCAST_START);
    }

    public boolean isGracePeriodBroadcastEnd() {
        return getBooleanSafe("grace_period.broadcast_end", Constants.GracePeriod.DEFAULT_BROADCAST_END);
    }

    public boolean isGracePeriodBossBarEnabled() {
        return getBooleanSafe("grace_period.boss_bar.enabled", Constants.GracePeriod.DEFAULT_BOSS_BAR_ENABLED);
    }

    public BossBar.Color getGracePeriodBossBarColor() {
        return getEnumSafe("grace_period.boss_bar.color", BossBar.Color.class, Constants.GracePeriod.DEFAULT_BOSS_BAR_COLOR);
    }

    public BossBar.Overlay getGracePeriodBossBarStyle() {
        return getEnumSafe("grace_period.boss_bar.style", BossBar.Overlay.class, Constants.GracePeriod.DEFAULT_BOSS_BAR_STYLE);
    }

    /** Whether the Nether is automatically re-enabled when the grace period runs out on its own. */
    public boolean isGracePeriodEnableNetherOnEnd() {
        return getBooleanSafe("grace_period.on_end.enable_nether", Constants.GracePeriod.DEFAULT_ENABLE_NETHER_ON_END);
    }

    /** Whether the End is automatically re-enabled when the grace period runs out on its own. */
    public boolean isGracePeriodEnableEndOnEnd() {
        return getBooleanSafe("grace_period.on_end.enable_end", Constants.GracePeriod.DEFAULT_ENABLE_END_ON_END);
    }
}