package net.rose.elementSMPRefined.util.visual;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Latency helpers for the display-entity visuals in {@code util.visual.model}.
 * <p>
 * Two things go wrong for high-ping players:
 * <ol>
 *   <li><b>Lag behind the player.</b> A visual centred on {@code player.getLocation()} is placed
 *   where the <i>server</i> thinks the player is, which trails the client by roughly the ping
 *   (client to server, then the display update back again). While the player moves, the ring /
 *   chain anchor visibly trails behind them. {@link Tracker} estimates the player's velocity and
 *   leads the visual by the ping, so it lands on the player on screen.</li>
 *   <li><b>Jitter.</b> Display entities are teleported / interpolated over 1 tick. If a packet
 *   arrives late the display freezes, if two arrive together it jumps. {@link #smoothingTicks}
 *   hands out a longer interpolation window as ping goes up, so the client has slack to ride out
 *   the jitter.</li>
 * </ol>
 * Players at or below {@link #LOW_PING_MS} get exactly the old behaviour (1 tick, and the lead is
 * effectively zero), so low-ping players see no change.
 * <p>
 * Note: interpolation settings live on the entity and are shared by everyone who can see it, so
 * they follow the <i>owner's</i> ping.
 */
public final class PingCompensation {

    private static final double MS_PER_TICK = 50.0;

    /** At or below this the interpolation window stays at 1 tick - identical to the old behaviour. */
    private static final int LOW_PING_MS = 70;
    private static final int MID_PING_MS = 250;
    private static final int MAX_SMOOTHING_TICKS = 3;

    /** Ping below this is just normal jitter and gets no lead. */
    private static final double LEAD_DEADZONE_MS = 25.0;
    private static final double MAX_LEAD_TICKS = 7.0;
    /** Vertical motion changes direction too fast (jumps) to be predicted as far as horizontal. */
    private static final double VERTICAL_LEAD_FACTOR = 0.5;
    private static final double MAX_YAW_LEAD_DEGREES = 45.0;
    /** How quickly the velocity estimate follows new movement (0..1). */
    private static final double VELOCITY_SMOOTHING = 0.5;
    /** A single-tick move longer than this (blocks, squared) is a teleport, not movement. */
    private static final double TELEPORT_JUMP_SQUARED = 36.0;

    private PingCompensation() {}

    public static int ping(Player player) {
        return player == null ? 0 : Math.max(0, player.getPing());
    }

    /** Interpolation / teleport duration in ticks for this player's ping (1 for low ping, up to 3). */
    public static int smoothingTicks(Player player) {
        return smoothingTicks(player, MAX_SMOOTHING_TICKS);
    }

    /**
     * Same as {@link #smoothingTicks(Player)} but capped. Fast projectiles use a low cap, because
     * every extra tick of smoothing makes the model trail the hit position by another step.
     */
    public static int smoothingTicks(Player player, int cap) {
        if (player == null) return 1;
        int ping = ping(player);
        int ticks = ping <= LOW_PING_MS ? 1 : ping <= MID_PING_MS ? 2 : MAX_SMOOTHING_TICKS;
        return Math.max(1, Math.min(ticks, cap));
    }

    private static double wrapDegrees(double degrees) {
        return ((degrees + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
    }

    /**
     * Follows one player and predicts where (and which way) they are by the time a display
     * update reaches their screen. Call {@link #sample(int)} <b>once per tick</b>.
     */
    public static final class Tracker {

        private final Player player;
        private final double maxLeadBlocks;

        private boolean primed;
        private World lastWorld;
        private double lastX, lastY, lastZ;
        private float lastYaw;

        private double velX, velY, velZ; // blocks per tick, smoothed
        private double yawRate;          // degrees per tick, smoothed

        private final Vector lead = new Vector();
        private float yaw;

        /** @param maxLeadBlocks cap on how far ahead of the player the prediction may reach */
        public Tracker(Player player, double maxLeadBlocks) {
            this.player = player;
            this.maxLeadBlocks = maxLeadBlocks;
        }

        /**
         * @param smoothingTicks the interpolation window the visual is currently using; the extra
         *                       ticks beyond 1 are also lag, so they are folded into the lead
         * @return the player's location moved ahead by the predicted lag (a fresh copy)
         */
        public Location sample(int smoothingTicks) {
            Location now = player.getLocation();
            World world = now.getWorld();

            if (!primed || world != lastWorld) {
                velX = velY = velZ = 0;
                yawRate = 0;
                primed = true;
            } else {
                double dx = now.getX() - lastX;
                double dy = now.getY() - lastY;
                double dz = now.getZ() - lastZ;
                if (dx * dx + dy * dy + dz * dz > TELEPORT_JUMP_SQUARED) {
                    velX = velY = velZ = 0;
                    yawRate = 0;
                } else {
                    velX += (dx - velX) * VELOCITY_SMOOTHING;
                    velY += (dy - velY) * VELOCITY_SMOOTHING;
                    velZ += (dz - velZ) * VELOCITY_SMOOTHING;
                    double dyaw = wrapDegrees(now.getYaw() - lastYaw);
                    yawRate += (dyaw - yawRate) * VELOCITY_SMOOTHING;
                }
            }
            lastWorld = world;
            lastX = now.getX();
            lastY = now.getY();
            lastZ = now.getZ();
            lastYaw = now.getYaw();

            double leadTicks = Math.min(MAX_LEAD_TICKS,
                    Math.max(0.0, ping(player) - LEAD_DEADZONE_MS) / MS_PER_TICK
                            + Math.max(0, smoothingTicks - 1));

            lead.setX(velX * leadTicks);
            lead.setY(velY * leadTicks * VERTICAL_LEAD_FACTOR);
            lead.setZ(velZ * leadTicks);
            double length = lead.length();
            if (length > maxLeadBlocks) {
                lead.multiply(maxLeadBlocks / length);
            }

            double yawLead = Math.max(-MAX_YAW_LEAD_DEGREES, Math.min(MAX_YAW_LEAD_DEGREES, yawRate * leadTicks));
            yaw = now.getYaw() + (float) yawLead;

            return now.add(lead);
        }

        /** The offset applied by the last {@link #sample(int)} (a copy). */
        public Vector lead() {
            return lead.clone();
        }

        /** The player's yaw led by the last {@link #sample(int)} - for things placed "in front of" them. */
        public float yaw() {
            return yaw;
        }
    }
}
