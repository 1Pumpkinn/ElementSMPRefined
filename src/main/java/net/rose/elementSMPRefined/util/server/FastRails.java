package net.rose.elementSMPRefined.util.server;

import net.rose.elementSMPRefined.managers.ConfigManager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rail;
import org.bukkit.entity.Minecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Fast rails, in the style of CivMC railways: a minecart travelling over a rail that has copper
 * blocks directly beneath it (two by default, see {@code fast_rails.copper_blocks_required}) gets
 * a higher speed cap and is gently accelerated up to it, with no powered rails needed. Leaving
 * the copper section puts the cart back to the vanilla speed cap.
 * <p>
 * On ascending rails a cart that is travelling uphill is kept at a minimum speed so it doesn't
 * stall on the climb. A cart that is rolling back down is never pushed, so it can't be driven
 * back and forth across a slope.
 * <p>
 * Every copper block variant counts (plain, exposed, weathered, oxidized and all waxed forms), so
 * a railway doesn't stop working as the copper ages.
 */
public class FastRails implements Listener {

    /** Vanilla minecart speed cap, in blocks per tick. */
    private static final double VANILLA_MAX_SPEED = 0.4;

    /** Below this horizontal speed a cart is treated as stopped and is not pushed. */
    private static final double MIN_PUSH_SPEED = 0.05;

    /** Same, on a slope, where a cart that is still creeping up should be kept going. */
    private static final double SLOPE_MIN_PUSH_SPEED = 0.02;

    /** Debug lines per cart are at most this many ticks apart. */
    private static final long DEBUG_INTERVAL_TICKS = 10L;

    private static final Set<Material> COPPER_BLOCKS =
            EnumSet.of(
                    Material.COPPER_BLOCK,
                    Material.EXPOSED_COPPER,
                    Material.WEATHERED_COPPER,
                    Material.OXIDIZED_COPPER,
                    Material.WAXED_COPPER_BLOCK,
                    Material.WAXED_EXPOSED_COPPER,
                    Material.WAXED_WEATHERED_COPPER,
                    Material.WAXED_OXIDIZED_COPPER);

    private final ConfigManager config;

    /** Carts whose speed cap this class raised, so only those are ever put back to vanilla. */
    private final Set<UUID> boosted = new HashSet<>();

    /** Last server tick a debug line was printed for each cart (only used while debugging). */
    private final Map<UUID, Integer> lastDebugTick = new HashMap<>();

    public FastRails(ConfigManager config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Minecart cart)) return;

        if (!config.isFastRailsEnabled()) {
            restore(cart);
            return;
        }

        Block rail = fastRailAt(event.getTo(), config.getFastRailsCopperBlocksRequired());
        if (rail == null) {
            debug(cart, event.getTo(), "no fast rail here (not a rail, or copper missing below it)");
            restore(cart);
            return;
        }

        double maxSpeed = Math.max(VANILLA_MAX_SPEED, config.getFastRailsMaxSpeed());
        cart.setMaxSpeed(maxSpeed);
        boosted.add(cart.getUniqueId());

        Vector uphill = uphillDirection(rail);
        if (uphill != null) {
            double floor = Math.min(maxSpeed, config.getFastRailsSlopeMinSpeed());
            climb(cart, maxSpeed, config.getFastRailsAcceleration(), floor, uphill);
        } else {
            accelerate(cart, maxSpeed, config.getFastRailsAcceleration());
        }
        debug(cart, event.getTo(), "fast rail " + rail.getBlockData().getAsString()
                + (uphill != null ? " (slope)" : ""));
    }

    @EventHandler
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        boosted.remove(event.getVehicle().getUniqueId());
        lastDebugTick.remove(event.getVehicle().getUniqueId());
    }

    /** The rail block at {@code at} if it has {@code required} copper blocks right under it, else null. */
    private static Block fastRailAt(Location at, int required) {
        Block rail = at.getBlock();
        if (!Tag.RAILS.isTagged(rail.getType())) return null;

        Block below = rail;
        for (int i = 0; i < required; i++) {
            below = below.getRelative(BlockFace.DOWN);
            if (!COPPER_BLOCKS.contains(below.getType())) return null;
        }
        return rail;
    }

    /** Horizontal direction that is uphill on an ascending rail, or null if the rail is not a slope. */
    private static Vector uphillDirection(Block rail) {
        BlockData data = rail.getBlockData();
        if (!(data instanceof Rail railData)) return null;
        return switch (railData.getShape()) {
            case ASCENDING_EAST -> new Vector(1, 0, 0);
            case ASCENDING_WEST -> new Vector(-1, 0, 0);
            case ASCENDING_NORTH -> new Vector(0, 0, -1);
            case ASCENDING_SOUTH -> new Vector(0, 0, 1);
            default -> null;
        };
    }

    /** Pushes a moving cart along its current heading, without ever exceeding {@code maxSpeed}. */
    private static void accelerate(Minecart cart, double maxSpeed, double acceleration) {
        if (acceleration <= 0) return;

        Vector velocity = cart.getVelocity();
        double speed = Math.hypot(velocity.getX(), velocity.getZ());
        if (speed < MIN_PUSH_SPEED || speed >= maxSpeed) return;

        double factor = Math.min(1.0 + acceleration, maxSpeed / speed);
        cart.setVelocity(new Vector(velocity.getX() * factor, velocity.getY(), velocity.getZ() * factor));
    }

    /**
     * On an ascending rail: speeds a cart that is heading uphill up to at least {@code floor}
     * (never past {@code maxSpeed}). A cart that is stopped or rolling downhill is left alone.
     */
    private static void climb(
            Minecart cart, double maxSpeed, double acceleration, double floor, Vector uphill) {
        Vector velocity = cart.getVelocity();
        double speed = Math.hypot(velocity.getX(), velocity.getZ());
        if (speed < SLOPE_MIN_PUSH_SPEED || speed >= maxSpeed) return;

        double alongUphill = velocity.getX() * uphill.getX() + velocity.getZ() * uphill.getZ();
        if (alongUphill <= 0) return; // rolling back down, don't push it

        double target = Math.min(maxSpeed, Math.max(speed * (1.0 + Math.max(0.0, acceleration)), floor));
        double factor = target / speed;
        if (factor <= 1.0) return;
        // y is scaled too, so the cart keeps following the incline instead of being pulled off it
        cart.setVelocity(velocity.clone().multiply(factor));
    }

    private void restore(Minecart cart) {
        if (boosted.remove(cart.getUniqueId())) {
            cart.setMaxSpeed(VANILLA_MAX_SPEED);
        }
    }

    private void debug(Minecart cart, Location at, String what) {
        if (!config.isFastRailsDebug()) return;
        int now = Bukkit.getCurrentTick();
        Integer last = lastDebugTick.get(cart.getUniqueId());
        if (last != null && now - last < DEBUG_INTERVAL_TICKS) return;
        lastDebugTick.put(cart.getUniqueId(), now);

        Block b = at.getBlock();
        Vector v = cart.getVelocity();
        Bukkit.getLogger().info(String.format(
                "[FastRails] cart at %d,%d,%d on %s | %s | vel %.3f,%.3f,%.3f | maxSpeed %.2f | below: %s, %s",
                b.getX(), b.getY(), b.getZ(), b.getType(), what,
                v.getX(), v.getY(), v.getZ(), cart.getMaxSpeed(),
                b.getRelative(BlockFace.DOWN).getType(),
                b.getRelative(BlockFace.DOWN).getRelative(BlockFace.DOWN).getType()));
    }
}
