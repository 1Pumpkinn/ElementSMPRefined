package hs.elementSMPRefined.ability.passive.metal.listeners;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.status.StatusEffectType;
import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

/**
 * Listener for metal chain stun effects.
 * Enforces stun constraints:
 * - Prevents stunned entities from moving
 * - Removes knockback from stunned entities
 * - Works for both players (via StatusEffectManager) and mobs (via AI disable)
 */
public class MetalChainStunListener implements Listener {
    private static final Vector ZERO_VELOCITY = new Vector(0, 0, 0);

    private final ElementSMPRefined plugin;

    public MetalChainStunListener(JavaPlugin plugin) {
        this.plugin = (ElementSMPRefined) plugin;
    }

    /**
     * Prevent stunned entities from moving.
     * Runs at HIGHEST priority to intercept movement before other listeners.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityMove(EntityMoveEvent event) {
        LivingEntity entity = event.getEntity();

        // Players are handled by StatusEffectListener
        if (entity instanceof Player) {
            return;
        }

        // Mobs marked as stunned (AI disabled) cannot move
        if (entity instanceof Mob mob && !mob.hasAI()) {
            event.setCancelled(true);
            entity.setVelocity(ZERO_VELOCITY);
        }
    }

    /**
     * Prevent stunned entities from being knocked back by damage.
     * This ensures stun immobilization is complete.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }

        boolean isStunned = false;

        // Check if player is stunned via StatusEffectManager
        if (entity instanceof Player player) {
            isStunned = plugin.getStatusEffectManager().hasEffect(player, StatusEffectType.FULL_STUN) ||
                       plugin.getStatusEffectManager().hasEffect(player, StatusEffectType.STUN);
        }
        // Check if mob is stunned (AI disabled = stunned marker)
        else if (entity instanceof Mob mob && !mob.hasAI()) {
            isStunned = true;
        }

        // Remove all knockback from stunned entities
        if (isStunned) {
            entity.setVelocity(ZERO_VELOCITY);
        }
    }
}