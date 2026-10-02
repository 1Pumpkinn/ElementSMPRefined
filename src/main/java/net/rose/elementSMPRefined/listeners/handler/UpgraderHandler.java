package net.rose.elementSMPRefined.listeners.handler;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.data.PlayerData;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.API.event.UpgradeLevelChangeEvent;
import net.rose.elementSMPRefined.items.ItemKeys;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.util.bukkit.ItemUtil;
import net.rose.elementSMPRefined.lang.Lang;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles upgrader item usage: crafting only gives the item, and right-clicking it
 * unlocks the next upgrade level for the player's current element.
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
        EquipmentSlot hand = event.getHand();
        PlayerData playerData = elementManager.data(player.getUniqueId());
        ElementType currentElement = playerData.getCurrentElement();
        if (currentElement == null) {
            player.sendMessage(Lang.UPGRADER_NO_ELEMENT_YET);
            return;
        }
        int currentUpgradeLevel = playerData.getUpgradeLevel(currentElement);

        event.setCancelled(true);

        if (upgraderLevel == 1) {
            handleUpgradeI(player, item, hand, playerData, currentElement, currentUpgradeLevel);
        } else if (upgraderLevel == 2) {
            handleUpgradeII(player, item, hand, playerData, currentElement, currentUpgradeLevel);
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

    private void handleUpgradeI(Player player, ItemStack item, EquipmentSlot hand, PlayerData playerData,
                                ElementType currentElement, int currentUpgradeLevel) {
        if (currentUpgradeLevel >= 1) {
            player.sendMessage(Lang.UPGRADER_YOU_ALREADY_HAVE_UPGRADE_I);
            return;
        }

        if (applyUpgrade(player, item, hand, playerData, currentElement, 1)) {
            player.sendMessage(Lang.UPGRADER_YOU_HAVE_UNLOCKED);
        }
    }

    private void handleUpgradeII(Player player, ItemStack item, EquipmentSlot hand, PlayerData playerData,
                                 ElementType currentElement, int currentUpgradeLevel) {
        if (currentUpgradeLevel < 1) {
            player.sendMessage(Lang.UPGRADER_YOU_NEED_UPGRADE_I_BEFORE);
            return;
        }

        if (currentUpgradeLevel >= 2) {
            player.sendMessage(Lang.UPGRADER_YOU_ALREADY_HAVE_UPGRADE_II);
            return;
        }

        if (applyUpgrade(player, item, hand, playerData, currentElement, 2)) {
            player.sendMessage(Lang.UPGRADER_YOU_HAVE_UNLOCKED_2);
        }
    }

    /**
     * Fires the cancellable UpgradeLevelChangeEvent before touching any state,
     * then applies the level, saves, refreshes passives, and consumes the
     * upgrader. Returns false (item/level untouched, no message) if another
     * plugin cancelled the event.
     */
    private boolean applyUpgrade(Player player, ItemStack item, EquipmentSlot hand, PlayerData playerData,
                                 ElementType currentElement, int level) {
        int previousLevel = playerData.getUpgradeLevel(currentElement);
        UpgradeLevelChangeEvent changeEvent = new UpgradeLevelChangeEvent(player, currentElement, previousLevel, level);
        plugin.getServer().getPluginManager().callEvent(changeEvent);
        if (changeEvent.isCancelled()) return false;

        playerData.setUpgradeLevel(currentElement, level);
        plugin.getDataStore().save(playerData);
        elementManager.applyUpsides(player);

        consumeItem(player, item, hand);
        return true;
    }

    private void consumeItem(Player player, ItemStack item, EquipmentSlot hand) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(null);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }
}