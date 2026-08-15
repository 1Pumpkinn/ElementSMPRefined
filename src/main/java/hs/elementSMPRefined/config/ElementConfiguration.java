package hs.elementSMPRefined.config;

import hs.elementSMPRefined.API.ElementType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

/**
 * Data-driven configuration for elements. Loads element settings from config files.
 */
public class ElementConfiguration {
    private final Map<ElementType, ElementConfig> configs = new HashMap<>();

    public ElementConfiguration(ConfigurationSection config) {
        loadFromConfig(config);
    }

    private void loadFromConfig(ConfigurationSection config) {
        if (config == null) return;

        for (String key : config.getKeys(false)) {
            try {
                ElementType type = ElementType.valueOf(key.toUpperCase());
                ConfigurationSection section = config.getConfigurationSection(key);
                if (section != null) {
                    configs.put(type, new ElementConfig(section));
                }
            } catch (IllegalArgumentException e) {
                // Skip invalid element types
            }
        }
    }

    public ElementConfig getConfig(ElementType type) {
        return configs.get(type);
    }

    public boolean hasConfig(ElementType type) {
        return configs.containsKey(type);
    }

    public void setConfigValue(ElementType type, String key, Object value) {
        ElementConfig config = configs.get(type);
        if (config == null) {
            // Create new config if it doesn't exist with default values
            config = new ElementConfig("Unknown", "No description", "WHITE", true, false, 50, 100);
            configs.put(type, config);
        }
        
        // Update the specific field based on key
        switch (key.toLowerCase()) {
            case "ability1_cost" -> {
                if (value instanceof Integer) {
                    configs.put(type, new ElementConfig(config.displayName, config.description, config.color, 
                            config.enabled, config.isBasic, (Integer) value, config.ability2Cost));
                }
                break;
            }
            case "ability2_cost" -> {
                if (value instanceof Integer) {
                    configs.put(type, new ElementConfig(config.displayName, config.description, config.color, 
                            config.enabled, config.isBasic, config.ability1Cost, (Integer) value));
                }
                break;
            }
            case "is_basic" -> {
                if (value instanceof Boolean) {
                    configs.put(type, new ElementConfig(config.displayName, config.description, config.color, 
                            config.enabled, (Boolean) value, config.ability1Cost, config.ability2Cost));
                }
                break;
            }
            case "enabled" -> {
                if (value instanceof Boolean) {
                    configs.put(type, new ElementConfig(config.displayName, config.description, config.color, 
                            (Boolean) value, config.isBasic, config.ability1Cost, config.ability2Cost));
                }
                break;
            }
            case "display_name" -> {
                if (value instanceof String) {
                    configs.put(type, new ElementConfig((String) value, config.description, config.color, 
                            config.enabled, config.isBasic, config.ability1Cost, config.ability2Cost));
                }
                break;
            }
            case "description" -> {
                if (value instanceof String) {
                    configs.put(type, new ElementConfig(config.displayName, (String) value, config.color, 
                            config.enabled, config.isBasic, config.ability1Cost, config.ability2Cost));
                }
                break;
            }
            case "color" -> {
                if (value instanceof String) {
                    configs.put(type, new ElementConfig(config.displayName, config.description, (String) value, 
                            config.enabled, config.isBasic, config.ability1Cost, config.ability2Cost));
                }
                break;
            }
        }
    }

    /**
     * Configuration data for a single element
     */
    public static class ElementConfig {
        private final String displayName;
        private final String description;
        private final String color;
        private final boolean enabled;
        private final boolean isBasic;
        private final int ability1Cost;
        private final int ability2Cost;

        public ElementConfig(ConfigurationSection section) {
            this.displayName = section.getString("display_name", "Unknown");
            this.description = section.getString("description", "No description");
            this.color = section.getString("color", "WHITE");
            this.enabled = section.getBoolean("enabled", true);
            this.isBasic = section.getBoolean("is_basic", false);
            this.ability1Cost = section.getInt("ability1_cost", 50);
            this.ability2Cost = section.getInt("ability2_cost", 100);
        }

        // Constructor for creating config programmatically
        public ElementConfig(String displayName, String description, String color, boolean enabled, 
                           boolean isBasic, int ability1Cost, int ability2Cost) {
            this.displayName = displayName;
            this.description = description;
            this.color = color;
            this.enabled = enabled;
            this.isBasic = isBasic;
            this.ability1Cost = ability1Cost;
            this.ability2Cost = ability2Cost;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public String getColor() { return color; }
        public boolean isEnabled() { return enabled; }
        public boolean isBasic() { return isBasic; }
        public int getAbility1Cost() { return ability1Cost; }
        public int getAbility2Cost() { return ability2Cost; }
    }
}