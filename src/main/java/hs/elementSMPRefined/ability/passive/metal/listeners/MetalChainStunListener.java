package hs.elementSMPRefined.ability.passive.metal.listeners;

import hs.elementSMPRefined.ElementSMPRefined;
import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

/**
 * Listener for metal chain stun effects.
 * Handles knockback prevention and ensures mobs stay stunned.
 */
public class MetalChainStunListener implements Listener {
    private final ElementSMPRefined plugin;

    public MetalChainStunListener(ElementSMPRefined plugin) {
        this.plugin = plugin;
    }

    /**
     * Prevent stunned mobs from moving (additional safeguard)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityMove(EntityMoveEvent event) {
        LivingEntity entity = event.getEntity();

        // Skip players (handled by StatusEffectListener)
        if (entity instanceof Player) return;

        // Check if mob has AI disabled (stunned by metal chain)
        if (entity instanceof Mob mob && (!mob.isAware() || !mob.hasAI())) {
            // Cancel the movement and zero out velocity
            event.setCancelled(true);
            entity.setVelocity(new Vector(0, 0, 0));
        }
    }

    /**
     * Prevent stunned entities from taking knockback (metal-specific behavior)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;

        // Check if entity is stunned by metal chain (using StatusEffectManager for players)
        if (entity instanceof Player && plugin.getStatusEffectManager().isStunned(entity)) {
            // Metal chain specifically prevents knockback more aggressively
            entity.setVelocity(new Vector(0, 0, 0));
        } else if (entity instanceof Mob mob && (!mob.isAware() || !mob.hasAI())) {
            // Also prevent knockback for stunned mobs
            entity.setVelocity(new Vector(0, 0, 0));
        }
    }
}