package net.rose.elementSMPRefined.core.API.element;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.CooldownManager;
import net.rose.elementSMPRefined.managers.TeamManager;

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
    private final TeamManager teamManager;
    private final ConfigManager configManager;
    private final ElementType elementType;
    private final ElementSMPRefined plugin;

    private ElementContext(Builder builder) {
        this.player = builder.player;
        this.upgradeLevel = builder.upgradeLevel;
        this.elementType = builder.elementType;
        this.cooldownManager = builder.cooldownManager;
        this.teamManager = builder.teamManager;
        this.configManager = builder.configManager;
        this.plugin = builder.plugin;
    }

    public Player getPlayer() {
        return player;
    }

    public int getUpgradeLevel() {
        return upgradeLevel;
    }

    public ElementType getElementType() {
        return elementType;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Player player;
        private int upgradeLevel;
        private ElementType elementType;
        private CooldownManager cooldownManager;
        private TeamManager teamManager;
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

        public Builder cooldownManager(CooldownManager manager) {
            this.cooldownManager = manager;
            return this;
        }

        public Builder teamManager(TeamManager manager) {
            this.teamManager = manager;
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
