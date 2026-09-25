package net.rose.elementSMPRefined.core.API.ability;

import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Base implementation of the Ability interface that handles common functionality
 */
public abstract class BaseAbility implements Ability {
    private final String abilityId;
    private final int cooldownSeconds;
    private final int requiredUpgradeLevel;
    private final ElementType elementType;     // non-null for the "live" constructor
    private final int abilitySlot;             // 1 or 2, paired with elementType
    private final ConfigManager configManager; // non-null for the "live" constructor
    private final Set<UUID> activePlayers = new HashSet<>();

    /**
     * Create a new ability with a fixed mana cost baked in at construction time.
     * Kept for addons and anything without a per-element config entry to key off
     * of. Every built-in ability should prefer the constructor below instead, so
     * its cost stays in sync with config.yml automatically.
     *
     * @param abilityId The unique identifier for this ability
     * @param cooldownSeconds The cooldown in seconds
     * @param requiredUpgradeLevel The minimum upgrade level required
     */
    public BaseAbility(String abilityId, int cooldownSeconds, int requiredUpgradeLevel) {
        this.abilityId = abilityId;
        this.cooldownSeconds = cooldownSeconds;
        this.requiredUpgradeLevel = requiredUpgradeLevel;
        this.elementType = null;
        this.abilitySlot = 0;
        this.configManager = null;
    }

    /**
     * Create a new ability whose mana cost is read live from ConfigManager instead
     * of being hardcoded here. This is the constructor every built-in ability should
     * use - change the cost once in config.yml (mana.ability1_cost/ability2_cost, or
     * a specific element's ability1_cost/ability2_cost) and it applies everywhere
     * that ability's cost is checked, with no Java edits or recompile needed.
     *
     * @param abilityId The unique identifier for this ability
     * @param elementType The element this ability belongs to
     * @param abilitySlot 1 for ability1, 2 for ability2 - determines which config cost applies
     * @param cooldownSeconds The cooldown in seconds
     * @param requiredUpgradeLevel The minimum upgrade level required
     * @param configManager Source of truth for the live mana cost lookup
     */
    public BaseAbility(String abilityId, ElementType elementType, int abilitySlot, int cooldownSeconds,
                       int requiredUpgradeLevel, ConfigManager configManager) {
        this.abilityId = abilityId;
        this.cooldownSeconds = cooldownSeconds;
        this.requiredUpgradeLevel = requiredUpgradeLevel;
        this.elementType = elementType;
        this.abilitySlot = abilitySlot;
        this.configManager = configManager;
    }


    @Override
    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    @Override
    public int getRequiredUpgradeLevel() {
        return requiredUpgradeLevel;
    }

    @Override
    public String getAbilityId() {
        return abilityId;
    }

    @Override
    public boolean isActiveFor(Player player) {
        return activePlayers.contains(player.getUniqueId());
    }

    @Override
    public void setActive(Player player, boolean active) {
        if (active) {
            activePlayers.add(player.getUniqueId());
        } else {
            activePlayers.remove(player.getUniqueId());
        }
    }

    public abstract String getName();

    public abstract String getDescription();

    /**
     * Helper method to check if a target is valid for an ability.
     * Override in subclasses for more complex logic.
     */
    protected boolean isValidTarget(ElementContext context, org.bukkit.entity.LivingEntity target) {
        return !target.equals(context.getPlayer());
    }
}