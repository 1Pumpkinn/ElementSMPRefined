package hs.elementSMPRefined.ability.passive.metal.listeners;

import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.managers.ElementManager;
import hs.elementSMPRefined.managers.TrustManager;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Metal Element Passive 2: Arrow Immunity
 * 
 * Metal element players with Upgrade 2 are completely immune to arrow damage.
 * This provides a strong defensive benefit and encourages ranged counterplay.
 * 
 * Does not block arrows from trusted/allied players.
 */
public class MetalArrowImmunityListener implements Listener {
    
    private static final int REQUIRED_UPGRADE_LEVEL = 2;

    private final ElementManager elementManager;
    private final TrustManager trustManager;

    public MetalArrowImmunityListener(ElementManager elementManager, TrustManager trustManager) {
        this.elementManager = elementManager;
        this.trustManager = trustManager;
    }

    /**
     * Block arrow damage to Metal element players with Upgrade 2.
     * Respects trust relationships (allows friendly fire).
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onArrowDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (!(event.getDamager() instanceof Arrow arrow)) {
            return;
        }

        var victimData = elementManager.data(victim.getUniqueId());
        
        // Null safety check
        if (victimData == null) {
            return;
        }

        // Check Metal element with Upgrade 2
        if (victimData.getCurrentElement() != ElementType.METAL) {
            return;
        }

        if (victimData.getUpgradeLevel(ElementType.METAL) < REQUIRED_UPGRADE_LEVEL) {
            return;
        }

        // Check if arrow was shot by a trusted player (friendly fire)
        if (arrow.getShooter() instanceof Player shooter) {
            if (trustManager.isTrusted(victim.getUniqueId(), shooter.getUniqueId()) ||
                trustManager.isTrusted(shooter.getUniqueId(), victim.getUniqueId())) {
                return;  // Allow friendly arrows
            }
        }

        // Block the arrow
        event.setCancelled(true);
    }
}