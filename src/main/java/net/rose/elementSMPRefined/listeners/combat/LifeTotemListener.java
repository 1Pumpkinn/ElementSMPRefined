package net.rose.elementSMPRefined.listeners.combat;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.rose.elementSMPRefined.ability.main.mythic.life.LifeTotemAbility;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/**
 * Feeds player attacks on a Totem of Life's hitbox into {@link LifeTotemAbility}.
 * <p>
 * The hitbox is an {@code Interaction} entity, which can't take normal damage, so the attack is
 * picked up from whichever event the server fires for it. Both are handled here; the ability ignores
 * the second one for the same swing.
 */
public class LifeTotemListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreAttack(PrePlayerAttackEntityEvent event) {
        if (LifeTotemAbility.handleAttack(event.getPlayer(), event.getAttacked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker
                && LifeTotemAbility.handleAttack(attacker, event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** Right-clicks that land on the hitbox (e.g. eating a golden apple while standing inside the totem). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        LifeTotemAbility.handleUse(event.getPlayer(), event.getRightClicked(), event.getHand());
    }
}
