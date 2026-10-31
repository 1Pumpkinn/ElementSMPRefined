package net.rose.elementSMPRefined.listeners.status;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.status.DisarmManager;
import net.rose.elementSMPRefined.status.StatusEffectManager;
import net.rose.elementSMPRefined.status.StatusEffectType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;

public class StatusEffectListener implements Listener {

    private final StatusEffectManager statusEffectManager;
    private final DisarmManager disarmManager;

    public StatusEffectListener(ElementSMPRefined plugin) {
        this.statusEffectManager = plugin.getStatusEffectManager();
        this.disarmManager = plugin.getDisarmManager();
    }

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
                return;
            }
        }

        if (event.getFrom().getX() != event.getTo().getX() ||
            event.getFrom().getY() != event.getTo().getY() ||
            event.getFrom().getZ() != event.getTo().getZ()) {
            if (isFullyStunned || isPartiallyStunned) {
                event.setCancelled(true);
                return;
            }

            // Stun / Root / Freeze: they can't move but may still look around. Cancelling the event would
            // also snap their camera back (the rollback restores the old yaw/pitch), so instead keep the
            // old position and carry over the new rotation.
            Location locked = event.getFrom().clone();
            locked.setYaw(event.getTo().getYaw());
            locked.setPitch(event.getTo().getPitch());
            event.setTo(locked);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (statusEffectManager.hasEffect(player, StatusEffectType.FULL_STUN)
                || statusEffectManager.isSilenced(player)
                || (event.getHand() == EquipmentSlot.HAND && isMainHandDisarmed(player))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (statusEffectManager.hasEffect(player, StatusEffectType.FULL_STUN)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        if (statusEffectManager.hasEffect(player, StatusEffectType.FULL_STUN)) {
            event.setCancelled(true);
        }
    }

    // ---- Disarm ----

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && isMainHandDisarmed(player)) {
            event.setCancelled(true);
        }
    }

    /** True if the player is holding something (not air) that a disarm currently locks. */
    private boolean isMainHandDisarmed(Player player) {
        Material hand = player.getInventory().getItemInMainHand().getType();
        if (hand == Material.AIR) return false;
        return disarmManager.isMainHandDisarmed(player, hand)
                || disarmManager.isWeaponDisarmed(player, hand);
    }
}
