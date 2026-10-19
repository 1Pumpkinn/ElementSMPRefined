package net.rose.elementSMPRefined.listeners.status;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.status.StatusEffectManager;
import net.rose.elementSMPRefined.status.StatusEffectType;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Listener for handling status effect mechanics like stun, freeze, etc.
 * Cancels appropriate events based on player's active status effects.
 * 
 * Performance note: PlayerMoveEvent fires frequently, so checks are minimized by:
 * - Only performing detailed checks if player has any restrictive effect
 * - Caching effect name lookups to avoid repeated queries
 */
public class StatusEffectListener implements Listener {
    
    private static final Component FULLY_STUNNED_MOVE_LOOK = Component.text("You are fully stunned and cannot move or look!");
    private static final Component PARTIALLY_STUNNED_MOVE_LOOK = Component.text("You are partially stunned and cannot move or look!");
    private static final Component FULLY_STUNNED_MOVE = Component.text("You are fully stunned and cannot move!");
    private static final Component PARTIALLY_STUNNED_MOVE = Component.text("You are partially stunned and cannot move!");
    private static final Component STUNNED_MOVE = Component.text("You are stunned and cannot move!");
    private static final Component ROOTED_MOVE = Component.text("You are rooted and cannot move!");
    private static final Component FROZEN_MOVE = Component.text("You are frozen and cannot move!");
    private static final Component FULLY_STUNNED_INTERACT = Component.text("You are fully stunned and cannot interact!");
    private static final Component SILENCED_INTERACT = Component.text("You are silenced and cannot interact!");
    private static final Component FULLY_STUNNED_BREAK = Component.text("You are fully stunned and cannot break blocks!");
    private static final Component FULLY_STUNNED_PLACE = Component.text("You are fully stunned and cannot place blocks!");

    private final StatusEffectManager statusEffectManager;

    public StatusEffectListener(ElementSMPRefined plugin) {
        this.statusEffectManager = plugin.getStatusEffectManager();
    }

    /**
     * Cancel movement for fully stunned, partially stunned, stunned, rooted, or frozen players.
     * Full stun and partial stun also prevent head movement.
     * 
     * Performance optimization: Early exit if player has no restrictive effects.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // This fires for every step of every player; almost nobody has an effect, so do ONE map
        // lookup and leave instead of five.
        if (!statusEffectManager.hasAnyEffect(player)) return;

        boolean isFullyStunned = statusEffectManager.hasEffect(player, StatusEffectType.FULL_STUN);
        boolean isPartiallyStunned = statusEffectManager.hasEffect(player, StatusEffectType.PARTIAL_STUN);
        boolean isStunned = statusEffectManager.hasEffect(player, StatusEffectType.STUN);
        boolean isRooted = statusEffectManager.hasEffect(player, StatusEffectType.ROOT);
        boolean isFrozen = statusEffectManager.hasEffect(player, StatusEffectType.FREEZE);

        if (!isFullyStunned && !isPartiallyStunned && !isStunned && !isRooted && !isFrozen) {
            return;
        }

        if (isFullyStunned || isPartiallyStunned) {
            if (event.getFrom().getYaw() != event.getTo().getYaw() ||
                event.getFrom().getPitch() != event.getTo().getPitch()) {
                event.setCancelled(true);
                Component message = isFullyStunned ? FULLY_STUNNED_MOVE_LOOK : PARTIALLY_STUNNED_MOVE_LOOK;
                player.sendActionBar(message);
                return;
            }
        }

        if (event.getFrom().getX() != event.getTo().getX() ||
            event.getFrom().getY() != event.getTo().getY() ||
            event.getFrom().getZ() != event.getTo().getZ()) {
            event.setCancelled(true);
            Component message = isFullyStunned ? FULLY_STUNNED_MOVE :
                            isPartiallyStunned ? PARTIALLY_STUNNED_MOVE :
                            isStunned ? STUNNED_MOVE :
                            isRooted ? ROOTED_MOVE : FROZEN_MOVE;
            player.sendActionBar(message);
        }
    }


    /**
     * Cancel interactions for fully stunned and silenced players.
     * Other stun types allow interactions since they don't affect hands.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        if (statusEffectManager.hasEffect(player, StatusEffectType.FULL_STUN)) {
            event.setCancelled(true);
            player.sendActionBar(FULLY_STUNNED_INTERACT);
        } else if (statusEffectManager.isSilenced(player)) {
            event.setCancelled(true);
            player.sendActionBar(SILENCED_INTERACT);
        }
    }

    /**
     * Cancel block breaking for fully stunned players.
     * Other stun types allow block breaking since they preserve hand control.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (statusEffectManager.hasEffect(player, StatusEffectType.FULL_STUN)) {
            event.setCancelled(true);
            player.sendActionBar(FULLY_STUNNED_BREAK);
        }
    }

    /**
     * Cancel block placing for fully stunned players.
     * Other stun types allow block placement since they preserve hand control.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        if (statusEffectManager.hasEffect(player, StatusEffectType.FULL_STUN)) {
            event.setCancelled(true);
            player.sendActionBar(FULLY_STUNNED_PLACE);
        }
    }
}