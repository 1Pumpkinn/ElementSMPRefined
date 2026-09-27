package net.rose.elementSMPRefined.ability.passive.air.listeners;

import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ElementManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Air Element
 *
 * When Air element players with Upgrade 2 hit a player,
 * This helps Air players control the fight vertically and
 * gives enemies a defensive buff if hit.
 */
public class AirCombatListener implements Listener {
    
    private static final int REQUIRED_UPGRADE_LEVEL = 2;
    private static final double PROC_CHANCE = 0.05;  // 5% chance
    private static final int SLOW_FALLING_DURATION_TICKS = 5 * 20;  // 5 seconds
    private static final int SLOW_FALLING_AMPLIFIER = 0;

    private final ElementManager elementManager;

    public AirCombatListener(ElementManager elementManager) {
        this.elementManager = elementManager;
    }


    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (!(event.getDamager() instanceof Player damager)) {
            return;
        }

        var damagerData = elementManager.data(damager.getUniqueId());
        
        // Null safety check
        if (damagerData == null) {
            return;
        }

        // Check Air element with Upgrade 2
        if (damagerData.getCurrentElement() != ElementType.AIR) {
            return;
        }

        if (damagerData.getUpgradeLevel(ElementType.AIR) < REQUIRED_UPGRADE_LEVEL) {
            return;
        }

        // Proc chance roll
        if (Math.random() < PROC_CHANCE) {
            victim.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOW_FALLING,
                    SLOW_FALLING_DURATION_TICKS,
                    SLOW_FALLING_AMPLIFIER,
                    true,  // ambient (no particles)
                    true,  // show particles despite ambient flag
                    true   // show icon
            ));
        }
    }
}
