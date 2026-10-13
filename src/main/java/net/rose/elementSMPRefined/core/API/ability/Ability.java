package net.rose.elementSMPRefined.core.API.ability;

import net.rose.elementSMPRefined.core.API.element.ElementContext;
import org.bukkit.entity.Player;

public interface Ability {
    /**
     * Execute the ability
     * @param context The context for the ability execution
     * @return true if the ability was executed successfully, false otherwise
     */
    boolean execute(ElementContext context);

    int getCooldownSeconds();

    int getRequiredUpgradeLevel();

    String getAbilityId();

    /**
     * Check if the ability is currently active for the player
     * @param player The player to check
     * @return true if the ability is active, false otherwise
     */
    boolean isActiveFor(Player player);

    void setActive(Player player, boolean active);

    String getName();

    String getDescription();
}