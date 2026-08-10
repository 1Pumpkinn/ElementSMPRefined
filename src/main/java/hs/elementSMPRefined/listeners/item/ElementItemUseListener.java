package hs.elementSMPRefined.listeners.item;

import hs.elementSMPRefined.managers.ElementManager;
import hs.elementSMPRefined.managers.ItemManager;
import hs.elementSMPRefined.util.bukkit.ItemUtil;
import hs.elementSMPRefined.util.visual.SoundUtils;
import hs.elementSMPRefined.ElementSMPRefined;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public class ElementItemUseListener implements Listener {
	private final ElementSMPRefined plugin;
	private final ElementManager elements;
	private final ItemManager itemManager;

	public ElementItemUseListener(ElementSMPRefined plugin, ElementManager elements, ItemManager itemManager) {
		this.plugin = plugin;
		this.elements = elements;
		this.itemManager = itemManager;
	}

	private boolean isElementItem(ItemStack stack) {
		return ItemUtil.isElementItem(plugin, stack);
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onInteract(PlayerInteractEvent event) {
		ItemStack inHand = event.getItem();
		if (inHand != null && isElementItem(inHand)) {
			// Play sound feedback using improved SoundUtils
			SoundUtils.playTo(event.getPlayer(), SoundUtils.UI.CLICK);
			
			if (hs.elementSMPRefined.items.CoreConsumptionHandler.handleCoreConsume(event, plugin, elements)) return;
			itemManager.handleUse(event);
		}
	}
}

