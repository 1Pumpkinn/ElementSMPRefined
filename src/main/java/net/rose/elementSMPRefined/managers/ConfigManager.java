package net.rose.elementSMPRefined.managers;

import net.rose.elementSMPRefined.config.Constants;
import net.rose.elementSMPRefined.config.ElementConfiguration;
import org.bukkit.configuration.ConfigurationSection;
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
    private static final boolean DEFAULT_STATUS_EFFECTS_ENABLED = true;
    private static final boolean DEFAULT_STATUS_EFFECT_DAMAGE_ENABLED = true;
    private static final boolean DEFAULT_STATUS_EFFECT_NOTIFICATIONS_ENABLED = true;
    private static final boolean DEFAULT_ADVANCED_REROLLER_RECIPE_ENABLED = true;
    private static final boolean DEFAULT_NETHER_DISABLED = Constants.Dimension.DEFAULT_NETHER_DISABLED;
    private static final boolean DEFAULT_END_DISABLED = Constants.Dimension.DEFAULT_END_DISABLED;
    private static final int DEFAULT_GRACE_PERIOD_DURATION_SECONDS = Constants.GracePeriod.DEFAULT_DURATION_SECONDS;
    private static final int DEFAULT_GRACE_PERIOD_HUNGER_PROTECTION_SECONDS = Constants.GracePeriod.DEFAULT_HUNGER_PROTECTION_SECONDS;
    private static final boolean DEFAULT_GRACE_PERIOD_AUTO_START = Constants.GracePeriod.DEFAULT_AUTO_START;

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private ElementConfiguration elementConfiguration;

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
        this.elementConfiguration = new ElementConfiguration(config.getConfigurationSection("elements"));
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

    /**
     * Resets an entire config section (e.g. "elements.fire") back to its shipped
     * defaults, wiping any extra keys that had been added under it too.
     * Returns false (no-op) if the section has no shipped default.
     */
    public boolean resetSectionToDefault(String path) {
        FileConfiguration defaults = getDefaultConfig();
        ConfigurationSection defaultSection = defaults.getConfigurationSection(path);
        if (defaultSection == null) {
            return false;
        }
        config.set(path, null);
        for (String key : defaultSection.getKeys(true)) {
            if (defaultSection.isConfigurationSection(key)) continue;
            config.set(path + "." + key, defaultSection.get(key));
        }
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

    private boolean getBooleanSafe(String path, boolean fallback) {
        try {
            return config.getBoolean(path, fallback);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error reading " + path + " from config, using default value " + fallback, e);
            return fallback;
        }
    }

    // Status effect settings
    public boolean areStatusEffectsEnabled() {
        return getBooleanSafe("status_effects.enabled", DEFAULT_STATUS_EFFECTS_ENABLED);
    }

    public boolean isStatusEffectDamageEnabled() {
        return getBooleanSafe("status_effects.damage_per_tick", DEFAULT_STATUS_EFFECT_DAMAGE_ENABLED);
    }

    public boolean areStatusEffectNotificationsEnabled() {
        return getBooleanSafe("status_effects.notification_messages", DEFAULT_STATUS_EFFECT_NOTIFICATIONS_ENABLED);
    }

    // Element configuration
    public ElementConfiguration getElementConfiguration() {
        return elementConfiguration;
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
}