package hs.elementSMPRefined.listeners;

import hs.elementSMPRefined.API.ElementType;
import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.gui.ElementSelectionGUI;
import hs.elementSMPRefined.items.ItemKeys;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class GUIListener implements Listener {
    private final ElementSMPRefined plugin;
    // Prevent rapid re-open loops when inventories transition
    private final java.util.Set<java.util.UUID> suppressReopen = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public GUIListener(ElementSMPRefined plugin) {
        this.plugin = plugin;
    }

    public void onPlayerQuit(UUID playerUuid) {
        suppressReopen.remove(playerUuid);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        if (title.contains("Rolling Element") || title.contains("Select Your Element")) {
            event.setCancelled(true);

            hs.elementSMPRefined.gui.ElementSelectionGUI gui = ElementSelectionGUI.getGUI(player.getUniqueId());
            if (gui != null) {
                gui.handleClick(event.getRawSlot());
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        String title = event.getView().getTitle();
        if (title.contains("Rolling Element") || title.contains("Select Your Element")) {
            ElementSelectionGUI.removeGUI(player.getUniqueId());
            // Capture close reason to avoid reopening during inventory transitions
            InventoryCloseEvent.Reason reason = event.getReason();
            // Delay the check to the next tick so element assignment can complete
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // Skip if we just opened, or if a new inventory is opening/closed by plugin
                if (suppressReopen.contains(player.getUniqueId())) return;
                if (reason == InventoryCloseEvent.Reason.OPEN_NEW ||
                        reason == InventoryCloseEvent.Reason.PLUGIN) {
                    return;
                }
                hs.elementSMPRefined.managers.ElementManager em = plugin.getElementManager();
                if (em.data(player.getUniqueId()).getCurrentElement() == null) {
                    player.sendMessage(net.kyori.adventure.text.Component.text("You must choose an element to play!").color(net.kyori.adventure.text.format.NamedTextColor.RED));
                    suppressReopen.add(player.getUniqueId());
                    new hs.elementSMPRefined.gui.ElementSelectionGUI(plugin, player, false).open();
                    // Remove suppression shortly after to allow future legitimate closes to trigger reopen
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> suppressReopen.remove(player.getUniqueId()), 2L);
                }
            });
        }
    }


    @EventHandler
    public void onElementItemUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !item.hasItemMeta()) return;

        // Check if this is an element item
        boolean isElementItem = item.getItemMeta().getPersistentDataContainer()
                .has(ItemKeys.elementItem(plugin), PersistentDataType.BYTE);

        if (!isElementItem) return;

        // Only handle right-click actions
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
                event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Get the element type from the item
        String elementTypeString = item.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.elementType(plugin), PersistentDataType.STRING);

        if (elementTypeString == null) {
            player.sendMessage(net.kyori.adventure.text.Component.text("Invalid element item!").color(net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        try {
            ElementType elementType =
                    ElementType.valueOf(elementTypeString);

            // Check if player already has this element
            hs.elementSMPRefined.data.PlayerData pd = plugin.getElementManager().data(player.getUniqueId());
            if (pd.hasElementItem(elementType)) {
                player.sendMessage(
                        net.kyori.adventure.text.Component.text("You already have the ")
                                .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                                .append(net.kyori.adventure.text.Component.text(elementType.name(), net.kyori.adventure.text.format.NamedTextColor.GOLD))
                                .append(net.kyori.adventure.text.Component.text(" core! You cannot consume it again.", net.kyori.adventure.text.format.NamedTextColor.YELLOW))
                );
                return;
            }

            // Apply the element
            plugin.getElementManager().assignElement(player, elementType);

            // Consume the element core FIRST before giving new one
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                // Remove from the hand that was used
                if (event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) {
                    player.getInventory().setItemInMainHand(null);
                } else {
                    player.getInventory().setItemInOffHand(null);
                }
            }

            // Give a new matching core immediately after user consumes one
            plugin.getElementManager().giveElementItem(player, elementType);

            player.sendMessage(
                    net.kyori.adventure.text.Component.text("You have chosen ")
                            .color(net.kyori.adventure.text.format.NamedTextColor.GREEN)
                            .append(net.kyori.adventure.text.Component.text(elementType.name(), net.kyori.adventure.text.format.NamedTextColor.AQUA))
                            .append(net.kyori.adventure.text.Component.text(" as your element!", net.kyori.adventure.text.format.NamedTextColor.GREEN))
            );

        } catch (IllegalArgumentException e) {
            player.sendMessage(net.kyori.adventure.text.Component.text("Invalid element type!").color(net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }
}
