package net.rose.elementSMPRefined.API.event;

import net.rose.elementSMPRefined.API.element.ElementId;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after a player's element is directly set via
 * {@link net.rose.elementSMPRefined.API.ElementApi#setElement(Player, ElementId)}
 * (e.g. a GUI reroll) - preserves the player's existing upgrade level, as
 * opposed to {@link ElementAssignEvent} which resets it.
 * <p>
 * Fires after the change has already been saved, so this is purely
 * informational and is <b>not</b> cancellable.
 */
public class ElementSetEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ElementId newElementId;
    private final ElementId previousElementId;

    public ElementSetEvent(Player player, ElementId newElementId, ElementId previousElementId) {
        this.player = player;
        this.newElementId = newElementId;
        this.previousElementId = previousElementId;
    }

    public Player getPlayer() {
        return player;
    }

    public ElementId getNewElementId() {
        return newElementId;
    }

    /** The element the player had before this change, or {@code null} if they had none. */
    public ElementId getPreviousElementId() {
        return previousElementId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}