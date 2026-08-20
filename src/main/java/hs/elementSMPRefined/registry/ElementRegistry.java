package hs.elementSMPRefined.registry;

import hs.elementSMPRefined.API.element.Element;
import hs.elementSMPRefined.API.element.ElementId;
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
    private final Map<ElementId, Element> elementsById = new java.util.HashMap<>();
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

        ElementId id = element.getId();
        if (elementsById.containsKey(id)) {
            plugin.getLogger().warning("Element " + id + " is already registered. Skipping duplicate.");
            return;
        }

        elementsById.put(id, element);
        ElementType type = element.getType();
        if (type != null) {
            elements.put(type, element);
        }
    }

    /**
     * Register an element supplied by an addon after built-in registration is complete.
     */
    public void registerAddon(Element element) {
        if (element == null || element.getType() == null) {
            throw new IllegalArgumentException("Addon element and element type are required");
        }

        ElementId id = element.getId();
        Element existing = elementsById.putIfAbsent(id, element);
        if (existing != null) {
            throw new IllegalArgumentException("Element " + id + " is already registered");
        }

        ElementType type = element.getType();
        if (type != null) {
            Element enumElement = elements.putIfAbsent(type, element);
            if (enumElement != null) {
                elementsById.remove(id, element);
                throw new IllegalArgumentException("Element " + type + " is already registered");
            }
        }
    }

    public Element get(ElementType type) {
        return elements.get(type);
    }

    public Element get(ElementId id) {
        return elementsById.get(id);
    }

    public Collection<Element> getAllElements() {
        return Collections.unmodifiableCollection(elements.values());
    }

    public Set<ElementType> getAllTypes() {
        return Collections.unmodifiableSet(elements.keySet());
    }

    public Set<ElementId> getAllIds() {
        return Collections.unmodifiableSet(elementsById.keySet());
    }

    public boolean isRegistered(ElementType type) {
        return elements.containsKey(type);
    }

    public boolean isRegistered(ElementId id) {
        return elementsById.containsKey(id);
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
