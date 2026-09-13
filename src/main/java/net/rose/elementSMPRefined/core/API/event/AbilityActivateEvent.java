package net.rose.elementSMPRefined.core.API.event;

import net.rose.elementSMPRefined.core.API.element.ElementId;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after a player successfully activates an ability - either a core
 * ability (slot 1 or 2 of their current element) or an addon ability
 * triggered by ID via {@link net.rose.elementSMPRefined.core.API.ElementApi#activateAbility(Player, String)}.
 * <p>
 * This only fires on success. A failed activation (on cooldown, insufficient
 * mana, wrong upgrade level, disarmed, etc.) never reaches this event, and the
 * ability has already executed and had its mana spent by the time it fires -
 * so this is purely informational and is <b>not</b> cancellable.
 */
public class AbilityActivateEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ElementId elementId;
    private final int slot;
    private final String abilityName;

    public AbilityActivateEvent(Player player, ElementId elementId, int slot, String abilityName) {
        this.player = player;
        this.elementId = elementId;
        this.slot = slot;
        this.abilityName = abilityName;
    }

    public Player getPlayer() {
        return player;
    }

    /** The element this ability belongs to. */
    public ElementId getElementId() {
        return elementId;
    }

    /**
     * 1 or 2 for a core element ability activated normally. -1 when activated
     * by ability ID through {@link net.rose.elementSMPRefined.core.API.ElementApi#activateAbility(Player, String)},
     * since that path isn't tied to a slot.
     */
    public int getSlot() {
        return slot;
    }

    /** Display name of the ability that was activated. */
    public String getAbilityName() {
        return abilityName;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}