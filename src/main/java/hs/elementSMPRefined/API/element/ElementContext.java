package hs.elementSMPRefined.API.element;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.managers.ConfigManager;
import hs.elementSMPRefined.managers.ManaManager;
import hs.elementSMPRefined.managers.TrustManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Context object that encapsulates all managers required for element abilities.
 * Uses builder pattern for flexible construction.
 */
public class ElementContext {
    private final Player player;
    private final int upgradeLevel;
    private final ManaManager manaManager;
    private final TrustManager trustManager;
    private final ConfigManager configManager;
    private final ElementType elementType;
    private final ElementSMPRefined plugin;

    private ElementContext(Builder builder) {
        this.player = builder.player;
        this.upgradeLevel = builder.upgradeLevel;
        this.elementType = builder.elementType;
        this.manaManager = builder.manaManager;
        this.trustManager = builder.trustManager;
        this.configManager = builder.configManager;
        this.plugin = builder.plugin;
    }

    // Getters
    public Player getPlayer() { return player; }
    public int getUpgradeLevel() { return upgradeLevel; }
    public ElementType getElementType() { return elementType; }
    public ManaManager getManaManager() { return manaManager; }
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
        private ManaManager manaManager;
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

        public Builder manaManager(ManaManager manager) {
            this.manaManager = manager;
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