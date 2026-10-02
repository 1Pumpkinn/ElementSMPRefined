package net.rose.elementSMPRefined.core.API.event;

import net.rose.elementSMPRefined.core.API.element.ElementType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before a player's upgrade level for an element changes. Core fires it
 * when an upgrader item is right-clicked.
 * <p>
 * Cancel this to block the level change - nothing is consumed or applied.
 */
public class UpgradeLevelChangeEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final ElementType element;
    private final int previousLevel;
    private final int newLevel;
    private boolean cancelled;

    public UpgradeLevelChangeEvent(@NotNull Player player, @NotNull ElementType element, int previousLevel, int newLevel) {
        super(player);
        this.element = element;
        this.previousLevel = previousLevel;
        this.newLevel = newLevel;
    }

    /**
     * Gets the element whose upgrade level is changing.
     *
     * @return The element type.
     */
    public @NotNull ElementType getElement() {
        return element;
    }

    /**
     * Gets the player's upgrade level before this change.
     *
     * @return The previous level.
     */
    public int getPreviousLevel() {
        return previousLevel;
    }

    /**
     * Gets the upgrade level the player is about to have.
     *
     * @return The new level.
     */
    public int getNewLevel() {
        return newLevel;
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