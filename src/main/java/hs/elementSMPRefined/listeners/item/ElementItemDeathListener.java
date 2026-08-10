package hs.elementSMPRefined.listeners.item;

import hs.elementPlugin.ElementPlugin;
import hs.elementPlugin.data.PlayerData;
import hs.elementPlugin.elements.ElementType;
import hs.elementPlugin.managers.ElementManager;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public record ElementItemDeathListener(ElementPlugin plugin, ElementManager elements) implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent e) {
        PlayerData pd = elements.data(e.getEntity().getUniqueId());
        ElementType currentElement = pd.getCurrentElement();

        if (currentElement != null) {
            int currentLevel = pd.getUpgradeLevel(currentElement);

            if (currentLevel > 0) {
                for (int i = 0; i < currentLevel; i++) {
                    if (i == 0) {
                        e.getDrops().add(plugin.getItemManager().createUpgrader1());
                    } else {
                        e.getDrops().add(plugin.getItemManager().createUpgrader2());
                    }
                }

                pd.setUpgradeLevel(currentElement, 0);
                plugin.getDataStore().save(pd);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (e.getEntity().isOnline()) {
                            elements.applyUpsides(e.getEntity());
                        }
                    }
                }.runTaskLater(plugin, 1L);
            }
        }

        if (shouldDropCore(currentElement)) {
            plugin.getLogger().info("Player " + e.getEntity().getName() + " died with " + currentElement + " element - dropping core");

            ItemStack coreItem = hs.elementPlugin.items.ElementCoreItem.createCore(plugin, currentElement);
            if (coreItem != null) {
                e.getDrops().add(coreItem);
                plugin.getLogger().info("Added " + currentElement + " core to death drops");
            } else {
                plugin.getLogger().warning("Failed to create " + currentElement + " core item");
            }

            pd.removeElementItem(currentElement);
            plugin.getDataStore().save(pd);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (e.getEntity().isOnline()) {
                        elements.assignRandomDifferentElement(e.getEntity());
                        e.getEntity().sendMessage(ChatColor.YELLOW + "Your core dropped and you rolled a new element!");
                    }
                }
            }.runTaskLater(plugin, 40L);
        } else {
            plugin.getLogger().info("Player " + e.getEntity().getName() + " died with " + currentElement + " element - no core drop");
        }
    }

    private boolean shouldDropCore(ElementType t) {
        return t == ElementType.LIFE || t == ElementType.DEATH;
    }
}

