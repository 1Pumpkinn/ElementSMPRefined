package hs.elementSMPRefined.API.element;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Optional interface for elements that need to register event listeners as part of their passives.
 * Implement this to declare which listeners your element needs registered, eliminating the need
 * for hard-coded listener wiring in ListenerInitializer.
 * 
 * Usage:
 * <pre>
 * public class MyElement extends BaseElement implements ListenerProvider {
 *     @Override
 *     public List&lt;Listener&gt; getListeners(JavaPlugin plugin) {
 *         return List.of(new MyPassiveListener(plugin, ...));
 *     }
 * }
 * </pre>
 */
public interface ListenerProvider {
    /**
     * Return all listeners this element requires to be registered.
     * Listeners are registered in the order returned.
     * 
     * @param plugin The plugin instance for lifecycle management
     * @return A list of listeners for this element's passives or abilities
     */
    List<Listener> getListeners(JavaPlugin plugin);
}
