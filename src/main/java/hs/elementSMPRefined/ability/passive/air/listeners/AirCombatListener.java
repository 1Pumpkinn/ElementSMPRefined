package hs.elementSMPRefined.ability.passive.air.listeners;

import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.managers.ElementManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Air Element Passive 2: Slow Falling on Hit
 * 
 * When Air element players with Upgrade 2 hit a player,
 * there's a 5% chance to grant Slow Falling to the victim for 5 seconds.
 * 
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

    /**
     * Apply Slow Falling to hit players with a proc chance.
     * Only triggers for Air element players with Upgrade 2.
     */
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
