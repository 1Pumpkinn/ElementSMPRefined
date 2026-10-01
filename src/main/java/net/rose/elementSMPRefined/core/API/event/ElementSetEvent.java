package net.rose.elementSMPRefined.core.API.event;

import net.rose.elementSMPRefined.core.API.element.ElementType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after a player's element is directly set via
 * {@link net.rose.elementSMPRefined.managers.ElementManager#setElement(Player, ElementType)}
 * (e.g. a GUI reroll) - preserves the player's existing upgrade level, as
 * opposed to {@link ElementAssignEvent} which resets it.
 * <p>
 * Fires after the change has already been saved, so this is purely
 * informational and is <b>not</b> cancellable.
 */
public class ElementSetEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ElementType newElement;
    private final ElementType previousElement;

    public ElementSetEvent(Player player, ElementType newElement, ElementType previousElement) {
        this.player = player;
        this.newElement = newElement;
        this.previousElement = previousElement;
    }

    public Player getPlayer() {
        return player;
    }

    public ElementType getNewElement() {
        return newElement;
    }

    /** The element the player had before this change, or {@code null} if they had none. */
    public ElementType getPreviousElement() {
        return previousElement;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}