package net.rose.elementSMPRefined.util.movement;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Shared helper for abilities that throw their own caster through the air (dashes and springboards).
 * <p>
 * The launch replaces the caster's velocity and wipes the fall distance built up so far.
 */
public final class AbilityLaunch {

    /** Hard cap on launch speed (blocks per tick) so stacked boosts can't send anyone into orbit. */
    private static final double MAX_SPEED = 1.8;

    private AbilityLaunch() {}

    /**
     * Launches {@code player} with {@code velocity}, capped at {@link #MAX_SPEED}.
     */
    public static void launch(Player player, Vector velocity) {
        if (player.getGameMode() == GameMode.SPECTATOR) return;

        Vector v = velocity.clone();
        if (v.lengthSquared() > MAX_SPEED * MAX_SPEED) {
            v.normalize().multiply(MAX_SPEED);
        }

        player.setVelocity(v);
        player.setFallDistance(0f);
    }
}
