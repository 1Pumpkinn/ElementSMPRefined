package hs.elementSMPRefined.items.api;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.elements.ElementType;
import hs.elementSMPRefined.managers.ConfigManager;
import hs.elementSMPRefined.managers.ManaManager;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public interface ElementItem {
    ElementType getElementType();

    ItemStack create(ElementSMPRefined plugin);

    void registerRecipe(ElementSMPRefined plugin);

    boolean isItem(ItemStack stack, ElementSMPRefined plugin);

    boolean handleUse(PlayerInteractEvent e, ElementSMPRefined plugin, ManaManager mana, ConfigManager config);

    void handleDamage(EntityDamageByEntityEvent e, ElementSMPRefined plugin);

    default void handleLaunch(ProjectileLaunchEvent e, ElementSMPRefined plugin, ManaManager mana, ConfigManager config) {}
}
