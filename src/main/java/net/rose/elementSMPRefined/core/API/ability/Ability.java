package net.rose.elementSMPRefined.core.API.ability;

import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public interface Ability {
    /**
     * Execute the ability
     * @param context The context for the ability execution
     * @return true if the ability was executed successfully, false otherwise
     */
    boolean execute(ElementContext context);

    int getCooldownSeconds();

    /**
     * Upgrade level the caster needs before this ability can be used. By convention the
     * slot-1 ability needs level 1 and the slot-2 ability needs level 2.
     */
    int getRequiredUpgradeLevel();

    String getAbilityId();

    /** The element this ability belongs to, or {@code null} for ability templates. */
    default @Nullable ElementType getElementType() {
        return null;
    }

    /** 1 or 2 - which of the element's two ability slots this is - or 0 if unassigned. */
    default int getSlot() {
        return 0;
    }

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
