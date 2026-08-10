package hs.elementSMPRefined.config;

import hs.elementPlugin.elements.ElementType;

import java.util.EnumMap;
import java.util.Map;

public final class AbilityCosts {
    private AbilityCosts() {}

    public record ElementCosts(int ability1, int ability2, int itemUse, int itemThrow) {
        public ElementCosts {
            if (ability1 < 0 || ability2 < 0 || itemUse < 0 || itemThrow < 0) {
                throw new IllegalArgumentException("Costs cannot be negative");
            }
        }
    }

    private static final Map<ElementType, ElementCosts> DEFAULTS = new EnumMap<>(ElementType.class);

    static {
        DEFAULTS.put(ElementType.AIR, new ElementCosts(50, 75, 75, 25));
        DEFAULTS.put(ElementType.WATER, new ElementCosts(50, 75, 75, 25));
        DEFAULTS.put(ElementType.FIRE, new ElementCosts(50, 75, 75, 25));
        DEFAULTS.put(ElementType.EARTH, new ElementCosts(50, 75, 75, 25));
        DEFAULTS.put(ElementType.LIFE, new ElementCosts(50, 75, 75, 25));
        DEFAULTS.put(ElementType.DEATH, new ElementCosts(50, 75, 75, 25));
        DEFAULTS.put(ElementType.METAL, new ElementCosts(50, 75, 75, 25));
        DEFAULTS.put(ElementType.FROST, new ElementCosts(50, 75, 75, 25));
    }

    public static ElementCosts getDefaults(ElementType type) {
        return DEFAULTS.getOrDefault(type, new ElementCosts(50, 75, 75, 25));
    }
}

