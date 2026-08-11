package hs.elementSMPRefined.listeners.item;

import hs.elementSMPRefined.managers.ItemManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

/**
 * Handles combat-related element item events including damage and projectile launching
 */
public class ElementCombatListener implements Listener {
    private final ItemManager itemManager;

    public ElementCombatListener(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        itemManager.handleDamage(event);
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        itemManager.handleLaunch(event);
    }
}
