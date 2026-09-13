package net.rose.elementSMPRefined.listeners.combat;

import net.rose.elementSMPRefined.managers.TrustManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Listener for preventing damage between trusted players.
 * Supports both direct damage and projectile attacks.
 *
 * Trust is granted/revoked only via {@link TrustManager#addMutualTrust} and
 * {@link TrustManager#removeMutualTrust} everywhere in this codebase, so
 * isTrusted(a, b) and isTrusted(b, a) are always kept in sync - a single
 * direction check below is enough (and avoids a second map lookup on every
 * non-trusted hit, which is the common case in combat). If a one-directional
 * trust grant is ever added elsewhere, this needs to go back to checking
 * both directions.
 */
public class CombatListener implements Listener {
    private final TrustManager trust;

    public CombatListener(TrustManager trust) {
        this.trust = trust;
    }

    /**
     * Prevent damage between trusted players.
     * Runs at HIGHEST priority to ensure this runs before other damage modifiers.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player damager = extractDamager(event);
        if (damager == null || damager.equals(victim)) {
            return;
        }

        if (trust.isTrusted(victim.getUniqueId(), damager.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Extract the player damager from the event.
     * Handles both direct damage and projectile attacks.
     */
    private Player extractDamager(EntityDamageByEntityEvent event) {
        // Direct player-to-player damage
        if (event.getDamager() instanceof Player) {
            return (Player) event.getDamager();
        }

        // Projectile damage (arrows, fireballs, etc.)
        if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                return (Player) projectile.getShooter();
            }
        }

        return null;
    }
}