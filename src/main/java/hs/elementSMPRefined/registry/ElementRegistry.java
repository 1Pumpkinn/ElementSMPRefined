package hs.elementSMPRefined.registry;

import hs.elementSMPRefined.API.element.Element;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.ElementSMPRefined;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Central lookup for all registered elements, keyed by {@link ElementType}.
 * An element is self-describing (see {@link Element}), so this registry only
 * tracks the instances themselves - no parallel metadata to keep in sync.
 */
public class ElementRegistry {
    private final ElementSMPRefined plugin;
    private final Map<ElementType, Element> elements = new EnumMap<>(ElementType.class);
    private boolean frozen = false;

    public ElementRegistry(JavaPlugin plugin) {
        this.plugin = (ElementSMPRefined) plugin;
    }

    /**
     * Register an element. Its {@link Element#getType()} determines its slot.
     */
    public void register(Element element) {
        if (frozen) {
            throw new IllegalStateException("Registry is frozen and cannot accept new registrations");
        }

        ElementType type = element.getType();
        if (elements.containsKey(type)) {
            plugin.getLogger().warning("Element " + type + " is already registered. Skipping duplicate.");
            return;
        }

        elements.put(type, element);
    }

    public Element get(ElementType type) {
        return elements.get(type);
    }

    public Collection<Element> getAllElements() {
        return Collections.unmodifiableCollection(elements.values());
    }

    public Set<ElementType> getAllTypes() {
        return Collections.unmodifiableSet(elements.keySet());
    }

    public boolean isRegistered(ElementType type) {
        return elements.containsKey(type);
    }

    /**
     * Freeze the registry to prevent further registrations.
     */
    public void freeze() {
        this.frozen = true;
    }

    public int size() {
        return elements.size();
    }
}
