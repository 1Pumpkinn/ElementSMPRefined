package net.rose.elementSMPRefined.core.API.element;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

class ElementTypeTest {

    @Test
    void parseIsCaseInsensitiveAndTrimsWhitespace() {
        assertEquals(
                Optional.of(net.rose.elementSMPRefined.core.API.element.ElementType.FIRE),
                net.rose.elementSMPRefined.core.API.element.ElementType.parse("fire"));
        assertEquals(
                Optional.of(net.rose.elementSMPRefined.core.API.element.ElementType.FIRE),
                net.rose.elementSMPRefined.core.API.element.ElementType.parse("FIRE"));
        assertEquals(
                Optional.of(net.rose.elementSMPRefined.core.API.element.ElementType.STORM),
                net.rose.elementSMPRefined.core.API.element.ElementType.parse("  Storm \n"));
    }

    @Test
    void parseRejectsNullBlankAndUnknownNames() {
        assertTrue(net.rose.elementSMPRefined.core.API.element.ElementType.parse(null).isEmpty());
        assertTrue(net.rose.elementSMPRefined.core.API.element.ElementType.parse("").isEmpty());
        assertTrue(net.rose.elementSMPRefined.core.API.element.ElementType.parse("   ").isEmpty());
        assertTrue(
                net.rose.elementSMPRefined.core.API.element.ElementType.parse("plasma").isEmpty());
    }

    @Test
    void legacyNamespacedFormatIsNoLongerReadable() {
        assertTrue(
                net.rose
                        .elementSMPRefined
                        .core
                        .API
                        .element
                        .ElementType
                        .parse("elements:fire")
                        .isEmpty());
    }

    @Test
    void parseRoundTripsEveryTypeName() {
        for (net.rose.elementSMPRefined.core.API.element.ElementType type :
                net.rose.elementSMPRefined.core.API.element.ElementType.values()) {
            assertEquals(
                    Optional.of(type),
                    net.rose.elementSMPRefined.core.API.element.ElementType.parse(type.name()));
        }
    }

    @Test
    void exampleIsNotPlayable() {
        assertFalse(net.rose.elementSMPRefined.core.API.element.ElementType.EXAMPLE.isPlayable());
        assertFalse(
                net.rose
                        .elementSMPRefined
                        .core
                        .API
                        .element
                        .ElementType
                        .playable()
                        .contains(net.rose.elementSMPRefined.core.API.element.ElementType.EXAMPLE));
    }

    @Test
    void everyOtherTypeIsPlayable() {
        for (net.rose.elementSMPRefined.core.API.element.ElementType type :
                net.rose.elementSMPRefined.core.API.element.ElementType.values()) {
            if (type != net.rose.elementSMPRefined.core.API.element.ElementType.EXAMPLE) {
                assertTrue(type.isPlayable(), type + " should be playable");
            }
        }
        assertEquals(
                net.rose.elementSMPRefined.core.API.element.ElementType.values().length - 1,
                net.rose.elementSMPRefined.core.API.element.ElementType.playable().size());
    }

    @Test
    void allEightCoreElementsAreRegisteredTypes() {
        Set<net.rose.elementSMPRefined.core.API.element.ElementType> expected =
                Set.of(
                        net.rose.elementSMPRefined.core.API.element.ElementType.AIR,
                        net.rose.elementSMPRefined.core.API.element.ElementType.WATER,
                        net.rose.elementSMPRefined.core.API.element.ElementType.FIRE,
                        net.rose.elementSMPRefined.core.API.element.ElementType.EARTH,
                        net.rose.elementSMPRefined.core.API.element.ElementType.METAL,
                        net.rose.elementSMPRefined.core.API.element.ElementType.FROST,
                        net.rose.elementSMPRefined.core.API.element.ElementType.LIFE,
                        net.rose.elementSMPRefined.core.API.element.ElementType.DEATH);
        assertTrue(
                net.rose
                        .elementSMPRefined
                        .core
                        .API
                        .element
                        .ElementType
                        .playable()
                        .containsAll(expected));
    }
}
