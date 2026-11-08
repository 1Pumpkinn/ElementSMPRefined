package net.rose.elementSMPRefined.ability.passive.mythic.death.listeners;

import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.managers.TeamManager;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class DeathKillHealListener implements Listener {

    private static final double HEARTS_RESTORED = 3.0;
    private static final double HEAL_AMOUNT = HEARTS_RESTORED * 2.0;

    private final ElementManager elementManager;
    private final TeamManager teamManager;

    public DeathKillHealListener(ElementManager elementManager, TeamManager teamManager) {
        this.elementManager = elementManager;
        this.teamManager = teamManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim) || !killer.isOnline() || killer.isDead()) {
            return;
        }

        var playerData = elementManager.data(killer.getUniqueId());
        if (playerData == null || playerData.getCurrentElement() != ElementType.DEATH) return;

        if (playerData.getUpgradeLevel(ElementType.DEATH) < BaseElement.PASSIVE_2_UPGRADE_LEVEL)
            return;

        if (teamManager.isFriendly(killer.getUniqueId(), victim.getUniqueId())) return;

        AttributeInstance maxHealth = killer.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth != null ? maxHealth.getValue() : 20.0;
        killer.setHealth(Math.min(max, killer.getHealth() + HEAL_AMOUNT));
    }
}
