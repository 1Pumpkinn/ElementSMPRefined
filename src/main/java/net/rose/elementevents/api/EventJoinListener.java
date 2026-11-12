package net.rose.elementevents.api;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Shows every running {@link ServerEvent} to players who join mid-event. */
public class EventJoinListener implements Listener {
    private final EventManager events;

    public EventJoinListener(EventManager events) {
        this.events = events;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        events.showActiveTo(event.getPlayer());
    }
}
