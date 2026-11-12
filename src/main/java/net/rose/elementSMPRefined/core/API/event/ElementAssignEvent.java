package net.rose.elementSMPRefined.core.API.event;

import net.rose.elementSMPRefined.core.API.element.ElementType;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired after a player is given an element through
 * {@link net.rose.elementSMPRefined.managers.ElementManager#assignElement(Player, ElementType, Cause)}
 * - the first-join roll, rerollers, forging, mythic books, withdrawing and death reverts.
 * See {@link ElementSetEvent} for the admin route.
 * <p>
 * Fires after the assignment has already been saved, so this is purely informational and is
 * <b>not</b> cancellable. The player's upgrade level is kept.
 */
public class ElementAssignEvent extends ElementChangeEvent {

    public ElementAssignEvent(
            @NotNull Player player,
            @NotNull ElementType newElement,
            @Nullable ElementType previousElement,
            @NotNull Cause cause) {
        super(player, newElement, previousElement, cause);
    }

    public static @NotNull HandlerList getHandlerList() {
        return ElementChangeEvent.getHandlerList();
    }
}
