package net.rose.elementSMPRefined.core.API.event;

import net.rose.elementSMPRefined.core.API.element.ElementType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after a player is assigned an element via
 * {@link net.rose.elementSMPRefined.managers.ElementManager#assignBasicElement(Player, ElementType)}
 * (the first-join roll and the basic reroller). The player's upgrade level is
 * kept, as opposed to {@link ElementSetEvent}, which resets it.
 * <p>
 * Fires after the assignment has already been saved, so this is purely
 * informational and is <b>not</b> cancellable. To block an assignment,
 * intervene before calling {@code assignBasicElement} in the first place.
 */
public class ElementAssignEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ElementType newElement;
    private final ElementType previousElement;

    public ElementAssignEvent(Player player, ElementType newElement, ElementType previousElement) {
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

    /** The element the player had before this assignment, or {@code null} if they had none. */
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