package net.rose.elementevents.api.event;

import net.rose.elementevents.api.ServerEvent;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after a {@link ServerEvent} has stopped - either it ran its course (rewards were handed
 * out) or an admin cancelled it. Not fired when the plugin shuts down mid-event. Purely
 * informational and not cancellable.
 */
public class ServerEventEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public enum Reason {
        /** The timer ran out or an admin ended it; rewards were awarded. */
        COMPLETED,
        /** An admin cancelled it; nothing was awarded. */
        CANCELLED
    }

    private final ServerEvent serverEvent;
    private final Reason reason;

    public ServerEventEndEvent(@NotNull ServerEvent serverEvent, @NotNull Reason reason) {
        this.serverEvent = serverEvent;
        this.reason = reason;
    }

    public @NotNull ServerEvent getServerEvent() {
        return serverEvent;
    }

    public @NotNull Reason getReason() {
        return reason;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
