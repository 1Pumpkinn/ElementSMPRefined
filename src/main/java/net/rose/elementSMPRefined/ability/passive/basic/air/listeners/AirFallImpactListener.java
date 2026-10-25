package net.rose.elementSMPRefined.ability.passive.basic.air.listeners;

import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ElementManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Air element Passive 1: no fall damage.
 * Also inherited by Storm (upgraded from Air).
 * (Passive 2, Wind Burst, lives in {@link AirWindBurstListener}.)
 */
public class AirFallImpactListener implements Listener {
    private final ElementManager elementManager;

    public AirFallImpactListener(ElementManager elementManager) {
        this.elementManager = elementManager;
    }

    /** Air, and Storm (upgraded from Air, inherits these passives). */
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
