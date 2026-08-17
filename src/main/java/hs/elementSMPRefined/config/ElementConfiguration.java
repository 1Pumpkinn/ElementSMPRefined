package hs.elementSMPRefined.config;

import hs.elementSMPRefined.API.element.ElementType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

/**
 * Data-driven configuration for elements. Loads element settings from config files.
 */
public class ElementConfiguration {

    // Kept in sync with the defaults in config.yml and ConfigManager.
    private static final int DEFAULT_ABILITY1_COST = 50;
    private static final int DEFAULT_ABILITY2_COST = 75;

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

    /**
     * Updates a single field on {@code type}'s in-memory config, leaving every
     * other field as-is. If {@code type} has no config yet, one is created from
     * defaults first. An unrecognized {@code key} or a value of the wrong type
     * is a no-op - it does not create or touch any entry.
     */
    public void setConfigValue(ElementType type, String key, Object value) {
        ElementConfig existing = configs.getOrDefault(type,
                new ElementConfig("Unknown", "No description", "WHITE", true, false,
                        DEFAULT_ABILITY1_COST, DEFAULT_ABILITY2_COST));

        String displayName = existing.displayName;
        String description = existing.description;
        String color = existing.color;
        boolean enabled = existing.enabled;
        boolean isBasic = existing.isBasic;
        int ability1Cost = existing.ability1Cost;
        int ability2Cost = existing.ability2Cost;

        switch (key.toLowerCase()) {
            case "display_name" -> {
                if (!(value instanceof String s)) return;
                displayName = s;
            }
            case "description" -> {
                if (!(value instanceof String s)) return;
                description = s;
            }
            case "color" -> {
                if (!(value instanceof String s)) return;
                color = s;
            }
            case "enabled" -> {
                if (!(value instanceof Boolean b)) return;
                enabled = b;
            }
            case "is_basic" -> {
                if (!(value instanceof Boolean b)) return;
                isBasic = b;
            }
            case "ability1_cost" -> {
                if (!(value instanceof Integer i)) return;
                ability1Cost = i;
            }
            case "ability2_cost" -> {
                if (!(value instanceof Integer i)) return;
                ability2Cost = i;
            }
            default -> {
                return; // unknown key - don't create/touch an entry over it
            }
        }

        configs.put(type, new ElementConfig(displayName, description, color, enabled, isBasic, ability1Cost, ability2Cost));
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
            this.ability1Cost = section.getInt("ability1_cost", DEFAULT_ABILITY1_COST);
            this.ability2Cost = section.getInt("ability2_cost", DEFAULT_ABILITY2_COST);
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