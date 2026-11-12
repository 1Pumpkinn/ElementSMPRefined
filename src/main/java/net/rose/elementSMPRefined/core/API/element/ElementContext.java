package net.rose.elementSMPRefined.core.API.element;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.CooldownManager;
import net.rose.elementSMPRefined.managers.TeamManager;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * Context object that encapsulates all managers required for element abilities.
 * Uses builder pattern for flexible construction. Immutable once built.
 */
public class ElementContext {
    private final Player player;
    private final int upgradeLevel;
    private final CooldownManager cooldownManager;
    private final TeamManager teamManager;
    private final ConfigManager configManager;
    private final ElementType elementType;
    private final ElementSMPRefined plugin;
    private final boolean stolen;

    private ElementContext(Builder builder) {
        this.player = Objects.requireNonNull(builder.player, "player");
        this.elementType = Objects.requireNonNull(builder.elementType, "elementType");
        this.upgradeLevel = builder.upgradeLevel;
        this.cooldownManager = builder.cooldownManager;
        this.teamManager = builder.teamManager;
        this.configManager = builder.configManager;
        this.plugin = builder.plugin;
        this.stolen = builder.stolen;
    }

    public Player getPlayer() {
        return player;
    }

    public int getUpgradeLevel() {
        return upgradeLevel;
    }

    /** Whether the caster has reached at least {@code level}. */
    public boolean hasUpgrade(int level) {
        return upgradeLevel >= level;
    }

    /** The element whose ability is being cast (the stolen element during Soul Siphon). */
    public ElementType getElementType() {
        return elementType;
    }

    /**
     * True when the caster is casting another player's element through Soul Siphon rather than
     * their own.
     */
    public boolean isStolen() {
        return stolen;
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

    public ElementSMPRefined getPlugin() {
        return plugin;
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
        private boolean stolen;

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

        public Builder stolen(boolean stolen) {
            this.stolen = stolen;
            return this;
        }

        /** @throws NullPointerException if the player or element type was never set */
        public ElementContext build() {
            return new ElementContext(this);
        }
    }
}
