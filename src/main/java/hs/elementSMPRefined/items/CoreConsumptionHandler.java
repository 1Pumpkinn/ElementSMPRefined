package hs.elementSMPRefined.items;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.data.PlayerData;
import hs.elementSMPRefined.API.ElementType;
import hs.elementSMPRefined.managers.ElementManager;
import hs.elementSMPRefined.util.bukkit.ItemUtil;
import hs.elementSMPRefined.util.visual.SoundUtils;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public final class CoreConsumptionHandler {
    private CoreConsumptionHandler() {}

    public static boolean isRightClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    /**
     * Handles consuming Life/Death core items on right-click. Returns true if the event was handled.
     */
    public static boolean handleCoreConsume(PlayerInteractEvent e, ElementSMPRefined plugin, ElementManager elements) {
        Player player = e.getPlayer();
        ItemStack inHand = e.getItem();
        if (inHand == null || !ItemUtil.isElementItem(plugin, inHand)) return false;

        // Use improved API
        Optional<ElementType> typeOpt = ItemUtil.getElementTypeOptional(plugin, inHand);
        if (typeOpt.isEmpty()) return false;
        ElementType type = typeOpt.get();

        if (type != ElementType.LIFE && type != ElementType.DEATH) return false;

        if (!isRightClick(e.getAction())) return false;

        // CRITICAL: Check if clicking on a pedestal - if so, don't consume the core
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            org.bukkit.block.Block clickedBlock = e.getClickedBlock();
            if (clickedBlock != null && clickedBlock.getType() == org.bukkit.Material.LODESTONE) {
                // Check if it's a custom block (pedestal) using BlockDataStorage

            }
        }

        PlayerData pd = elements.data(player.getUniqueId());
        // Switch to the core's element
        elements.setElement(player, type);

        // Mark that they have consumed this core
        pd.addElementItem(type);
        plugin.getDataStore().save(pd);

        // Consume the item - use the actual item in hand (main or off-hand)
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.equals(inHand)) {
            itemInHand.setAmount(itemInHand.getAmount() - 1);
        } else {
            itemInHand = player.getInventory().getItemInOffHand();
            if (itemInHand.equals(inHand)) {
                itemInHand.setAmount(itemInHand.getAmount() - 1);
            }
        }

        // Use improved SoundUtils for feedback
        SoundUtils.playTo(player, SoundUtils.Element.LIFE);
        SoundUtils.playTo(player, SoundUtils.Ability.SUCCESS);

        player.sendMessage(ChatColor.GREEN + "You consumed the " +
                hs.elementSMPRefined.items.ElementCoreItem.getDisplayName(type) + ChatColor.GREEN + "!");

        e.setCancelled(true);
        return true;
    }
}


