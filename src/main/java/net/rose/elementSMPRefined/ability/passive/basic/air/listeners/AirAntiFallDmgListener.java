package net.rose.elementSMPRefined.ability.passive.basic.air.listeners;

import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ElementManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

public class AirAntiFallDmgListener implements Listener {
    private final ElementManager elementManager;

    public AirAntiFallDmgListener(ElementManager elementManager) {
        this.elementManager = elementManager;
    }

    private static boolean hasAirPassives(ElementType type) {
        return type == ElementType.AIR || type == ElementType.STORM;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;

        var playerData = elementManager.data(player.getUniqueId());
        if (playerData == null || !hasAirPassives(playerData.getCurrentElement())) return;

        event.setCancelled(true);
    }
}
