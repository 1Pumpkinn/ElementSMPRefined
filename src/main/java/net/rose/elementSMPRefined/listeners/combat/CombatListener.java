package net.rose.elementSMPRefined.listeners.combat;

import net.rose.elementSMPRefined.managers.TrustManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class CombatListener implements Listener {
    private final TrustManager trust;

    public CombatListener(TrustManager trust) {
        this.trust = trust;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {

            return;
        }

        Player damager = extractDamager(event);
        if (damager == null || damager.equals(victim)) {
            return;
        }

        if (trust.isTrusted(victim.getUniqueId(), damager.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private Player extractDamager(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }

        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }

        return null;
    }
}