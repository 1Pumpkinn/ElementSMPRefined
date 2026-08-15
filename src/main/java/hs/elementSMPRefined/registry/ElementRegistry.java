package hs.elementSMPRefined.registry;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.API.Element;
import hs.elementSMPRefined.API.ElementType;

import java.util.*;

/**
 * Central registry for all elements. Provides automatic registration and lookup.
 * Replaces manual element registration in ElementManager.
 */
public class ElementRegistry {
    private final ElementSMPRefined plugin;
    private final Map<ElementType, Element> elements = new EnumMap<>(ElementType.class);
    private final Map<ElementType, ElementData> elementData = new EnumMap<>(ElementType.class);
    private boolean frozen = false;

    public ElementRegistry(ElementSMPRefined plugin) {
        this.plugin = plugin;
    }

    /**
     * Register an element with the registry.
     * @param element The element instance
     * @param data The element's metadata
     */
    public void register(Element element, ElementData data) {
        if (frozen) {
            throw new IllegalStateException("Registry is frozen and cannot accept new registrations");
        }

        ElementType type = element.getType();
        if (elements.containsKey(type)) {
            plugin.getLogger().warning("Element " + type + " is already registered. Skipping duplicate.");
            return;
        }

        elements.put(type, element);
        elementData.put(type, data);
    }

    /**
     * Get an element by type
     */
    public Element get(ElementType type) {
        return elements.get(type);
    }

    /**
     * Get element data by type
     */
    public ElementData getData(ElementType type) {
        return elementData.get(type);
    }

    /**
     * Get all registered elements
     */
    public Collection<Element> getAllElements() {
        return Collections.unmodifiableCollection(elements.values());
    }

    /**
     * Get all registered element types
     */
    public Set<ElementType> getAllTypes() {
        return Collections.unmodifiableSet(elements.keySet());
    }

    /**
     * Check if an element type is registered
     */
    public boolean isRegistered(ElementType type) {
        return elements.containsKey(type);
    }

    /**
     * Freeze the registry to prevent further registrations
     */
    public void freeze() {
        this.frozen = true;
    }

    /**
     * Get the number of registered elements
     */
    public int size() {
        return elements.size();
    }

    /**
     * Data class for element metadata
     */
    public record ElementData(
            String displayName,
            String description,
            String color,
            boolean isBasic,
            boolean requiresUpgrade,
            ElementType[] requiredElements
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String displayName;
            private String description;
            private String color = "WHITE";
            private boolean isBasic = false;
            private boolean requiresUpgrade = false;
            private ElementType[] requiredElements = new ElementType[0];

            public Builder displayName(String displayName) {
                this.displayName = displayName;
                return this;
            }

            public Builder description(String description) {
                this.description = description;
                return this;
            }

            public Builder color(String color) {
                this.color = color;
                return this;
            }

            public Builder isBasic(boolean isBasic) {
                this.isBasic = isBasic;
                return this;
            }

            public Builder requiresUpgrade(boolean requiresUpgrade) {
                this.requiresUpgrade = requiresUpgrade;
                return this;
            }

            public Builder requiredElements(ElementType... elements) {
                this.requiredElements = elements;
                return this;
            }

            public ElementData build() {
                if (displayName == null) {
                    throw new IllegalStateException("Display name is required");
                }
                if (description == null) {
                    throw new IllegalStateException("Description is required");
                }
                return new ElementData(displayName, description, color, isBasic, requiresUpgrade, requiredElements);
            }
        }
    }
}