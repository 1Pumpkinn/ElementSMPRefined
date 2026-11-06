package net.rose.elementSMPRefined.util.bukkit;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Lets movement abilities push through cobwebs without removing or hiding them.
 *
 * <p>While an entity overlaps a cobweb, vanilla multiplies its movement each tick by
 * (0.25, 0.05, 0.25) and then wipes its momentum, so a velocity set by an ability is crushed to a
 * crawl. Dividing the velocity we set by that same multiplier cancels it out: the entity moves at
 * the speed the ability wanted and the web stays exactly as it was.
 */
public final class WebUtil {
    // Vanilla cobweb "stuck speed multiplier".
    private static final double WEB_XZ = 0.25;
    private static final double WEB_Y = 0.05;
    // Safety caps so a boosted velocity can't launch an entity if it leaves the web mid-tick.
    // Vertical needs far more headroom than horizontal: the web's vertical multiplier is 5x
    // harsher, so a cap of 8 only ever allowed 0.4 blocks/tick upward, which is why dashes
    // stalled inside webs.
    private static final double MAX_XZ = 8.0;
    private static final double MAX_Y = 12.0;

    private WebUtil() {}

    /** True if the entity's bounding box is currently touching a cobweb. */
    public static boolean isInWeb(Entity entity) {
        World world = entity.getWorld();
        BoundingBox box = entity.getBoundingBox();
        for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX()); x++) {
            for (int y = (int) Math.floor(box.getMinY());
                    y <= (int) Math.floor(box.getMaxY());
                    y++) {
                for (int z = (int) Math.floor(box.getMinZ());
                        z <= (int) Math.floor(box.getMaxZ());
                        z++) {
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    if (world.getBlockAt(x, y, z).getType() == Material.COBWEB) return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the velocity to actually set so the entity moves at {@code desired} even while
     * inside a cobweb. Outside a web, returns {@code desired} unchanged. Does not modify the
     * input.
     */
    public static Vector compensate(Entity entity, Vector desired) {
        if (!isInWeb(entity)) return desired;
        return new Vector(
                clamp(desired.getX() / WEB_XZ, MAX_XZ),
                clamp(desired.getY() / WEB_Y, MAX_Y),
                clamp(desired.getZ() / WEB_XZ, MAX_XZ));
    }

    /**
     * Moves the entity by (up to) {@code delta} this tick by teleporting it, sliding along solid
     * blocks instead of going through them. Webs have no collision, so this passes straight
     * through them.
     *
     * <p>Velocity can't do this reliably for fast movement: the server re-simulates every client
     * move with the web slowdown applied again, so a boosted velocity makes the client's
     * position disagree with the server's and the player gets rubber-banded back. A server-side
     * teleport is authoritative, so there is nothing to disagree with.
     */
    public static void moveThrough(Entity entity, Vector delta) {
        World world = entity.getWorld();
        BoundingBox box = entity.getBoundingBox().clone();
        Vector moved = new Vector();

        // Y first, then X, then Z - each axis independently so the entity slides along walls.
        double[] axes = {delta.getY(), delta.getX(), delta.getZ()};
        for (int i = 0; i < 3; i++) {
            double amount = axes[i];
            // Back off in halves until the box fits (or give up on this axis).
            for (int attempt = 0; attempt < 5 && Math.abs(amount) > 1.0E-3; attempt++) {
                BoundingBox test = box.clone();
                Vector shift = new Vector();
                if (i == 0) shift.setY(amount);
                else if (i == 1) shift.setX(amount);
                else shift.setZ(amount);
                test.shift(shift);
                if (!world.hasCollisionsIn(test)) {
                    box = test;
                    moved.add(shift);
                    break;
                }
                amount *= 0.5;
            }
        }

        Location target = entity.getLocation().add(moved);
        entity.teleport(target);
    }

    private static double clamp(double v, double max) {
        return Math.max(-max, Math.min(max, v));
    }
}
