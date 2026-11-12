package net.rose.elementevents.api.event;

import net.rose.elementevents.api.ServerEvent;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired just before a {@link ServerEvent} starts. Cancel it to stop the event from starting - the
 * {@code start} call then returns false and nothing is set up.
 */
public class ServerEventStartEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final ServerEvent serverEvent;
    private final int durationSeconds;
    private boolean cancelled;

    public ServerEventStartEvent(@NotNull ServerEvent serverEvent, int durationSeconds) {
        this.serverEvent = serverEvent;
        this.durationSeconds = durationSeconds;
    }

    public @NotNull ServerEvent getServerEvent() {
        return serverEvent;
    }

    /** How long the event is about to run. */
    public int getDurationSeconds() {
        return durationSeconds;
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
