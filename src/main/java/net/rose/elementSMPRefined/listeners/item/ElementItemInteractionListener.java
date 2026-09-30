package net.rose.elementSMPRefined.listeners.item;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.util.bukkit.ItemUtil;
import net.rose.elementSMPRefined.util.sound.SoundUtils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Plays a click sound when a player interacts with an element item.
 */
public class ElementItemInteractionListener implements Listener {
    private final ElementSMPRefined plugin;

    public ElementItemInteractionListener(ElementSMPRefined plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null && ItemUtil.isElementItem(plugin, item)) {
            SoundUtils.playTo(event.getPlayer(), SoundUtils.UI.CLICK);
        }
    }
}
