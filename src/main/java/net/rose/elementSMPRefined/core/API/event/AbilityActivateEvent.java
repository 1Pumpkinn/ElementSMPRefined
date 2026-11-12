package net.rose.elementSMPRefined.core.API.event;

import net.rose.elementSMPRefined.core.API.element.ElementType;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after a player successfully activates an ability (slot 1 or 2 of
 * their current element).
 * <p>
 * This only fires on success. A failed activation (on cooldown, wrong upgrade
 * level, disarmed, etc.) never reaches this event, and the ability has already
 * executed by the time it fires - so this is purely informational and is
 * <b>not</b> cancellable.
 */
public class AbilityActivateEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final ElementType element;
    private final int slot;
    private final String abilityId;
    private final String abilityName;
    private final boolean stolen;

    public AbilityActivateEvent(
            @NotNull Player player,
            @NotNull ElementType element,
            int slot,
            @NotNull String abilityId,
            @NotNull String abilityName,
            boolean stolen) {
        super(player);
        this.element = element;
        this.slot = slot;
        this.abilityId = abilityId;
        this.abilityName = abilityName;
        this.stolen = stolen;
    }

    /** The element whose ability was cast (the stolen element during Soul Siphon). */
    public @NotNull ElementType getElement() {
        return element;
    }

    /** 1 or 2, matching the slot of the ability that was activated. */
    public int getSlot() {
        return slot;
    }

    /** The stable id of the ability, e.g. {@code air_dash}. */
    public @NotNull String getAbilityId() {
        return abilityId;
    }

    /** The display name of the ability (carries legacy colour codes). */
    public @NotNull String getAbilityName() {
        return abilityName;
    }

    /** True when the caster cast this through Soul Siphon rather than their own element. */
    public boolean isStolen() {
        return stolen;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
