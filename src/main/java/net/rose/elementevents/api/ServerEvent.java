package net.rose.elementevents.api;

import net.kyori.adventure.text.Component;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Set;

/**
 * A server-wide event that admins start with {@code /element event start <id>} (Life &amp; Death
 * today, more mythic events later). Implement this - usually by extending
 * {@link AbstractTimedEvent} - and register it with {@link EventManager#register(ServerEvent)};
 * the command, tab completion, join handling, listener registration and shutdown are all driven
 * by the registry, so a new event needs no changes anywhere else.
 */
public interface ServerEvent {

    /** Stable lower-case id with underscores, e.g. {@code life_death}. Also the config key. */
    String getId();

    /** Display title, used for the boss bar and chat. */
    Component getTitle();

    /** Extra names the command accepts for this event (matching ignores case, '-' and '_'). */
    default Set<String> getAliases() {
        return Set.of();
    }

    boolean isActive();

    /** Seconds left, or 0 when the event isn't running. */
    int getRemainingSeconds();

    /** The duration used when {@code /element event start} gets none. */
    int defaultDurationSeconds();

    /**
     * Starts the event.
     *
     * @return false if it is already running or a {@code ServerEventStartEvent} listener
     *     cancelled it
     */
    boolean start(int durationSeconds);

    /** Ends the event now and hands out its rewards. @return false if it wasn't running. */
    boolean end();

    /** Stops the event without rewards. @return false if it wasn't running. */
    boolean cancel();

    /** Stops timers and hides the boss bar without announcing anything. Idempotent. */
    void cleanup();

    /** Shows the event to someone who joined mid-event. */
    void showTo(Player player);

    /** Sends the current state of the event (time left, standings...) to {@code sender}. */
    void sendStatus(CommandSender sender);

    /**
     * Messages shown to the admin right after a successful start - warnings such as "this reward
     * is already claimed". Empty by default.
     */
    default List<Component> startNotes() {
        return List.of();
    }

    /** Bukkit listeners this event needs; registered once at startup. */
    default List<Listener> getListeners() {
        return List.of();
    }
}
