package net.rose.elementSMPRefined.core.API.element;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;

/**
 * Every element in the plugin. This is the single identifier used for registry
 * lookups, persistence, events and item tags.
 * <p>
 * Each type belongs to exactly one {@link Tier}; the tier is the only place that decides
 * how an element can be obtained (starter roll, forging, mythic book), so nothing else
 * needs its own parallel "which elements are basic" list.
 */
public enum ElementType {
    AIR(Tier.BASIC),
    WATER(Tier.BASIC),
    FIRE(Tier.BASIC),
    EARTH(Tier.BASIC),

    METAL(Tier.FORGED),
    FROST(Tier.FORGED),
    LAVA(Tier.FORGED),
    STORM(Tier.FORGED),

    LIFE(Tier.MYTHIC),
    DEATH(Tier.MYTHIC),

    EXAMPLE(Tier.TEMPLATE);

    /** How an element is obtained. */
    public enum Tier {
        /** Rolled on first join and by the basic reroller. */
        BASIC(true),
        /** Reached by reforging a basic element (Lava, Metal, Frost, Storm). */
        FORGED(true),
        /**
         * Only one player on the whole server can hold each; claimed through a mythic book
         * (see {@code net.rose.elementevents.mythic.MythicBook}) rather than rerolling.
         */
        MYTHIC(true),
        /** Copy-me templates such as {@code ExampleElement}; never given to players. */
        TEMPLATE(false);

        private final boolean playable;

        Tier(boolean playable) {
            this.playable = playable;
        }

        public boolean isPlayable() {
            return playable;
        }
    }

    private final Tier tier;

    ElementType(Tier tier) {
        this.tier = tier;
    }

    public Tier getTier() {
        return tier;
    }

    /** Whether players can actually receive this element (false for example/template types). */
    public boolean isPlayable() {
        return tier.isPlayable();
    }

    public boolean isBasic() {
        return tier == Tier.BASIC;
    }

    /** Forged elements are the upgraded ones reached through the Reforge Altar. */
    public boolean isForged() {
        return tier == Tier.FORGED;
    }

    /**
     * Mythic elements: only one player on the whole server can hold each, and they are obtained
     * through a mythic book rather than rerolling.
     */
    public boolean isMythic() {
        return tier == Tier.MYTHIC;
    }

    /** All types that can actually be given to players. */
    public static EnumSet<ElementType> playable() {
        EnumSet<ElementType> result = EnumSet.noneOf(ElementType.class);
        for (ElementType type : values()) {
            if (type.isPlayable()) {
                result.add(type);
            }
        }
        return result;
    }

    /** All types in {@code tier}, in declaration order. */
    public static EnumSet<ElementType> ofTier(Tier tier) {
        EnumSet<ElementType> result = EnumSet.noneOf(ElementType.class);
        for (ElementType type : values()) {
            if (type.tier == tier) {
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
