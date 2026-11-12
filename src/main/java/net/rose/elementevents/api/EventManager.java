package net.rose.elementevents.api;

import net.rose.elementSMPRefined.ElementSMPRefined;

import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Registry of every {@link ServerEvent}. Register an event once at startup; everything else -
 * {@code /element event}, its tab completion, late-joiner handling, listener registration and
 * shutdown - goes through here, so adding an event touches nothing else.
 */
public class EventManager {
    private final ElementSMPRefined plugin;
    private final Map<String, ServerEvent> byId = new LinkedHashMap<>();
    private final Map<String, ServerEvent> byKey = new HashMap<>();

    public EventManager(ElementSMPRefined plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers {@code event} under its id and aliases.
     *
     * @throws IllegalArgumentException if the id or an alias is already taken
     */
    public void register(ServerEvent event) {
        Objects.requireNonNull(event, "event");
        String id = event.getId();
        if (!EventIds.isValidId(id)) {
            throw new IllegalArgumentException("Invalid event id: " + id);
        }
        if (byId.containsKey(id)) {
            throw new IllegalArgumentException("Event already registered: " + id);
        }

        List<String> keys = new ArrayList<>();
        keys.add(EventIds.normalize(id));
        for (String alias : event.getAliases()) {
            keys.add(EventIds.normalize(alias));
        }
        for (String key : keys) {
            if (key.isEmpty()) {
                throw new IllegalArgumentException(id + " has a blank alias");
            }
            ServerEvent taken = byKey.get(key);
            if (taken != null && taken != event) {
                throw new IllegalArgumentException(
                        id + " clashes with '" + taken.getId() + "' on name '" + key + "'");
            }
        }

        byId.put(id, event);
        keys.forEach(key -> byKey.put(key, event));
    }

    /** Finds an event by id or alias, ignoring case, '-' and '_'. */
    public Optional<ServerEvent> find(String raw) {
        String key = EventIds.normalize(raw);
        return key.isEmpty() ? Optional.empty() : Optional.ofNullable(byKey.get(key));
    }

    /** The registered event of exactly this class, e.g. {@code get(LifeDeathEvent.class)}. */
    public <T extends ServerEvent> Optional<T> get(Class<T> type) {
        for (ServerEvent event : byId.values()) {
            if (type.isInstance(event)) {
                return Optional.of(type.cast(event));
            }
        }
        return Optional.empty();
    }

    /** All registered events in registration order. */
    public Collection<ServerEvent> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    /** The ids of all registered events, for tab completion. */
    public List<String> ids() {
        return List.copyOf(byId.keySet());
    }

    /** The events that are running right now. */
    public List<ServerEvent> active() {
        return byId.values().stream().filter(ServerEvent::isActive).toList();
    }

    /** Shows every running event (boss bar...) to a player who just joined. */
    public void showActiveTo(Player player) {
        for (ServerEvent event : byId.values()) {
            if (event.isActive()) {
                event.showTo(player);
            }
        }
    }

    /** Registers the join handler and every event's own listeners. Call once at startup. */
    public void registerListeners() {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(new EventJoinListener(this), plugin);
        for (ServerEvent event : byId.values()) {
            event.getListeners().forEach(l -> pluginManager.registerEvents(l, plugin));
        }
    }

    /**
     * Stops every event without announcing anything (plugin disable). One event failing to clean
     * up must not stop the rest.
     */
    public void cleanup() {
        for (ServerEvent event : byId.values()) {
            try {
                event.cleanup();
            } catch (Exception e) {
                plugin.getLogger()
                        .log(Level.SEVERE, "Could not clean up event " + event.getId(), e);
            }
        }
    }
}
