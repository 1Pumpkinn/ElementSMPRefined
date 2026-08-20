package hs.elementSMPRefined.ability.passive.fire.listeners;

import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.managers.ElementManager;
import hs.elementSMPRefined.managers.TrustManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Fire Element Passive 2: Fire Aspect
 * 
 * When attacking enemies, Fire element players with Upgrade 2
 * set targets on fire for a duration.
 * 
 * Does not apply to trusted/allied players.
 */
public class FireCombatListener implements Listener {
    
    private static final int REQUIRED_UPGRADE_LEVEL = 2;
    private static final int FIRE_TICKS = 80;  // 4 seconds at 20 ticks/second
    private static final int FIRE_DURATION_SECONDS = FIRE_TICKS / 20;

    private final ElementManager elementManager;
    private final TrustManager trustManager;

    public FireCombatListener(ElementManager elementManager, TrustManager trustManager) {
        this.elementManager = elementManager;
        this.trustManager = trustManager;
    }

    /**
     * Apply fire to entities hit by Fire element players with Upgrade 2.
     * Respects trust relationships.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Check if damager is a player
        if (!(event.getDamager() instanceof Player damager)) {
            return;
        }

        var playerData = elementManager.data(damager.getUniqueId());
        
        // Null safety check
        if (playerData == null) {
            return;
        }

        // Check if player has Fire element
        if (playerData.getCurrentElement() != ElementType.FIRE) {
            return;
        }

        // Require Upgrade 2 for fire aspect
        if (playerData.getUpgradeLevel(ElementType.FIRE) < REQUIRED_UPGRADE_LEVEL) {
            return;
        }

        // Don't apply fire to trusted players (allies)
        if (event.getEntity() instanceof Player victim) {
            if (trustManager.isTrusted(damager.getUniqueId(), victim.getUniqueId()) ||
                trustManager.isTrusted(victim.getUniqueId(), damager.getUniqueId())) {
                return;
            }
        }

        // Apply fire aspect to target
        event.getEntity().setFireTicks(FIRE_TICKS);
    }
}