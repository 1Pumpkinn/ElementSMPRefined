package net.rose.elementSMPRefined.core.API.element;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElementTypeTest {

    @Test
    void parseIsCaseInsensitiveAndTrimsWhitespace() {
        assertEquals(Optional.of(ElementType.FIRE), ElementType.parse("fire"));
        assertEquals(Optional.of(ElementType.FIRE), ElementType.parse("FIRE"));
        assertEquals(Optional.of(ElementType.STORM), ElementType.parse("  Storm \n"));
    }

    @Test
    void parseRejectsNullBlankAndUnknownNames() {
        assertTrue(ElementType.parse(null).isEmpty());
        assertTrue(ElementType.parse("").isEmpty());
        assertTrue(ElementType.parse("   ").isEmpty());
        assertTrue(ElementType.parse("plasma").isEmpty());
    }

    @Test
    void legacyNamespacedFormatIsNoLongerReadable() {
        assertTrue(ElementType.parse("elements:fire").isEmpty());
    }

    @Test
    void parseRoundTripsEveryTypeName() {
        for (ElementType type : ElementType.values()) {
            assertEquals(Optional.of(type), ElementType.parse(type.name()));
        }
    }

    @Test
    void exampleIsNotPlayable() {
        assertFalse(ElementType.EXAMPLE.isPlayable());
        assertFalse(ElementType.playable().contains(ElementType.EXAMPLE));
    }

    @Test
    void everyOtherTypeIsPlayable() {
        for (ElementType type : ElementType.values()) {
            if (type != ElementType.EXAMPLE) {
                assertTrue(type.isPlayable(), type + " should be playable");
            }
        }
        assertEquals(ElementType.values().length - 1, ElementType.playable().size());
    }

    @Test
    void allEightCoreElementsAreRegisteredTypes() {
        Set<ElementType> expected = Set.of(
                ElementType.AIR, ElementType.WATER, ElementType.FIRE, ElementType.EARTH,
                ElementType.METAL, ElementType.FROST, ElementType.LIFE, ElementType.DEATH);
        assertTrue(ElementType.playable().containsAll(expected));
    }
}
