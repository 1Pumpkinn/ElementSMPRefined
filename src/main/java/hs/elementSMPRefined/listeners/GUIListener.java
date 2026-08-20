package hs.elementSMPRefined.listeners;

import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.gui.ElementSelectionGUI;
import hs.elementSMPRefined.items.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Listener for element selection GUI and element item interactions.
 * Prevents rapid re-open loops during inventory transitions.
 */
public class GUIListener implements Listener {
    private static final long REOPEN_SUPPRESSION_DURATION_TICKS = 2L;
    
    private final ElementSMPRefined plugin;
    // Prevent rapid re-open loops when inventories transition
    private final java.util.Set<UUID> suppressReopen = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public GUIListener(ElementSMPRefined plugin) {
        this.plugin = plugin;
    }

    /**
     * Called when player quits to clean up suppression tracking.
     */
    public void onPlayerQuit(UUID playerUuid) {
        suppressReopen.remove(playerUuid);
    }

    /**
     * Handle element selection GUI clicks.
     * Uses InventoryHolder pattern for robust GUI identification.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Identify GUI via InventoryHolder - robust and doesn't rely on title strings
        if (!(event.getInventory().getHolder() instanceof ElementSelectionGUI gui)) {
            return;
        }

        event.setCancelled(true);
        gui.handleClick(event.getRawSlot());
    }

    /**
     * Handle GUI close events.
     * Validates element selection is complete and reopens if needed.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (!(event.getInventory().getHolder() instanceof ElementSelectionGUI)) {
            return;
        }

        ElementSelectionGUI.removeGUI(player.getUniqueId());
        
        // Capture close reason to avoid reopening during inventory transitions
        InventoryCloseEvent.Reason reason = event.getReason();
        
        // Skip reopen checks for automatic inventory events
        if (reason == InventoryCloseEvent.Reason.OPEN_NEW ||
            reason == InventoryCloseEvent.Reason.PLUGIN) {
            return;
        }

        // Delay the check to the next tick so element assignment can complete
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (suppressReopen.contains(player.getUniqueId())) {
                return;
            }

            // Check if player has selected an element
            var elementManager = plugin.getElementManager();
            if (elementManager.data(player.getUniqueId()).getCurrentElement() == null) {
                player.sendMessage(Component.text("You must choose an element to play!")
                        .color(NamedTextColor.RED));
                suppressReopen.add(player.getUniqueId());
                
                // Reopen the GUI
                new ElementSelectionGUI(plugin, player, false).open();
                
                // Remove suppression shortly after to allow future legitimate closes
                plugin.getServer().getScheduler().runTaskLater(plugin, 
                    () -> suppressReopen.remove(player.getUniqueId()), 
                    REOPEN_SUPPRESSION_DURATION_TICKS);
            }
        });
    }

    /**
     * Handle element core item right-click usage.
     * Validates the item, checks prerequisites, and assigns element to player.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onElementItemUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Validate item exists and has metadata
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        // Only handle right-click actions
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
            event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Check if this is an element item
        var pdc = item.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(ItemKeys.elementItem(plugin), PersistentDataType.BYTE)) {
            return;
        }

        // Get the element type from the item
        String elementTypeString = pdc.get(ItemKeys.elementType(plugin), PersistentDataType.STRING);
        if (elementTypeString == null) {
            player.sendMessage(Component.text("Invalid element item!")
                    .color(NamedTextColor.RED));
            return;
        }

        try {
            ElementType elementType = ElementType.valueOf(elementTypeString);
            useElementCore(player, item, event.getHand(), elementType);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("Invalid element type!")
                    .color(NamedTextColor.RED));
        }
    }

    /**
     * Apply the element core to the player.
     * Validates prerequisites, consumes the item, and assigns the element.
     */
    private void useElementCore(Player player, ItemStack item, EquipmentSlot hand, ElementType elementType) {
        var elementManager = plugin.getElementManager();
        var playerData = elementManager.data(player.getUniqueId());

        // Null safety check
        if (playerData == null) {
            player.sendMessage(Component.text("Could not load your player data!")
                    .color(NamedTextColor.RED));
            return;
        }

        // Don't waste the core if they're already on this element
        if (playerData.getCurrentElement() == elementType) {
            player.sendMessage(Component.text("You are already using the ")
                    .color(NamedTextColor.YELLOW)
                    .append(Component.text(elementType.name(), NamedTextColor.GOLD))
                    .append(Component.text(" element!", NamedTextColor.YELLOW)));
            return;
        }

        // Consume the core
        consumeItem(player, item, hand);

        // Apply the element
        elementManager.assignElement(player, elementType);

        player.sendMessage(Component.text("You have chosen ")
                .color(NamedTextColor.GREEN)
                .append(Component.text(elementType.name(), NamedTextColor.AQUA))
                .append(Component.text(" as your element!", NamedTextColor.GREEN)));
    }

    /**
     * Consume one copy of the item from the player's inventory.
     */
    private void consumeItem(Player player, ItemStack item, EquipmentSlot hand) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            // Remove from the hand that was used
            if (hand == EquipmentSlot.HAND) {
                player.getInventory().setItemInMainHand(null);
            } else {
                player.getInventory().setItemInOffHand(null);
            }
        }
    }
}