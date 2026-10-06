package net.rose.elementSMPRefined.core.API.element;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;

/**
 * Every element in the plugin. This is the single identifier used for registry
 * lookups, persistence, events and item tags.
 */
public enum ElementType {
    AIR,
    WATER,
    FIRE,
    EARTH,
    LIFE,
    DEATH,
    METAL,
    FROST,
    LAVA,

    /**
     * Placeholder used by {@code ExampleElement}. It is never registered, so it is
     * not {@linkplain #isPlayable() playable}: it can't be rolled, set or suggested.
     */
    EXAMPLE(false);

    private final boolean playable;

    ElementType() {
        this(true);
    }

    ElementType(boolean playable) {
        this.playable = playable;
    }

    /** Whether players can actually receive this element (false for example/template types). */
    public boolean isPlayable() {
        return playable;
    }

    /** All types that can actually be given to players. */
    public static EnumSet<ElementType> playable() {
        EnumSet<ElementType> result = EnumSet.noneOf(ElementType.class);
        for (ElementType type : values()) {
            if (type.playable) {
                result.add(type);
            }
        }
        return result;
    }

    /**
     * Parses a stored or typed name ("fire", "FIRE"), case-insensitively and ignoring
     * surrounding whitespace. Anything that isn't an element name gives an empty result.
     */
    public static Optional<ElementType> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
