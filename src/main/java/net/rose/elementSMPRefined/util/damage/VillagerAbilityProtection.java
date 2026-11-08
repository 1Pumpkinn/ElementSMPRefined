package net.rose.elementSMPRefined.util.damage;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Safety net behind {@link AbilityTargets}: any ability damage that still reaches a villager or
 * wandering trader (an ability with its own target filter, a stray hit box) is cancelled.
 * Runs before {@link TrueDamage} so the damage never gets applied.
 */
public final class VillagerAbilityProtection implements Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (AbilityTargets.isProtected(event.getEntity())
                && AbilityDamage.isAbilityDamage(event.getEntity())) {
            event.setCancelled(true);
            AbilityDamage.markBlocked(event.getEntity());
        }
    }
}
