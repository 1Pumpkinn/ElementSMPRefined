package net.rose.elementevents.api;

import java.util.Locale;

/**
 * Event name matching. Ids are written with underscores ({@code life_death}) but players type
 * them any way they like, so lookups compare a normalised form: case, {@code '-'}, {@code '_'}
 * and whitespace are ignored ("Life-Death", "lifedeath" and "life_death" are the same event).
 */
public final class EventIds {

    private EventIds() {}

    /** @return the normalised lookup key, or an empty string for null/blank input */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT).replaceAll("[-_\\s]", "");
    }

    /** Event ids are lower-case words joined by underscores, e.g. {@code life_death}. */
    public static boolean isValidId(String id) {
        return id != null && id.matches("[a-z][a-z0-9]*(_[a-z0-9]+)*");
    }
}
