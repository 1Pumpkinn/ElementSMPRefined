package hs.elementSMPRefined.util.time;

public final class TimeUtils {
    public record Expiration(long expiresAt) {
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

        public static Expiration fromNow(long durationMillis) {
            return new Expiration(System.currentTimeMillis() + durationMillis);
        }

        public static Expiration never() {
            return new Expiration(Long.MAX_VALUE);
        }
    }

    public static long millisToTicks(long millis) {
        return millis / 50L;
    }

    public static long ticksToMillis(long ticks) {
        return ticks * 50L;
    }

    public static long secondsToTicks(int seconds) {
        return seconds * 20L;
    }

    public static int ticksToSeconds(long ticks) {
        return (int) (ticks / 20L);
    }

    public static long secondsToMillis(int seconds) {
        return seconds * 1000L;
    }

    public static int millisToSeconds(long millis) {
        return (int) (millis / 1000L);
    }

    private TimeUtils() {}
}

