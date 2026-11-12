package net.rose.elementSMPRefined.core.API.event;

import net.rose.elementSMPRefined.core.API.element.ElementType;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired after a player's element is directly set by an admin via
 * {@link net.rose.elementSMPRefined.managers.ElementManager#setElement(Player, ElementType)}
 * ({@code /element set}). The cause is always {@link Cause#ADMIN}.
 * <p>
 * Fires after the change has already been saved, so this is purely informational and is
 * <b>not</b> cancellable. The player's upgrade level is kept.
 */
public class ElementSetEvent extends ElementChangeEvent {

    public ElementSetEvent(
            @NotNull Player player,
            @NotNull ElementType newElement,
            @Nullable ElementType previousElement) {
        super(player, newElement, previousElement, Cause.ADMIN);
    }

    public static @NotNull HandlerList getHandlerList() {
        return ElementChangeEvent.getHandlerList();
    }
}
