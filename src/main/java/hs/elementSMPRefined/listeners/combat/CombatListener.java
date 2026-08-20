package hs.elementSMPRefined.listeners.combat;

import hs.elementSMPRefined.managers.ElementManager;
import hs.elementSMPRefined.managers.TrustManager;
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
 * Trust is bidirectional - either trusting the other player prevents damage.
 */
public class CombatListener implements Listener {
    private final TrustManager trust;
    private final ElementManager elements;

    public CombatListener(TrustManager trust, ElementManager elements) {
        this.trust = trust;
        this.elements = elements;
    }

    /**
     * Prevent damage between trusted players.
     * Runs at HIGHEST priority to ensure this runs before other damage modifiers.
     * 
     * Trust check is bidirectional:
     * - Victim trusts damager OR
     * - Damager trusts victim
     * 
     * This prevents accidental damage in both directions.
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

        // Check bidirectional trust
        if (trust.isTrusted(victim.getUniqueId(), damager.getUniqueId()) || 
            trust.isTrusted(damager.getUniqueId(), victim.getUniqueId())) {
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

