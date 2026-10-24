package net.rose.elementSMPRefined.util.server;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small parsing/formatting helpers shared by the {@code /grace} and
 * {@code /dimension} commands so both accept the same input styles.
 * <p>
 * Durations accept a plain number of seconds ({@code 90}) or any combination
 * of day/hour/minute/second units ({@code 10m}, {@code 1h30m}, {@code 2d}, {@code 1m30s}).
 */
public final class ParseUtil {
    private static final Pattern FULL_DURATION = Pattern.compile("(\\d+[dhms])+");
    private static final Pattern DURATION_PART = Pattern.compile("(\\d+)([dhms])");

    private ParseUtil() {}

    /**
     * Parses a duration into whole seconds. Returns null on anything invalid,
     * negative, overflowing, or (unless {@code allowZero}) zero.
     */
    public static Integer parseSeconds(String raw, boolean allowZero) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim().toLowerCase();
        if (text.isEmpty()) {
            return null;
        }

        long total;
        if (text.chars().allMatch(Character::isDigit)) {
            if (text.length() > 9) {
                return null;
            }
            total = Long.parseLong(text);
        } else if (FULL_DURATION.matcher(text).matches()) {
            total = 0;
            Matcher matcher = DURATION_PART.matcher(text);
            while (matcher.find()) {
                if (matcher.group(1).length() > 9) {
                    return null;
                }
                long amount = Long.parseLong(matcher.group(1));
                total += amount * switch (matcher.group(2).charAt(0)) {
                    case 'd' -> 86_400L;
                    case 'h' -> 3_600L;
                    case 'm' -> 60L;
                    default -> 1L;
                };
                if (total > Integer.MAX_VALUE) {
                    return null;
                }
            }
        } else {
            return null;
        }

        if (total > Integer.MAX_VALUE || (total == 0 && !allowZero)) {
            return null;
        }
        return (int) total;
    }

    /** Parses true/false plus the usual aliases (on/off, yes/no, enable/disable). Returns null if unrecognized. */
    public static Boolean parseBoolean(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase()) {
            case "true", "on", "yes", "y", "enable", "enabled" -> Boolean.TRUE;
            case "false", "off", "no", "n", "disable", "disabled" -> Boolean.FALSE;
            default -> null;
        };
    }

    /** Clock style used for countdowns: {@code 09:59}, or {@code 1:05:03} once an hour or more is involved. */
    public static String formatClock(long seconds) {
        seconds = Math.max(0, seconds);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return "%d:%02d:%02d".formatted(hours, minutes, secs);
        }
        return "%02d:%02d".formatted(minutes, secs);
    }

    /** Readable style used in settings/messages: {@code 1h 5m 3s}, {@code 10m}, {@code 0s}. */
    public static String formatCompact(long seconds) {
        seconds = Math.max(0, seconds);
        if (seconds == 0) {
            return "0s";
        }
        long days = seconds / 86_400;
        long hours = (seconds % 86_400) / 3_600;
        long minutes = (seconds % 3_600) / 60;
        long secs = seconds % 60;

        StringBuilder out = new StringBuilder();
        if (days > 0) out.append(days).append("d ");
        if (hours > 0) out.append(hours).append("h ");
        if (minutes > 0) out.append(minutes).append("m ");
        if (secs > 0) out.append(secs).append("s ");
        return out.toString().trim();
    }
}