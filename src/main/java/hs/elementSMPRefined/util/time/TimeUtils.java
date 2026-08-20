package hs.elementSMPRefined.util.time;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enhanced time utilities with formatting, parsing, and comprehensive time conversions.
 * Provides human-readable time formats and flexible time string parsing.
 */
public final class TimeUtils {

    /**
     * Represents a time expiration with validation and utility methods
     */
    public record Expiration(long expiresAt) {
        public Expiration {
            if (expiresAt < 0) {
                throw new IllegalArgumentException("Expiration time cannot be negative");
            }
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }

        public boolean isActive() {
            return !isExpired();
        }

        public long remainingMillis() {
            long remaining = expiresAt - System.currentTimeMillis();
            return Math.max(0, remaining);
        }

        public long remainingSeconds() {
            return remainingMillis() / 1000;
        }

        public long remainingMinutes() {
            return remainingSeconds() / 60;
        }

        public String getRemainingTimeString() {
            return formatDuration(remainingMillis());
        }

        public static Expiration fromNow(long durationMillis) {
            return new Expiration(System.currentTimeMillis() + durationMillis);
        }

        public static Expiration fromSeconds(int seconds) {
            return fromNow(seconds * 1000L);
        }

        public static Expiration fromMinutes(int minutes) {
            return fromSeconds(minutes * 60);
        }

        public static Expiration fromHours(int hours) {
            return fromMinutes(hours * 60);
        }

        public static Expiration never() {
            return new Expiration(Long.MAX_VALUE);
        }
    }

    /**
     * Convert milliseconds to ticks (1 tick = 50ms)
     */
    public static long millisToTicks(long millis) {
        return millis / 50L;
    }

    /**
     * Convert ticks to milliseconds
     */
    public static long ticksToMillis(long ticks) {
        return ticks * 50L;
    }

    /**
     * Convert seconds to ticks (1 second = 20 ticks)
     */
    public static long secondsToTicks(int seconds) {
        return seconds * 20L;
    }

    /**
     * Convert ticks to seconds
     */
    public static int ticksToSeconds(long ticks) {
        return (int) (ticks / 20L);
    }

    /**
     * Convert seconds to milliseconds
     */
    public static long secondsToMillis(int seconds) {
        return seconds * 1000L;
    }

    /**
     * Convert milliseconds to seconds
     */
    public static int millisToSeconds(long millis) {
        return (int) (millis / 1000L);
    }

    /**
     * Convert minutes to milliseconds
     */
    public static long minutesToMillis(int minutes) {
        return minutes * 60L * 1000L;
    }

    /**
     * Convert hours to milliseconds
     */
    public static long hoursToMillis(int hours) {
        return hours * 60L * 60L * 1000L;
    }

    /**
     * Convert days to milliseconds
     */
    public static long daysToMillis(int days) {
        return days * 24L * 60L * 60L * 1000L;
    }

    /**
     * Format duration in milliseconds to human-readable string
     * Examples: "1h 30m 45s", "2m 30s", "45s"
     */
    public static String formatDuration(long millis) {
        if (millis < 0) return "0s";

        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        seconds %= 60;
        minutes %= 60;
        hours %= 24;

        StringBuilder sb = new StringBuilder();

        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0 || sb.length() == 0) {
            sb.append(seconds).append("s");
        }

        return sb.toString().trim();
    }

    /**
     * Format duration in milliseconds to detailed string
     * Examples: "1h 30m 45s 123ms", "2m 30s 500ms"
     */
    public static String formatDurationDetailed(long millis) {
        if (millis < 0) return "0ms";

        long milliseconds = millis % 1000;
        long seconds = (millis / 1000) % 60;
        long minutes = (millis / (1000 * 60)) % 60;
        long hours = (millis / (1000 * 60 * 60)) % 24;
        long days = millis / (1000 * 60 * 60 * 24);

        StringBuilder sb = new StringBuilder();

        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0 || sb.length() == 0) {
            sb.append(seconds).append("s ");
        }
        if (milliseconds > 0 || sb.length() == 0) {
            sb.append(milliseconds).append("ms");
        }

        return sb.toString().trim();
    }

    /**
     * Format duration in seconds to "MM:SS" format
     */
    public static String formatMMSS(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    /**
     * Format duration in seconds to "HH:MM:SS" format
     */
    public static String formatHHMMSS(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    /**
     * Format timestamp to readable date string
     */
    public static String formatDate(long timestamp) {
        return formatDate(timestamp, "yyyy-MM-dd HH:mm:ss");
    }

    /**
     * Format timestamp to custom date string
     */
    public static String formatDate(long timestamp, String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        return sdf.format(new Date(timestamp));
    }

    /**
     * Format current time to readable string
     */
    public static String formatCurrentTime() {
        return formatDate(System.currentTimeMillis());
    }

    /**
     * Parse time string to milliseconds
     * Supports formats: "1h 30m", "2m 30s", "45s", "1h30m", "90s"
     */
    public static long parseDuration(String timeString) {
        if (timeString == null || timeString.trim().isEmpty()) {
            return 0;
        }

        long totalMillis = 0;

        // Pattern to match time components (e.g., "1h", "30m", "45s")
        Pattern pattern = Pattern.compile("(\\d+)([dhms])", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(timeString);

        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2).toLowerCase();

            switch (unit) {
                case "d":
                    totalMillis += daysToMillis(value);
                    break;
                case "h":
                    totalMillis += hoursToMillis(value);
                    break;
                case "m":
                    totalMillis += minutesToMillis(value);
                    break;
                case "s":
                    totalMillis += secondsToMillis(value);
                    break;
            }
        }

        return totalMillis;
    }

    /**
     * Parse time string to seconds
     */
    public static int parseDurationSeconds(String timeString) {
        return millisToSeconds(parseDuration(timeString));
    }

    /**
     * Parse simple number to milliseconds (assumes seconds)
     */
    public static long parseSimple(String timeString) {
        try {
            int seconds = Integer.parseInt(timeString.trim());
            return secondsToMillis(seconds);
        } catch (NumberFormatException e) {
            return parseDuration(timeString);
        }
    }

    /**
     * Get current time in milliseconds
     */
    public static long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * Get current time in seconds
     */
    public static long currentTimeSeconds() {
        return System.currentTimeMillis() / 1000;
    }

    /**
     * Check if a timestamp is within the last given duration
     */
    public static boolean isWithin(long timestamp, long durationMillis) {
        return (System.currentTimeMillis() - timestamp) <= durationMillis;
    }

    /**
     * Get elapsed time since timestamp in milliseconds
     */
    public static long elapsedSince(long timestamp) {
        return System.currentTimeMillis() - timestamp;
    }

    /**
     * Get elapsed time since timestamp in human-readable format
     */
    public static String elapsedSinceFormatted(long timestamp) {
        return formatDuration(elapsedSince(timestamp));
    }

    /**
     * Get time until timestamp in milliseconds
     */
    public static long timeUntil(long timestamp) {
        return timestamp - System.currentTimeMillis();
    }

    /**
     * Get time until timestamp in human-readable format
     */
    public static String timeUntilFormatted(long timestamp) {
        return formatDuration(timeUntil(timestamp));
    }

    /**
     * Convert TimeUnit to milliseconds
     */
    public static long toMillis(long duration, TimeUnit unit) {
        return unit.toMillis(duration);
    }

    /**
     * Convert milliseconds to TimeUnit
     */
    public static long fromMillis(long millis, TimeUnit unit) {
        return unit.convert(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * Sleep for the specified duration (for use in async tasks)
     */
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private TimeUtils() {}
}

