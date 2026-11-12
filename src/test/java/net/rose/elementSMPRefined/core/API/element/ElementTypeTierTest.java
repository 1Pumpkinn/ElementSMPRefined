package net.rose.elementSMPRefined.core.API.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.rose.elementSMPRefined.core.API.element.ElementType.Tier;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

class ElementTypeTierTest {

    @Test
    void basicElementsAreTheFourStarterElementsInOrder() {
        assertEquals(
                java.util.List.of(
                        ElementType.AIR, ElementType.WATER, ElementType.FIRE, ElementType.EARTH),
                java.util.List.copyOf(ElementType.ofTier(Tier.BASIC)));
    }

    @Test
    void forgedElementsAreTheFourReforgeTargets() {
        assertEquals(
                EnumSet.of(
                        ElementType.METAL,
                        ElementType.FROST,
                        ElementType.LAVA,
                        ElementType.STORM),
                ElementType.ofTier(Tier.FORGED));
    }

    @Test
    void mythicElementsAreLifeAndDeath() {
        assertEquals(
                EnumSet.of(ElementType.LIFE, ElementType.DEATH), ElementType.ofTier(Tier.MYTHIC));
    }

    @Test
    void everyTypeBelongsToExactlyOneTier() {
        int total = 0;
        for (Tier tier : Tier.values()) {
            total += ElementType.ofTier(tier).size();
        }
        assertEquals(ElementType.values().length, total);
    }

    @Test
    void tierFlagsAgreeWithGetTier() {
        for (ElementType type : ElementType.values()) {
            assertEquals(type.getTier() == Tier.BASIC, type.isBasic(), type.name());
            assertEquals(type.getTier() == Tier.FORGED, type.isForged(), type.name());
            assertEquals(type.getTier() == Tier.MYTHIC, type.isMythic(), type.name());
            assertEquals(type.getTier().isPlayable(), type.isPlayable(), type.name());
        }
    }

    @Test
    void templateTierIsNotPlayable() {
        assertEquals(EnumSet.of(ElementType.EXAMPLE), ElementType.ofTier(Tier.TEMPLATE));
        assertFalse(ElementType.EXAMPLE.isPlayable());
        assertTrue(ElementType.playable().containsAll(ElementType.ofTier(Tier.MYTHIC)));
    }
}
