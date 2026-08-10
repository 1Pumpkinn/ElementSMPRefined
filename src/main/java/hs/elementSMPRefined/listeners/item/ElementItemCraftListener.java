package hs.elementSMPRefined.listeners.item;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.data.PlayerData;
import hs.elementSMPRefined.elements.ElementType;
import hs.elementSMPRefined.items.ItemKeys;
import hs.elementSMPRefined.managers.ElementManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class ElementItemCraftListener implements Listener {
    private final ElementSMPRefined plugin;
    private final ElementManager elements;

    public ElementItemCraftListener(ElementSMPRefined plugin, ElementManager elements) {
        this.plugin = plugin;
        this.elements = elements;
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof org.bukkit.entity.Player p)) return;
        ItemStack result = e.getRecipe() == null ? null : e.getRecipe().getResult();
        if (result == null) return;
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        Integer level = meta.getPersistentDataContainer().get(ItemKeys.upgraderLevel(plugin), PersistentDataType.INTEGER);
        if (level != null) {
            handleUpgraderCrafting(e, p, level);
            return;
        }

        Byte isElem = meta.getPersistentDataContainer().get(ItemKeys.elementItem(plugin), PersistentDataType.BYTE);
        if (isElem != null && isElem == (byte)1) {
            String t = meta.getPersistentDataContainer().get(ItemKeys.elementType(plugin), PersistentDataType.STRING);
            ElementType type;
            try { 
                type = ElementType.valueOf(t); 
            } catch (Exception ex) { 
                return; 
            }
            
            if (type == ElementType.AIR || type == ElementType.WATER || type == ElementType.FIRE || type == ElementType.EARTH) {
                handleBasicElementCrafting(e, p, type);
            }
        }
    }

    private void handleUpgraderCrafting(CraftItemEvent e, org.bukkit.entity.Player p, Integer level) {
        PlayerData pd = elements.data(p.getUniqueId());
        ElementType type = pd.getCurrentElement();
        if (type == null) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "You don't have an element yet.");
            return;
        }
        
        if (level == 2) {
            int currentLevel = pd.getUpgradeLevel(type);
            if (currentLevel < 1) {
                e.setCancelled(true);
                p.sendMessage(ChatColor.RED + "You must craft and possess Upgrader I before crafting Upgrader II.");
                return;
            }
        }
        
        int current = pd.getUpgradeLevel(type);
        if (level <= current) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.YELLOW + "You already have this upgrade.");
            return;
        }

        CraftingInventory craftingInv = e.getInventory();
        ItemStack[] matrix = craftingInv.getMatrix();
        
        org.bukkit.inventory.Recipe recipe = e.getRecipe();
        if (recipe instanceof org.bukkit.inventory.ShapedRecipe shapedRecipe) {
            String[] shape = shapedRecipe.getShape();
            java.util.Map<Character, org.bukkit.inventory.RecipeChoice> ingredients = shapedRecipe.getChoiceMap();
            
            for (int i = 0; i < matrix.length; i++) {
                ItemStack item = matrix[i];
                if (item == null || item.getType() == Material.AIR) continue;
                
                int row = i / 3;
                int col = i % 3;
                
                boolean isPartOfRecipe = false;
                if (row < shape.length && col < shape[row].length()) {
                    char ingredientChar = shape[row].charAt(col);
                    isPartOfRecipe = ingredients.containsKey(ingredientChar);
                }
                
                if (isPartOfRecipe) {
                    if (item.getAmount() > 1) {
                        item.setAmount(item.getAmount() - 1);
                        matrix[i] = item;
                    } else {
                        matrix[i] = null;
                    }
                }
            }
            
            craftingInv.setMatrix(matrix);
        } else {
            for (int i = 0; i < matrix.length; i++) {
                if (matrix[i] != null && matrix[i].getType() != Material.AIR) {
                    if (matrix[i].getAmount() > 1) {
                        matrix[i].setAmount(matrix[i].getAmount() - 1);
                    } else {
                        matrix[i] = null;
                    }
                }
            }
            craftingInv.setMatrix(matrix);
        }
        
        e.getInventory().setResult(null);
        
        pd.setUpgradeLevel(type, level);
        plugin.getDataStore().save(pd);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        if (level == 1) {
            p.sendMessage(ChatColor.GREEN + "Unlocked Ability 1 for " + ChatColor.AQUA + type.name());
        } else if (level == 2) {
            p.sendMessage(ChatColor.AQUA + "Unlocked Ability 2 and Upside 2 for " + ChatColor.GOLD + type.name());
            elements.applyUpsides(p);
        }
    }

    private void handleBasicElementCrafting(CraftItemEvent e, org.bukkit.entity.Player p, ElementType type) {
        PlayerData pd = elements.data(p.getUniqueId());
        
        if (pd.hasElementItem(type)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "You can only craft this item once.");
            return;
        }

        CraftingInventory craftingInv = e.getInventory();
        ItemStack[] matrix = craftingInv.getMatrix();
        
        org.bukkit.inventory.Recipe recipe = e.getRecipe();
        if (recipe instanceof org.bukkit.inventory.ShapedRecipe shapedRecipe) {
            String[] shape = shapedRecipe.getShape();
            java.util.Map<Character, org.bukkit.inventory.RecipeChoice> ingredients = shapedRecipe.getChoiceMap();
            
            for (int i = 0; i < matrix.length; i++) {
                ItemStack item = matrix[i];
                if (item == null || item.getType() == Material.AIR) continue;
                
                int row = i / 3;
                int col = i % 3;
                
                boolean isPartOfRecipe = false;
                if (row < shape.length && col < shape[row].length()) {
                    char ingredientChar = shape[row].charAt(col);
                    isPartOfRecipe = ingredients.containsKey(ingredientChar);
                }
                
                if (isPartOfRecipe) {
                    if (item.getAmount() > 1) {
                        item.setAmount(item.getAmount() - 1);
                        matrix[i] = item;
                    } else {
                        matrix[i] = null;
                    }
                }
            }
            
            craftingInv.setMatrix(matrix);
        } else {
            for (int i = 0; i < matrix.length; i++) {
                if (matrix[i] != null && matrix[i].getType() != Material.AIR) {
                    if (matrix[i].getAmount() > 1) {
                        matrix[i].setAmount(matrix[i].getAmount() - 1);
                    } else {
                        matrix[i] = null;
                    }
                }
            }
            craftingInv.setMatrix(matrix);
        }
        
        e.setCancelled(true);
        
        p.getInventory().addItem(e.getRecipe().getResult());
        
        pd.addElementItem(type);
        pd.setCurrentElementUpgradeLevel(0);
        
        plugin.getDataStore().save(pd);
        p.playSound(p.getLocation(), Sound.UI_TOAST_IN, 1f, 1.2f);
        p.sendMessage(ChatColor.GREEN + "Crafted element item for " + ChatColor.AQUA + type.name());
        p.sendMessage(ChatColor.YELLOW + "All upgrades reset to None");
    }
}

