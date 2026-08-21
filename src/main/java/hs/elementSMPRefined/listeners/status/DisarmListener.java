package hs.elementSMPRefined.listeners.status;

import hs.elementSMPRefined.status.DisarmManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Enforces {@code WEAPON_DISARM} and {@code MAIN_HAND_DISARM}: cancels melee damage and
 * item use for whichever material {@link DisarmManager} currently has locked for a player.
 * Bukkit's own item cooldown (set by {@link DisarmManager}) only blocks right-click "use"
 * actions and is purely cosmetic for melee - the actual attack block has to happen here.
 */
public class DisarmListener implements Listener {
    private static final Component WEAPON_DISARMED = Component.text(
            "That weapon is disarmed and can't be used!", NamedTextColor.RED);

    private final DisarmManager disarmManager;

    public DisarmListener(DisarmManager disarmManager) {
        this.disarmManager = disarmManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        Material handMaterial = player.getInventory().getItemInMainHand().getType();
        if (handMaterial == Material.AIR) return;

        if (isDisarmed(player, handMaterial)) {
            event.setCancelled(true);
            player.sendActionBar(WEAPON_DISARMED);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        Material handMaterial = player.getInventory().getItemInMainHand().getType();
        if (handMaterial == Material.AIR) return;

        if (isDisarmed(player, handMaterial)) {
            event.setCancelled(true);
            player.sendActionBar(WEAPON_DISARMED);
        }
    }

    private boolean isDisarmed(Player player, Material handMaterial) {
        return disarmManager.isMainHandDisarmed(player, handMaterial)
                || disarmManager.isWeaponDisarmed(player, handMaterial);
    }
}