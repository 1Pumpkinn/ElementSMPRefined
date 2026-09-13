package net.rose.elementSMPRefined.API.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after mana has been spent on a successful ability activation.
 * <p>
 * Informational only - by the time this fires the mana is already gone and
 * the ability has already run. Not cancellable; use {@link AbilityActivateEvent}
 * if you need to stop the activation before mana is spent.
 */
public class ManaSpendEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final int amountSpent;
    private final int remainingMana;

    public ManaSpendEvent(@NotNull Player player, int amountSpent, int remainingMana) {
        super(player);
        this.amountSpent = amountSpent;
        this.remainingMana = remainingMana;
    }

    /**
     * Gets the amount of mana that was spent.
     *
     * @return The amount spent.
     */
    public int getAmountSpent() {
        return amountSpent;
    }

    /**
     * Gets the player's mana remaining after the spend.
     *
     * @return The remaining mana.
     */
    public int getRemainingMana() {
        return remainingMana;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}