package net.rose.elementSMPRefined.core.API.event;

import net.rose.elementSMPRefined.core.API.element.ElementType;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base of every event fired after a player's element changed. Listen to this class to react to
 * any element change regardless of how it happened, or to {@link ElementAssignEvent} /
 * {@link ElementSetEvent} to react to just one route. All subclasses share one handler list, so a
 * listener on this class receives both.
 * <p>
 * These fire after the change has been applied and saved, so they are purely informational and
 * <b>not</b> cancellable. The player's upgrade level is kept across the change.
 */
public abstract class ElementChangeEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    /** Why the element changed. */
    public enum Cause {
        /** The roll on first join. */
        FIRST_JOIN,
        /** The basic or advanced reroller. */
        REROLL,
        /** A Forged Upgrader turned a basic element into its forged one. */
        FORGE,
        /** {@code /withdraw forged} turned a forged element back into its base. */
        UNFORGE,
        /** A forged element reverted to its base because the player died. */
        DEATH_REVERT,
        /** A mythic book was used. */
        MYTHIC_CLAIM,
        /** {@code /withdraw mythic} turned a mythic element back into a book. */
        MYTHIC_WITHDRAW,
        /** {@code /element set}. */
        ADMIN
    }

    private final ElementType newElement;
    private final ElementType previousElement;
    private final Cause cause;

    protected ElementChangeEvent(
            @NotNull Player player,
            @NotNull ElementType newElement,
            @Nullable ElementType previousElement,
            @NotNull Cause cause) {
        super(player);
        this.newElement = newElement;
        this.previousElement = previousElement;
        this.cause = cause;
    }

    public @NotNull ElementType getNewElement() {
        return newElement;
    }

    /** The element the player had before this change, or {@code null} if they had none. */
    public @Nullable ElementType getPreviousElement() {
        return previousElement;
    }

    public @NotNull Cause getCause() {
        return cause;
    }

    /** False when the player re-rolled into the element they already had. */
    public boolean isChange() {
        return previousElement != newElement;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
