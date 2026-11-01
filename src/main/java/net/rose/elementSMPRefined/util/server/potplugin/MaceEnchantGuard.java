package net.rose.elementSMPRefined.util.server.potplugin;

import net.rose.elementSMPRefined.items.EnchantableMace;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Maces can't be enchanted, except the single mace carrying the enchantable tag
 * ({@link EnchantableMace}).
 * <ul>
 *   <li>Enchanting table: no enchant offers for an untagged mace, and the enchant is cancelled.</li>
 *   <li>Anvil: an untagged mace can't come out of the anvil carrying enchantments.</li>
 *   <li>Safety sweep: enchantments are stripped off untagged maces when a player joins, picks
 *       one up, or closes an inventory. Tagged maces are never touched.</li>
 * </ul>
 */
public class MaceEnchantGuard implements Listener {
    private final JavaPlugin plugin;

    public MaceEnchantGuard(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (EnchantableMace.isRestrictedMace(plugin, event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (EnchantableMace.isRestrictedMace(plugin, event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (EnchantableMace.isRestrictedMace(plugin, result) && !result.getEnchantments().isEmpty()) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        stripInventory(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Item entity = event.getItem();
        ItemStack stack = entity.getItemStack();
        if (strip(stack)) {
            entity.setItemStack(stack);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        HumanEntity human = event.getPlayer();
        if (human instanceof Player player) {
            stripInventory(player);
        }
    }

    private void stripInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (strip(item)) {
                inventory.setItem(slot, item);
            }
        }
    }

    /** Removes all enchantments from an untagged mace. Returns true if anything was removed. */
    private boolean strip(ItemStack item) {
        if (!EnchantableMace.isRestrictedMace(plugin, item) || item.getEnchantments().isEmpty()) {
            return false;
        }
        item.removeEnchantments();
        return true;
    }
}
