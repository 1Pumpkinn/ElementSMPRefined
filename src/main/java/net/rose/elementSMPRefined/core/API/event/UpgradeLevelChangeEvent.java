package net.rose.elementSMPRefined.core.API.event;

import net.rose.elementSMPRefined.core.API.element.ElementType;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before a player's upgrade level for an element changes - when an upgrader item is
 * right-clicked and when one is withdrawn with {@code /withdraw upgrader}.
 * <p>
 * Cancel this to block the level change - nothing is consumed or applied.
 */
public class UpgradeLevelChangeEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    /** What triggered the change. */
    public enum Cause {
        /** An upgrader item was used. */
        UPGRADER,
        /** The level was withdrawn back into an upgrader item. */
        WITHDRAW
    }

    private final ElementType element;
    private final int previousLevel;
    private final int newLevel;
    private final Cause cause;
    private boolean cancelled;

    public UpgradeLevelChangeEvent(
            @NotNull Player player,
            @NotNull ElementType element,
            int previousLevel,
            int newLevel,
            @NotNull Cause cause) {
        super(player);
        this.element = element;
        this.previousLevel = previousLevel;
        this.newLevel = newLevel;
        this.cause = cause;
    }

    public @NotNull ElementType getElement() {
        return element;
    }

    public int getPreviousLevel() {
        return previousLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }

    public @NotNull Cause getCause() {
        return cause;
    }

    public boolean isUpgrade() {
        return newLevel > previousLevel;
    }

    public boolean isDowngrade() {
        return newLevel < previousLevel;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
