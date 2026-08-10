package hs.elementSMPRefined.config;

public final class MetadataKeys {
    private MetadataKeys() {}

    public static final class Earth {
        public static final String MINE_UNTIL = "earth_mine_until";
        public static final String CHARM_NEXT_UNTIL = "earth_charm_next";
        public static final String TUNNELING = "earth_tunneling";
        public static final String CHARMED_OWNER = "earth_charmed_owner";
        public static final String CHARMED_UNTIL = "earth_charmed_until";

        private Earth() {}
    }

    public static final class Frost {
        public static final String FROZEN_PUNCH_READY = "frost_frozen_punch_ready";
        public static final String FROZEN = "frost_frozen";
        public static final String CIRCLE_FROZEN = "frost_freezing_circle";

        private Frost() {}
    }

    public static final class Metal {
        public static final String CHAIN_STUN = "metal_chain_stunned";

        private Metal() {}
    }

    public static final class Death {
        public static final String SUMMONED_OWNER = "death_summoned_owner";
        public static final String SUMMONED_UNTIL = "death_summoned_until";

        private Death() {}
    }

    public static final class Air {
        private Air() {}
    }

    public static final class Water {
        private Water() {}
    }

    public static final class Fire {
        private Fire() {}
    }

    public static final class Life {
        private Life() {}
    }
}

