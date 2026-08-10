package hs.elementSMPRefined.elements.impl.death;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.data.PlayerData;
import hs.elementSMPRefined.elements.ElementType;
import hs.elementSMPRefined.items.ItemKeys;
import hs.elementSMPRefined.managers.ElementManager;
import hs.elementSMPRefined.util.visual.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class DeathElementCraftListener implements Listener {
    private final ElementSMPRefined plugin;
    private final ElementManager elementManager;

    public DeathElementCraftListener(ElementSMPRefined plugin, ElementManager elementManager) {
        this.plugin = plugin;
        this.elementManager = elementManager;
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        ItemStack result = e.getRecipe() == null ? null : e.getRecipe().getResult();
        if (result == null) return;

        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        // Check for Death core specifically using the unique key
        Byte isDeathCore = meta.getPersistentDataContainer().get(
                ItemKeys.deathCore(plugin),
                PersistentDataType.BYTE
        );

        if (isDeathCore == null || isDeathCore != (byte)1) return;

        PlayerData pd = elementManager.data(p.getUniqueId());

        if (plugin.getDataStore().isDeathElementCrafted()) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "The Death Element has already been crafted by someone!");
            return;
        }

        if (pd.hasElementItem(ElementType.DEATH)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "You have already crafted the Death Element!");
            return;
        }

        consumeIngredients(e);

        e.setCancelled(true);
        p.getInventory().addItem(result);

        pd.addElementItem(ElementType.DEATH);
        pd.setCurrentElementUpgradeLevel(0);
        plugin.getDataStore().setDeathElementCrafted(true);
        plugin.getDataStore().save(pd);

        SoundUtils.playTo(p, SoundUtils.UI.ROLL);
        SoundUtils.playTo(p, SoundUtils.Element.DEATH);

        Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "💀 " + p.getName() + " has crafted the Death Element! 💀");
    }

    private void consumeIngredients(CraftItemEvent e) {
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
        }

        craftingInv.setMatrix(matrix);
    }
}