package net.rose.elementevents.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EventIdsTest {

    @Test
    void normalizeIgnoresCaseSeparatorsAndWhitespace() {
        assertEquals("lifedeath", EventIds.normalize("life_death"));
        assertEquals("lifedeath", EventIds.normalize("Life-Death"));
        assertEquals("lifedeath", EventIds.normalize("  LIFE DEATH "));
        assertEquals("lifedeath", EventIds.normalize("lifedeath"));
    }

    @Test
    void normalizeGivesEmptyForNullAndBlank() {
        assertEquals("", EventIds.normalize(null));
        assertEquals("", EventIds.normalize(""));
        assertEquals("", EventIds.normalize(" _- "));
    }

    @Test
    void validIdsAreLowerCaseWordsJoinedByUnderscores() {
        assertTrue(EventIds.isValidId("life_death"));
        assertTrue(EventIds.isValidId("boss"));
        assertTrue(EventIds.isValidId("storm2_hunt"));
    }

    @Test
    void invalidIdsAreRejected() {
        assertFalse(EventIds.isValidId(null));
        assertFalse(EventIds.isValidId(""));
        assertFalse(EventIds.isValidId("Life_Death"));
        assertFalse(EventIds.isValidId("life-death"));
        assertFalse(EventIds.isValidId("_life"));
        assertFalse(EventIds.isValidId("life_"));
        assertFalse(EventIds.isValidId("life__death"));
        assertFalse(EventIds.isValidId("2fast"));
    }
}
