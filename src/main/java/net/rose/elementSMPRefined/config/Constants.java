package net.rose.elementSMPRefined.config;

public final class Constants {
    private Constants() {}

    public static final class Timing {
        public static final long TICKS_PER_SECOND = 20L;
        public static final long HALF_SECOND = 10L;
        public static final long TWO_SECONDS = 40L;

        private Timing() {}
    }

    public static final class Mana {
        public static final int DEFAULT_MAX = 100;
        public static final int DEFAULT_REGEN = 1;

        private Mana() {}
    }

    public static final class Health {
        public static final double NORMAL_MAX = 20.0;
        public static final double LIFE_MAX = 30.0;

        private Health() {}
    }

    public static final class Animation {
        public static final int ROLL_STEPS = 16;
        public static final long ROLL_DELAY_TICKS = 3L;
        public static final long DOUBLE_TAP_THRESHOLD_MS = 250L;
        public static final long TAP_CHECK_DELAY = 6L;
        public static final long TAP_CLEANUP_DELAY = 2L;

        private Animation() {}
    }

    public static final class Duration {
        public static final long EARTH_TUNNEL_MS = 20_000L;
        public static final long FROST_PUNCH_READY_MS = 10_000L;
        public static final long FROST_FREEZE_MS = 5_000L;
        public static final long FROST_CIRCLE_MS = 10_000L;
        public static final long METAL_CHAIN_STUN_MS = 3_000L;

        private Duration() {}
    }

    public static final class Distance {
        public static final double AIR_DASH_RADIUS = 3.0;
        public static final double FIRE_GEYSER_RADIUS = 5.5;
        public static final double LIFE_REGEN_RADIUS = 5.0;
        public static final double FROST_CIRCLE_RADIUS = 5.0;

        private Distance() {}
    }

    public static final class GracePeriod {
        public static final int DEFAULT_DURATION_SECONDS = 600;
        public static final int DEFAULT_HUNGER_PROTECTION_SECONDS = 300;
        public static final boolean DEFAULT_AUTO_START = false;

        private GracePeriod() {}
    }

    public static final class Dimension {
        public static final boolean DEFAULT_NETHER_DISABLED = true;
        public static final boolean DEFAULT_END_DISABLED = true;
        // Velocity applied to push a player back out of a portal they just got
        // blocked from using. Without this, the player stays inside the portal
        // block and the client keeps re-triggering the portal event every tick.
        public static final double PORTAL_PUSHBACK_STRENGTH = 0.6;

        private Dimension() {}
    }

    public static final class Warnings {
        // Minimum time between repeated warnings (PvP blocked, dimension
        // blocked) to the same player, so standing in one spot spamming an
        // action doesn't spam their chat - or the server - either.
        public static final long COOLDOWN_MS = 3_000L;

        private Warnings() {}
    }
}