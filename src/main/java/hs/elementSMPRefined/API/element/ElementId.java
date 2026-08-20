package hs.elementSMPRefined.API.element;

import java.util.Objects;

/**
 * Stable identifier for an element. Addons should use a unique namespace,
 * for example {@code myaddon:storm}.
 */
public record ElementId(String namespace, String key) {
    public ElementId {
        if (namespace == null || namespace.isBlank() || key == null || key.isBlank()) {
            throw new IllegalArgumentException("Element ID namespace and key are required");
        }
        if (!namespace.matches("[a-z0-9._-]+") || !key.matches("[a-z0-9._-]+")) {
            throw new IllegalArgumentException("Element IDs may only contain lowercase letters, numbers, '.', '_' or '-'");
        }
    }

    public static ElementId builtin(ElementType type) {
        Objects.requireNonNull(type, "type");
        return new ElementId("elements", type.name().toLowerCase());
    }

    public static ElementId parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Element ID cannot be null");
        }
        String[] parts = value.split(":", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Element ID must use the namespace:key format");
        }
        return new ElementId(parts[0], parts[1]);
    }

    @Override
    public String toString() {
        return namespace + ":" + key;
    }
}