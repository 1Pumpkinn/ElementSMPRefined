package hs.elementSMPRefined.listeners.item;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.data.PlayerData;
import hs.elementSMPRefined.elements.ElementType;
import hs.elementSMPRefined.managers.ElementManager;
import hs.elementSMPRefined.util.bukkit.ItemUtil;
import hs.elementSMPRefined.util.visual.SoundUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public class ElementItemPickupListener implements Listener {
	private final ElementSMPRefined plugin;
	private final ElementManager elements;

	public ElementItemPickupListener(ElementSMPRefined plugin, ElementManager elements) {
		this.plugin = plugin;
		this.elements = elements;
	}

	@EventHandler
	public void onPickup(EntityPickupItemEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;
		ItemStack stack = event.getItem().getItemStack();
		if (!ItemUtil.isElementItem(plugin, stack)) return;
		
		// Use the improved API
		Optional<ElementType> typeOpt = ItemUtil.getElementTypeOptional(plugin, stack);
		if (typeOpt.isEmpty()) return;
		ElementType type = typeOpt.get();

		if (type == ElementType.LIFE || type == ElementType.DEATH) {
			return;
		}

		PlayerData playerData = elements.data(player.getUniqueId());
		ElementType oldElement = playerData.getCurrentElement();
		if (oldElement != type) {
			elements.setElement(player, type);
			// Play sound feedback using improved SoundUtils
			SoundUtils.playTo(player, SoundUtils.UI.SELECT);
		}
	}
}

