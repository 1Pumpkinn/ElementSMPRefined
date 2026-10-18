package net.rose.elementSMPRefined.core;

public final class Constants {
    private Constants() {}

    public static final class Timing {
        public static final long TICKS_PER_SECOND = 20L;
        public static final long HALF_SECOND = 10L;
        public static final long TWO_SECONDS = 40L;

        private Timing() {}
    }

    public static final class Health {
        public static final double NORMAL_MAX = 20.0;
        public static final double LIFE_MAX = 30.0;

        private Health() {}
    }

    public static final class Animation {
        public static final int ROLL_STEPS = 16;
        public static final long ROLL_DELAY_TICKS = 3L;

        // Gem orbit shown during a reroll
        public static final int ORBIT_GEMS_PER_ELEMENT = 1;
        public static final int ORBIT_SPIN_TICKS = 50;
        public static final int ORBIT_ELIMINATE_INTERVAL_TICKS = 8;
        public static final int ORBIT_GLIDE_TICKS = 12;
        public static final int ORBIT_HOLD_TICKS = 50;

        private Animation() {}
    }

    public static final class Duration {
        public static final long EARTH_TUNNEL_MS = 10_000L;
        public static final long FROST_PUNCH_READY_MS = 10_000L;
        public static final long FROST_FREEZE_MS = 5_000L;
        public static final long METAL_CHAIN_STUN_MS = 3_000L;

        private Duration() {}
    }

    public static final class Distance {
        public static final double AIR_DASH_RADIUS = 3.0;
        public static final double FIRE_GEYSER_RADIUS = 5.5;
        public static final double LIFE_REGEN_RADIUS = 5.0;

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
        public static final double PORTAL_PUSHBACK_STRENGTH = 0.6;

        private Dimension() {}
    }

    public static final class Warnings {
        public static final long COOLDOWN_MS = 3_000L;

        private Warnings() {}
    }
}