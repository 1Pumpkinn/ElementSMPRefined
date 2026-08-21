package hs.elementSMPRefined.managers;

import hs.elementSMPRefined.API.element.ElementId;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.config.ElementConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Typed access to config.yml. Every getter goes through {@link #getIntSafe}
 * or {@link #getBooleanSafe} so a malformed config.yml value falls back to
 * a sane default and logs a warning instead of taking the plugin down.
 */
public class ConfigManager {

    // Defaults - named so they're not scattered as unexplained numbers
    // through every getter below.
    private static final int DEFAULT_MAX_MANA = 100;
    private static final int DEFAULT_MANA_REGEN_PER_SECOND = 1;
    private static final int DEFAULT_ABILITY_1_COST = 50;
    private static final int DEFAULT_ABILITY_2_COST = 75;
    private static final boolean DEFAULT_STATUS_EFFECTS_ENABLED = true;
    private static final boolean DEFAULT_STATUS_EFFECT_DAMAGE_ENABLED = true;
    private static final boolean DEFAULT_STATUS_EFFECT_NOTIFICATIONS_ENABLED = true;
    private static final boolean DEFAULT_ADVANCED_REROLLER_RECIPE_ENABLED = true;

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private ElementConfiguration elementConfiguration;

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

    // Mana settings
    public int getMaxMana() {
        return getIntSafe("mana.max", DEFAULT_MAX_MANA);
    }

    public int getManaRegenPerSecond() {
        return getIntSafe("mana.regen_per_second", DEFAULT_MANA_REGEN_PER_SECOND);
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

    // Ability costs (data-driven approach)
    public int getAbility1Cost(ElementType type) {
        if (elementConfiguration.hasConfig(type)) {
            return elementConfiguration.getConfig(type).getAbility1Cost();
        }
        return DEFAULT_ABILITY_1_COST;
    }

    public int getAbility2Cost(ElementType type) {
        if (elementConfiguration.hasConfig(type)) {
            return elementConfiguration.getConfig(type).getAbility2Cost();
        }
        return DEFAULT_ABILITY_2_COST;
    }

    /**
     * ElementId-aware ability cost lookup, for addon elements as well as builtins.
     * Builtin IDs (namespace "elements") defer to the per-type config section as
     * before; addon elements have no per-type config section yet, so they fall
     * back to the same defaults everyone else gets when unconfigured.
     */
    public int getAbility1Cost(ElementId id) {
        ElementType type = id == null ? null : id.toBuiltinType();
        return type != null ? getAbility1Cost(type) : DEFAULT_ABILITY_1_COST;
    }

    public int getAbility2Cost(ElementId id) {
        ElementType type = id == null ? null : id.toBuiltinType();
        return type != null ? getAbility2Cost(type) : DEFAULT_ABILITY_2_COST;
    }

    public boolean isAdvancedRerollerRecipeEnabled() {
        return getBooleanSafe("recipes.advanced_reroller_enabled", DEFAULT_ADVANCED_REROLLER_RECIPE_ENABLED);
    }

    public void setAdvancedRerollerRecipeEnabled(boolean enabled) {
        config.set("recipes.advanced_reroller_enabled", enabled);
        plugin.saveConfig();
    }
}