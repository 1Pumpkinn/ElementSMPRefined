package net.rose.elementSMPRefined.ability.passive.upgraded.lava.listeners;

import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Lava Element Passive 3: Desperate Fury
 *
 * While a Lava player is at 4 hearts (8 HP) or below, their melee hits deal
 * 15% extra damage. Deliberately small - it rewards fighting on low health
 * without turning a near-dead Lava player into a glass cannon.
 *
 * Does not affect trusted players or the attacker's own damage.
 */
public class LavaIncreaseDamageListener implements Listener {

    private static final double HEALTH_THRESHOLD = 8.0; // 4 hearts
    private static final double DAMAGE_MULTIPLIER = 1.15; // +15%

    private final ElementManager elementManager;
    private final TrustManager trustManager;

    public LavaIncreaseDamageListener(ElementManager elementManager, TrustManager trustManager) {
        this.elementManager = elementManager;
        this.trustManager = trustManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        // Direct melee only - projectiles and abilities don't count
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (attacker.equals(event.getEntity())) return;

        if (attacker.getHealth() > HEALTH_THRESHOLD) return;

        var data = elementManager.data(attacker.getUniqueId());
        if (data == null || data.getCurrentElement() != ElementType.LAVA) return;

        if (event.getEntity() instanceof Player victim
                && trustManager.isTrusted(attacker.getUniqueId(), victim.getUniqueId())) {
            return;
        }

        event.setDamage(event.getDamage() * DAMAGE_MULTIPLIER);
    }
}