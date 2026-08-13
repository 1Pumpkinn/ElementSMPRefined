package hs.elementSMPRefined.managers;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.config.ElementConfiguration;
import hs.elementSMPRefined.elements.ElementType;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.logging.Level;

public class ConfigManager {
    private final ElementSMPRefined plugin;
    private FileConfiguration config;
    private ElementConfiguration elementConfiguration;

    public ConfigManager(ElementSMPRefined plugin) {
        this.plugin = plugin;
        try {
            this.config = plugin.getConfig();
            this.elementConfiguration = new ElementConfiguration(config.getConfigurationSection("elements"));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load plugin configuration", e);
            throw new RuntimeException("Could not load plugin configuration", e);
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public void reload() {
        try {
            plugin.reloadConfig();
            this.config = plugin.getConfig();
            this.elementConfiguration = new ElementConfiguration(config.getConfigurationSection("elements"));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to reload configuration", e);
        }
    }

    // Mana settings
    public int getMaxMana() {
        try {
            return config.getInt("mana.max", 100);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error reading mana.max from config, using default value 100", e);
            return 100;
        }
    }

    public int getManaRegenPerSecond() {
        try {
            return config.getInt("mana.regen_per_second", 1);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error reading mana.regen_per_second from config, using default value 1", e);
            return 1;
        }
    }

    // Status effect settings
    public boolean areStatusEffectsEnabled() {
        try {
            return config.getBoolean("status_effects.enabled", true);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error reading status_effects.enabled from config, using default value true", e);
            return true;
        }
    }

    public boolean isStatusEffectDamageEnabled() {
        try {
            return config.getBoolean("status_effects.damage_per_tick", true);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error reading status_effects.damage_per_tick from config, using default value true", e);
            return true;
        }
    }

    public boolean areStatusEffectNotificationsEnabled() {
        try {
            return config.getBoolean("status_effects.notification_messages", true);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error reading status_effects.notification_messages from config, using default value true", e);
            return true;
        }
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
        return 50;
    }

    public int getAbility2Cost(ElementType type) {
        if (elementConfiguration.hasConfig(type)) {
            return elementConfiguration.getConfig(type).getAbility2Cost();
        }
        return 75;
    }

    public boolean isAdvancedRerollerRecipeEnabled() {
        try {
            return config.getBoolean("recipes.advanced_reroller_enabled", true);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error reading advanced_reroller_enabled from config, using default value true", e);
            return true;
        }
    }

    public void setAdvancedRerollerRecipeEnabled(boolean enabled) {
        config.set("recipes.advanced_reroller_enabled", enabled);
        plugin.saveConfig();
    }
}