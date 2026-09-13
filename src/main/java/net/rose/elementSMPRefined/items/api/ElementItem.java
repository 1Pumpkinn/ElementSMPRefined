package net.rose.elementSMPRefined.items.api;

import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.ManaManager;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public interface ElementItem {
    ElementType getElementType();

    ItemStack create(JavaPlugin plugin);

    void registerRecipe(JavaPlugin plugin);

    boolean isItem(ItemStack stack, JavaPlugin plugin);

    boolean handleUse(PlayerInteractEvent e, JavaPlugin plugin, ManaManager mana, ConfigManager config);

    void handleDamage(EntityDamageByEntityEvent e, JavaPlugin plugin);

    default void handleLaunch(ProjectileLaunchEvent e, JavaPlugin plugin, ManaManager mana, ConfigManager config) {}
}
