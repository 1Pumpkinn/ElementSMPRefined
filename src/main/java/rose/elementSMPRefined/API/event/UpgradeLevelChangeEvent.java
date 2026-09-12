package rose.elementSMPRefined.API.event;

import rose.elementSMPRefined.API.element.ElementId;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before a player's upgrade level for an element changes (e.g. leveling
 * up an element's abilities). Not currently fired by core - wire this in
 * wherever upgrade levels are applied (upgrade command/GUI) once that code
 * is touched.
 * <p>
 * Cancel this to block the level change.
 */
public class UpgradeLevelChangeEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final ElementId elementId;
    private final int previousLevel;
    private final int newLevel;
    private boolean cancelled;

    public UpgradeLevelChangeEvent(@NotNull Player player, @NotNull ElementId elementId, int previousLevel, int newLevel) {
        super(player);
        this.elementId = elementId;
        this.previousLevel = previousLevel;
        this.newLevel = newLevel;
    }

    /**
     * Gets the element whose upgrade level is changing.
     *
     * @return The element ID.
     */
    public @NotNull ElementId getElementId() {
        return elementId;
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