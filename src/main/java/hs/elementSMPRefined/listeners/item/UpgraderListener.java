package hs.elementSMPRefined.listeners.item;

import hs.elementSMPRefined.items.ItemKeys;
import hs.elementSMPRefined.managers.ElementManager;
import hs.elementSMPRefined.ElementSMPRefined;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class UpgraderListener implements Listener {
    private final ElementSMPRefined plugin;
    private final ElementManager elementManager;

    public UpgraderListener(ElementSMPRefined plugin, ElementManager elementManager) {
        this.plugin = plugin;
        this.elementManager = elementManager;
    }

    @EventHandler
    public void onUpgraderUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || (item.getType() != Material.AMETHYST_SHARD && item.getType() != Material.ECHO_SHARD)) {
            return;
        }

        if (!item.hasItemMeta()) {
            return;
        }

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        NamespacedKey upgraderKey = ItemKeys.upgraderLevel(plugin);

        if (!pdc.has(upgraderKey, PersistentDataType.INTEGER)) {
            return;
        }

        int upgraderLevel = pdc.get(upgraderKey, PersistentDataType.INTEGER);
        
        var playerData = elementManager.data(player.getUniqueId());
        var currentElement = playerData.getCurrentElement();
        int currentUpgradeLevel = playerData.getUpgradeLevel(currentElement);

        event.setCancelled(true);

        if (upgraderLevel == 1) {
            if (currentUpgradeLevel >= 1) {
                player.sendMessage(ChatColor.RED + "You already have Upgrade I");
                return;
            }
            
            playerData.setUpgradeLevel(currentElement, 1);
            plugin.getDataStore().save(playerData);
            
            elementManager.applyUpsides(player);
            
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
            
            player.sendMessage(ChatColor.GREEN + "You have unlocked " + ChatColor.GOLD +
                    "Upgrade I");
        }
        else if (upgraderLevel == 2) {
            if (currentUpgradeLevel < 1) {
                player.sendMessage(ChatColor.RED + "You need Upgrade I before you can use Upgrade II!");
                return;
            }
            
            if (currentUpgradeLevel >= 2) {
                player.sendMessage(ChatColor.RED + "You already have Upgrade II");
                return;
            }
            
            playerData.setUpgradeLevel(currentElement, 2);
            plugin.getDataStore().save(playerData);
            
            elementManager.applyUpsides(player);
            
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
            
            player.sendMessage(ChatColor.GREEN + "You have unlocked " + ChatColor.GOLD +
                    "Upgrade II");
        }
    }
}

