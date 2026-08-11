package hs.elementSMPRefined.listeners.item;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.data.PlayerData;
import hs.elementSMPRefined.elements.ElementType;
import hs.elementSMPRefined.items.ItemKeys;
import hs.elementSMPRefined.managers.ElementManager;
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

/**
 * Handles upgrader item usage for unlocking element abilities
 */
public class UpgraderHandler implements Listener {
    private final ElementSMPRefined plugin;
    private final ElementManager elementManager;

    public UpgraderHandler(ElementSMPRefined plugin, ElementManager elementManager) {
        this.plugin = plugin;
        this.elementManager = elementManager;
    }

    @EventHandler
    public void onUpgraderUse(PlayerInteractEvent event) {
        if (!isValidAction(event.getAction())) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!isValidUpgrader(item)) return;

        int upgraderLevel = getUpgraderLevel(item);
        PlayerData playerData = elementManager.data(player.getUniqueId());
        ElementType currentElement = playerData.getCurrentElement();
        int currentUpgradeLevel = playerData.getUpgradeLevel(currentElement);

        event.setCancelled(true);

        if (upgraderLevel == 1) {
            handleUpgradeI(player, item, playerData, currentElement, currentUpgradeLevel);
        } else if (upgraderLevel == 2) {
            handleUpgradeII(player, item, playerData, currentElement, currentUpgradeLevel);
        }
    }

    private boolean isValidAction(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    private boolean isValidUpgrader(ItemStack item) {
        if (item == null) return false;
        
        Material type = item.getType();
        if (type != Material.AMETHYST_SHARD && type != Material.ECHO_SHARD) return false;
        
        if (!item.hasItemMeta()) return false;
        
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        NamespacedKey upgraderKey = ItemKeys.upgraderLevel(plugin);
        
        return pdc.has(upgraderKey, PersistentDataType.INTEGER);
    }

    private int getUpgraderLevel(ItemStack item) {
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        NamespacedKey upgraderKey = ItemKeys.upgraderLevel(plugin);
        return pdc.get(upgraderKey, PersistentDataType.INTEGER);
    }

    private void handleUpgradeI(Player player, ItemStack item, PlayerData playerData, 
                              ElementType currentElement, int currentUpgradeLevel) {
        if (currentUpgradeLevel >= 1) {
            player.sendMessage(ChatColor.RED + "You already have Upgrade I");
            return;
        }
        
        applyUpgrade(player, item, playerData, currentElement, 1);
        player.sendMessage(ChatColor.GREEN + "You have unlocked " + ChatColor.GOLD + "Upgrade I");
    }

    private void handleUpgradeII(Player player, ItemStack item, PlayerData playerData, 
                               ElementType currentElement, int currentUpgradeLevel) {
        if (currentUpgradeLevel < 1) {
            player.sendMessage(ChatColor.RED + "You need Upgrade I before you can use Upgrade II!");
            return;
        }
        
        if (currentUpgradeLevel >= 2) {
            player.sendMessage(ChatColor.RED + "You already have Upgrade II");
            return;
        }
        
        applyUpgrade(player, item, playerData, currentElement, 2);
        player.sendMessage(ChatColor.GREEN + "You have unlocked " + ChatColor.GOLD + "Upgrade II");
    }

    private void applyUpgrade(Player player, ItemStack item, PlayerData playerData, 
                            ElementType currentElement, int level) {
        playerData.setUpgradeLevel(currentElement, level);
        plugin.getDataStore().save(playerData);
        elementManager.applyUpsides(player);
        
        consumeItem(player, item);
    }

    private void consumeItem(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }
}
