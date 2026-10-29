package net.rose.elementSMPRefined.util.scan;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Puts a player down on the ground before they are immobilised (Frost freeze, stuns, roots), so
 * nobody ends up hanging frozen in mid-air.
 */
public final class GroundSettle {

    /** How far below the player to look for ground before giving up (blocks). */
    private static final double MAX_GROUND_SEARCH = 64.0;
    /** Anything further than this above the ground counts as airborne and gets set down (blocks). */
    private static final double AIRBORNE_THRESHOLD = 0.15;

    private GroundSettle() {}

    /**
     * Makes sure the player is standing on the ground: if they're in the air they are put down on
     * the first solid surface beneath them, with their momentum and fall distance cleared and
     * gliding stopped.
     *
     * @return false if there is no ground below the player (the void), in which case nothing was changed
     */
    public static boolean settle(Player player) {
        Location at = player.getLocation();
        // Start a touch above the feet so a player standing inside a carpet/slab edge still finds their floor.
        Location from = at.clone().add(0, 0.1, 0);
        RayTraceResult ground = player.getWorld().rayTraceBlocks(
                from, new Vector(0, -1, 0), MAX_GROUND_SEARCH, FluidCollisionMode.NEVER, true);
        if (ground == null) return false;

        double groundY = ground.getHitPosition().getY();
        boolean airborne = at.getY() - groundY > AIRBORNE_THRESHOLD;

        if (airborne) {
            if (player.isGliding()) player.setGliding(false);
            Location landing = new Location(at.getWorld(), at.getX(), groundY, at.getZ(), at.getYaw(), at.getPitch());
            player.teleport(landing);
        }
        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0f);
        return true;
    }
}
