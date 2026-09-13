package net.rose.elementSMPRefined.listeners.handler;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.data.PlayerData;
import net.rose.elementSMPRefined.core.API.element.ElementId;
import net.rose.elementSMPRefined.core.API.event.UpgradeLevelChangeEvent;
import net.rose.elementSMPRefined.items.ItemKeys;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.util.bukkit.ItemUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
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
        ElementId currentElementId = playerData.getCurrentElementId();
        int currentUpgradeLevel = playerData.getUpgradeLevel(currentElementId);

        event.setCancelled(true);

        if (upgraderLevel == 1) {
            handleUpgradeI(player, item, playerData, currentElementId, currentUpgradeLevel);
        } else if (upgraderLevel == 2) {
            handleUpgradeII(player, item, playerData, currentElementId, currentUpgradeLevel);
        }
    }

    private boolean isValidAction(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    private boolean isValidUpgrader(ItemStack item) {
        if (item == null) return false;

        Material type = item.getType();
        if (type != Material.AMETHYST_SHARD && type != Material.ECHO_SHARD) return false;

        return ItemUtil.hasTag(item, ItemKeys.upgraderLevel(plugin), PersistentDataType.INTEGER);
    }

    private int getUpgraderLevel(ItemStack item) {
        return ItemUtil.getTag(item, ItemKeys.upgraderLevel(plugin), PersistentDataType.INTEGER).orElseThrow();
    }

    private void handleUpgradeI(Player player, ItemStack item, PlayerData playerData,
                                ElementId currentElementId, int currentUpgradeLevel) {
        if (currentUpgradeLevel >= 1) {
            player.sendMessage(ChatColor.RED + "You already have Upgrade I");
            return;
        }

        if (applyUpgrade(player, item, playerData, currentElementId, 1)) {
            player.sendMessage(ChatColor.GREEN + "You have unlocked " + ChatColor.GOLD + "Upgrade I");
        }
    }

    private void handleUpgradeII(Player player, ItemStack item, PlayerData playerData,
                                 ElementId currentElementId, int currentUpgradeLevel) {
        if (currentUpgradeLevel < 1) {
            player.sendMessage(ChatColor.RED + "You need Upgrade I before you can use Upgrade II!");
            return;
        }

        if (currentUpgradeLevel >= 2) {
            player.sendMessage(ChatColor.RED + "You already have Upgrade II");
            return;
        }

        if (applyUpgrade(player, item, playerData, currentElementId, 2)) {
            player.sendMessage(ChatColor.GREEN + "You have unlocked " + ChatColor.GOLD + "Upgrade II");
        }
    }

    /**
     * Fires the cancellable UpgradeLevelChangeEvent before touching any state,
     * then applies the level, saves, refreshes passives, and consumes the
     * upgrader. Returns false (item/level untouched, no message) if another
     * plugin cancelled the event.
     */
    private boolean applyUpgrade(Player player, ItemStack item, PlayerData playerData,
                                 ElementId currentElementId, int level) {
        int previousLevel = playerData.getUpgradeLevel(currentElementId);
        UpgradeLevelChangeEvent changeEvent = new UpgradeLevelChangeEvent(player, currentElementId, previousLevel, level);
        plugin.getServer().getPluginManager().callEvent(changeEvent);
        if (changeEvent.isCancelled()) return false;

        playerData.setUpgradeLevel(currentElementId, level);
        plugin.getDataStore().save(playerData);
        elementManager.applyUpsides(player);

        consumeItem(player, item);
        return true;
    }

    private void consumeItem(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }
}