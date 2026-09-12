package rose.elementSMPRefined.API.event;

import rose.elementSMPRefined.API.element.ElementId;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after a player is assigned an element via
 * {@link rose.elementSMPRefined.API.ElementApi#assignElement(Player, ElementId)}
 * (initial roll, admin grant, altar reward, etc.) - anything that resets the
 * player's upgrade level for their new element, as opposed to
 * {@link ElementSetEvent} which preserves it.
 * <p>
 * Fires after the assignment has already been saved, so this is purely
 * informational and is <b>not</b> cancellable. To block an assignment,
 * intervene before calling {@code assignElement} in the first place.
 */
public class ElementAssignEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ElementId newElementId;
    private final ElementId previousElementId;

    public ElementAssignEvent(Player player, ElementId newElementId, ElementId previousElementId) {
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

    /** The element the player had before this assignment, or {@code null} if they had none. */
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