package hs.elementSMPRefined.listeners.item;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.data.PlayerData;
import hs.elementSMPRefined.API.element.ElementId;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.items.ItemKeys;
import hs.elementSMPRefined.managers.ElementManager;
import hs.elementSMPRefined.util.visual.SoundUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles all element item crafting events including element cores and upgraders
 */
public class ElementItemCraftingListener implements Listener {
    private final ElementSMPRefined plugin;
    private final ElementManager elements;

    public ElementItemCraftingListener(ElementSMPRefined plugin, ElementManager elements) {
        this.plugin = plugin;
        this.elements = elements;
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;
        
        ItemStack result = event.getRecipe() == null ? null : event.getRecipe().getResult();
        if (result == null) return;
        
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        Integer upgraderLevel = meta.getPersistentDataContainer()
                .get(ItemKeys.upgraderLevel(plugin), PersistentDataType.INTEGER);
        
        if (upgraderLevel != null) {
            handleUpgraderCrafting(event, player, upgraderLevel);
            return;
        }

        Byte isElementItem = meta.getPersistentDataContainer()
                .get(ItemKeys.elementItem(plugin), PersistentDataType.BYTE);
        
        if (isElementItem != null && isElementItem == 1) {
            String typeString = meta.getPersistentDataContainer()
                    .get(ItemKeys.elementType(plugin), PersistentDataType.STRING);
            
            try {
                ElementType type = ElementType.valueOf(typeString);
                if (isBasicElement(type)) {
                    handleBasicElementCrafting(event, player, type);
                }
            } catch (Exception ignored) {
                // Invalid element type, ignore
            }
        }
    }

    private void handleUpgraderCrafting(CraftItemEvent event, org.bukkit.entity.Player player, int level) {
        PlayerData playerData = elements.data(player.getUniqueId());
        ElementId currentElementId = playerData.getCurrentElementId();
        
        if (currentElementId == null) {
            cancelCrafting(event, player, "You don't have an element yet.");
            return;
        }
        
        if (level == 2 && playerData.getUpgradeLevel(currentElementId) < 1) {
            cancelCrafting(event, player, "You must craft and possess Upgrader I before crafting Upgrader II.");
            return;
        }
        
        if (level <= playerData.getUpgradeLevel(currentElementId)) {
            cancelCrafting(event, player, "You already have this upgrade.");
            return;
        }

        consumeRecipeIngredients(event);
        event.getInventory().setResult(null);
        
        playerData.setUpgradeLevel(currentElementId, level);
        plugin.getDataStore().save(playerData);
        SoundUtils.playTo(player, SoundUtils.UI.SUCCESS);
        
        String message = level == 1
            ? "Unlocked Ability 1 for " + currentElementId
            : "Unlocked Ability 2 and Upside 2 for " + currentElementId;
        player.sendMessage(ChatColor.GREEN + message);
        
        if (level == 2) {
            elements.applyUpsides(player);
        }
    }

    private void handleBasicElementCrafting(CraftItemEvent event, org.bukkit.entity.Player player, ElementType type) {
        PlayerData playerData = elements.data(player.getUniqueId());
        
        if (playerData.hasElementItem(type)) {
            cancelCrafting(event, player, "You can only craft this item once.");
            return;
        }

        consumeRecipeIngredients(event);
        event.setCancelled(true);
        
        player.getInventory().addItem(event.getRecipe().getResult());
        
        playerData.addElementItem(type);
        playerData.setCurrentElementUpgradeLevel(0);
        plugin.getDataStore().save(playerData);
        
        SoundUtils.playTo(player, SoundUtils.UI.ROLL);
        player.sendMessage(ChatColor.GREEN + "Crafted element item for " + ChatColor.AQUA + type.name());
        player.sendMessage(ChatColor.YELLOW + "All upgrades reset to None");
    }

    private void consumeRecipeIngredients(CraftItemEvent event) {
        CraftingInventory craftingInv = event.getInventory();
        ItemStack[] matrix = craftingInv.getMatrix();
        
        org.bukkit.inventory.Recipe recipe = event.getRecipe();
        if (recipe instanceof org.bukkit.inventory.ShapedRecipe shapedRecipe) {
            consumeShapedRecipe(matrix, shapedRecipe);
        } else {
            consumeAllIngredients(matrix);
        }
        
        craftingInv.setMatrix(matrix);
    }

    private void consumeShapedRecipe(ItemStack[] matrix, org.bukkit.inventory.ShapedRecipe recipe) {
        String[] shape = recipe.getShape();
        java.util.Map<Character, org.bukkit.inventory.RecipeChoice> ingredients = recipe.getChoiceMap();
        
        for (int i = 0; i < matrix.length; i++) {
            ItemStack item = matrix[i];
            if (item == null || item.getType() == Material.AIR) continue;
            
            int row = i / 3;
            int col = i % 3;
            
            if (row < shape.length && col < shape[row].length()) {
                char ingredientChar = shape[row].charAt(col);
                if (ingredients.containsKey(ingredientChar)) {
                    consumeItem(matrix, i);
                }
            }
        }
    }

    private void consumeAllIngredients(ItemStack[] matrix) {
        for (int i = 0; i < matrix.length; i++) {
            if (matrix[i] != null && matrix[i].getType() != Material.AIR) {
                consumeItem(matrix, i);
            }
        }
    }

    private void consumeItem(ItemStack[] matrix, int index) {
        ItemStack item = matrix[index];
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            matrix[index] = null;
        }
    }

    private void cancelCrafting(CraftItemEvent event, org.bukkit.entity.Player player, String message) {
        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + message);
    }

    private boolean isBasicElement(ElementType type) {
        return type == ElementType.AIR || type == ElementType.WATER || 
               type == ElementType.FIRE || type == ElementType.EARTH;
    }
}
