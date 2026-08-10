package hs.elementSMPRefined.listeners.item;

import hs.elementSMPRefined.managers.ItemManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

public class ElementCombatProjectileListener implements Listener {
	private final hs.elementSMPRefined.managers.ItemManager itemManager;

	public ElementCombatProjectileListener(ItemManager itemManager) {
		this.itemManager = itemManager;
	}

	@EventHandler
	public void onDamage(EntityDamageByEntityEvent event) {
		itemManager.handleDamage(event);
	}

	@EventHandler
	public void onLaunch(ProjectileLaunchEvent event) {
		itemManager.handleLaunch(event);
	}
}

