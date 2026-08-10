package hs.elementSMPRefined.listeners;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.status.StatusEffectManager;
import hs.elementSMPRefined.status.StatusEffectType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Listener for handling status effect mechanics like stun, freeze, etc.
 * Cancels appropriate events based on player's active status effects.
 */
public class StatusEffectListener implements Listener {
    private final StatusEffectManager statusEffectManager;

    public StatusEffectListener(ElementSMPRefined plugin) {
        this.statusEffectManager = plugin.getStatusEffectManager();
    }

    /**
     * Cancel movement for fully stunned, partially stunned, stunned, rooted, or frozen players
     * Full stun and partial stun also prevent head movement
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        boolean isFullyStunned = statusEffectManager.hasEffect(player, StatusEffectType.FULL_STUN);
        boolean isPartiallyStunned = statusEffectManager.hasEffect(player, StatusEffectType.PARTIAL_STUN);
        boolean isStunned = statusEffectManager.hasEffect(player, StatusEffectType.STUN);
        boolean isRooted = statusEffectManager.hasEffect(player, StatusEffectType.ROOT);
        boolean isFrozen = statusEffectManager.hasEffect(player, StatusEffectType.FREEZE);

        if (isFullyStunned || isPartiallyStunned || isStunned || isRooted || isFrozen) {
            // Full stun and partial stun prevent head movement too
            if (isFullyStunned || isPartiallyStunned) {
                if (event.getFrom().getYaw() != event.getTo().getYaw() ||
                    event.getFrom().getPitch() != event.getTo().getPitch()) {
                    event.setCancelled(true);
                    String message = isFullyStunned ? "fully stunned" : "partially stunned";
                    player.sendActionBar("§cYou are " + message + " and cannot move or look!");
                    return;
                }
            }

            // All stun types prevent physical movement
            if (event.getFrom().getX() != event.getTo().getX() ||
                event.getFrom().getY() != event.getTo().getY() ||
                event.getFrom().getZ() != event.getTo().getZ()) {
                event.setCancelled(true);
                String message = isFullyStunned ? "fully stunned" :
                                isPartiallyStunned ? "partially stunned" :
                                isStunned ? "stunned" :
                                isRooted ? "rooted" : "frozen";
                player.sendActionBar("§cYou are " + message + " and cannot move!");
            }
        }
    }

    /**
     * Cancel interactions for fully stunned players
     * Partially stunned, stunned, and rooted players can still interact
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (statusEffectManager.hasEffect(player, StatusEffectType.FULL_STUN) || statusEffectManager.isSilenced(player)) {
            event.setCancelled(true);
            String message = statusEffectManager.hasEffect(player, StatusEffectType.FULL_STUN) ? 
                            "fully stunned" : "silenced";
            player.sendActionBar("§cYou are " + message + " and cannot interact!");
        }
    }

    /**
     * Cancel block breaking for fully stunned players
     * Partially stunned, stunned, and rooted players can still break blocks
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (statusEffectManager.hasEffect(player, StatusEffectType.FULL_STUN)) {
            event.setCancelled(true);
            player.sendActionBar("§cYou are fully stunned and cannot break blocks!");
        }
    }

    /**
     * Cancel block placing for fully stunned players
     * Partially stunned, stunned, and rooted players can still place blocks
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        if (statusEffectManager.hasEffect(player, StatusEffectType.FULL_STUN)) {
            event.setCancelled(true);
            player.sendActionBar("§cYou are fully stunned and cannot place blocks!");
        }
    }

    /**
     * Reduce damage for stunned players (they're defenseless)
     * REMOVED: Stunned players no longer take extra damage
     */
    /*
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (statusEffectManager.hasEffect(player, StatusEffectType.FULL_STUN) ||
            statusEffectManager.hasEffect(player, StatusEffectType.PARTIAL_STUN) ||
            statusEffectManager.hasEffect(player, StatusEffectType.STUN) ||
            statusEffectManager.hasEffect(player, StatusEffectType.ROOT)) {
            // Stunned players take 50% more damage
            event.setDamage(event.getDamage() * 1.5);
        }
    }
    */

    /**
     * Helper method to get the current status effect name for messages
     */
    private String getStatusEffectName(Player player) {
        if (statusEffectManager.hasEffect(player, StatusEffectType.FULL_STUN)) {
            return "fully stunned";
        } else if (statusEffectManager.hasEffect(player, StatusEffectType.PARTIAL_STUN)) {
            return "partially stunned";
        } else if (statusEffectManager.hasEffect(player, StatusEffectType.STUN)) {
            return "stunned";
        } else if (statusEffectManager.hasEffect(player, StatusEffectType.ROOT)) {
            return "rooted";
        } else if (statusEffectManager.hasEffect(player, StatusEffectType.FREEZE)) {
            return "frozen";
        } else if (statusEffectManager.isSilenced(player)) {
            return "silenced";
        }
        return "affected";
    }
}