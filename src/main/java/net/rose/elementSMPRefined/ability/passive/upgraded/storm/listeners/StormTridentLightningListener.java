package net.rose.elementSMPRefined.ability.passive.upgraded.storm.listeners;

import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StormTridentLightningListener implements Listener {

    private static final long TRACK_TICKS = 40L;

    private final JavaPlugin plugin;
    private final ElementManager elementManager;
    private final TrustManager trustManager;
    private final Map<UUID, UUID> strikes = new HashMap<>();

    public StormTridentLightningListener(JavaPlugin plugin, ElementManager elementManager, TrustManager trustManager) {
        this.plugin = plugin;
        this.elementManager = elementManager;
        this.trustManager = trustManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTridentHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (!(trident.getShooter() instanceof Player thrower)) return;

        var data = elementManager.data(thrower.getUniqueId());
        if (data == null || data.getCurrentElement() != ElementType.STORM) return;
        if (data.getUpgradeLevel(ElementType.STORM) < BaseElement.PASSIVE_2_UPGRADE_LEVEL) return;

        Location at = event.getHitEntity() != null ? event.getHitEntity().getLocation() : trident.getLocation();
        if (at.getWorld() == null) return;

        LightningStrike strike = at.getWorld().strikeLightning(at);
        strike.setCausingPlayer(thrower);

        UUID strikeId = strike.getUniqueId();
        strikes.put(strikeId, thrower.getUniqueId());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> strikes.remove(strikeId), TRACK_TICKS);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStrikeDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LightningStrike strike)) return;
        if (isProtected(strike, event.getEntity().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        extinguishAfterStrike(strike, event.getEntity());
    }

    private void extinguishAfterStrike(LightningStrike strike, Entity victim) {
        if (!strikes.containsKey(strike.getUniqueId())) return;
        boolean wasBurning = victim.getFireTicks() > 1;
        if (wasBurning) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (victim.isValid() && victim.getFireTicks() > 0) victim.setFireTicks(0);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStrikeIgniteEntity(EntityCombustByEntityEvent event) {
        if (event.getCombuster() instanceof LightningStrike strike && strikes.containsKey(strike.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStrikeIgniteBlock(BlockIgniteEvent event) {
        if (event.getCause() != BlockIgniteEvent.IgniteCause.LIGHTNING) return;
        if (event.getIgnitingEntity() instanceof LightningStrike strike && strikes.containsKey(strike.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private boolean isProtected(LightningStrike strike, UUID victimId) {
        UUID thrower = strikes.get(strike.getUniqueId());
        if (thrower == null) return false; // not one of ours
        return thrower.equals(victimId) || trustManager.isTrusted(thrower, victimId);
    }
}
