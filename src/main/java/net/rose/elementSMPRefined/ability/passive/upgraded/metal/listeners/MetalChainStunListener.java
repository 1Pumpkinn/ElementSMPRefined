package net.rose.elementSMPRefined.ability.passive.upgraded.metal.listeners;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.status.StatusEffectType;
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
 * - Removes knockback from stunned entities
 * - Works for both players (via StatusEffectManager) and mobs (via AI disable)
 *
 * Performance note: this used to also cancel {@code EntityMoveEvent} for AI-less mobs. That event
 * fires for every moving non-player entity on the server, every tick, as long as ANY plugin listens
 * to it, and nothing in this plugin disables mob AI any more, so the handler only added overhead.
 */
public class MetalChainStunListener implements Listener {
    private static final Vector ZERO_VELOCITY = new Vector(0, 0, 0);

    private final ElementSMPRefined plugin;

    public MetalChainStunListener(JavaPlugin plugin) {
        this.plugin = (ElementSMPRefined) plugin;
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

        if (entity instanceof Player player) {
            isStunned = plugin.getStatusEffectManager().hasEffect(player, StatusEffectType.FULL_STUN) ||
                       plugin.getStatusEffectManager().hasEffect(player, StatusEffectType.STUN);
        }
        else if (entity instanceof Mob mob && !mob.hasAI()) {
            isStunned = true;
        }

        if (isStunned) {
            entity.setVelocity(ZERO_VELOCITY);
        }
    }
}