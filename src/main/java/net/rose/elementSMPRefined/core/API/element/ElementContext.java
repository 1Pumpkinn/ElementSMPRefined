package net.rose.elementSMPRefined.core.API.element;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.CooldownManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Context object that encapsulates all managers required for element abilities.
 * Uses builder pattern for flexible construction.
 */
public class ElementContext {
    private final Player player;
    private final int upgradeLevel;
    private final CooldownManager cooldownManager;
    private final TrustManager trustManager;
    private final ConfigManager configManager;
    private final ElementType elementType;
    private final ElementId elementId;
    private final ElementSMPRefined plugin;

    private ElementContext(Builder builder) {
        this.player = builder.player;
        this.upgradeLevel = builder.upgradeLevel;
        this.elementType = builder.elementType;
        this.elementId = builder.elementId != null
                ? builder.elementId
                : builder.elementType == null ? null : ElementId.builtin(builder.elementType);
        this.cooldownManager = builder.cooldownManager;
        this.trustManager = builder.trustManager;
        this.configManager = builder.configManager;
        this.plugin = builder.plugin;
    }

    // Getters
    public Player getPlayer() { return player; }
    public int getUpgradeLevel() { return upgradeLevel; }
    public ElementType getElementType() { return elementType; }
    public ElementId getElementId() { return elementId; }
    public CooldownManager getCooldownManager() { return cooldownManager; }
    public TrustManager getTrustManager() { return trustManager; }
    public ConfigManager getConfigManager() { return configManager; }
    public ElementSMPRefined getPlugin() { return plugin; }

    // Builder
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Player player;
        private int upgradeLevel;
        private ElementType elementType;
        private ElementId elementId;
        private CooldownManager cooldownManager;
        private TrustManager trustManager;
        private ConfigManager configManager;
        private ElementSMPRefined plugin;

        public Builder player(Player player) {
            this.player = player;
            return this;
        }

        public Builder upgradeLevel(int level) {
            this.upgradeLevel = level;
            return this;
        }

        public Builder elementType(ElementType type) {
            this.elementType = type;
            return this;
        }

        public Builder elementId(ElementId id) {
            this.elementId = id;
            return this;
        }

        public Builder cooldownManager(CooldownManager manager) {
            this.cooldownManager = manager;
            return this;
        }

        public Builder trustManager(TrustManager manager) {
            this.trustManager = manager;
            return this;
        }

        public Builder configManager(ConfigManager manager) {
            this.configManager = manager;
            return this;
        }

        public Builder plugin(JavaPlugin plugin) {
            this.plugin = (ElementSMPRefined) plugin;
            return this;
        }

        public ElementContext build() {
            return new ElementContext(this);
        }
    }
}