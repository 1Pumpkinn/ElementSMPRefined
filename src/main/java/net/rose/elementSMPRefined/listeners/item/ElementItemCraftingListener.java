package net.rose.elementSMPRefined.listeners.item;

import net.kyori.adventure.text.Component;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.data.PlayerData;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.API.event.UpgradeLevelChangeEvent;
import net.rose.elementSMPRefined.items.ItemKeys;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.util.bukkit.ItemUtil;
import net.rose.elementSMPRefined.util.sound.SoundUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles upgrader crafting: validates the player's element and upgrade level,
 * fires {@link UpgradeLevelChangeEvent}, then applies the new level.
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
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack result = event.getRecipe() == null ? null : event.getRecipe().getResult();
        if (result == null) return;

        ItemUtil.getTag(result, ItemKeys.upgraderLevel(plugin), PersistentDataType.INTEGER)
                .ifPresent(level -> handleUpgraderCrafting(event, player, level));
    }

    private void handleUpgraderCrafting(CraftItemEvent event, Player player, int level) {
        PlayerData playerData = elements.data(player.getUniqueId());
        ElementType currentElement = playerData.getCurrentElement();

        if (currentElement == null) {
            cancelCrafting(event, player, Lang.CRAFTING_NO_ELEMENT_YET);
            return;
        }

        if (level == 2 && playerData.getUpgradeLevel(currentElement) < 1) {
            cancelCrafting(event, player, Lang.CRAFTING_UPGRADER_2_REQUIRES_UPGRADER_1);
            return;
        }

        if (level <= playerData.getUpgradeLevel(currentElement)) {
            cancelCrafting(event, player, Lang.CRAFTING_UPGRADE_ALREADY_OWNED);
            return;
        }

        // Same cancellable event the upgrader item fires, so another plugin can veto
        // a crafted upgrade too. Fired before anything is consumed or changed.
        UpgradeLevelChangeEvent changeEvent = new UpgradeLevelChangeEvent(
                player, currentElement, playerData.getUpgradeLevel(currentElement), level);
        plugin.getServer().getPluginManager().callEvent(changeEvent);
        if (changeEvent.isCancelled()) {
            event.setCancelled(true);
            return;
        }

        consumeRecipeIngredients(event);
        event.getInventory().setResult(null);

        playerData.setUpgradeLevel(currentElement, level);
        plugin.getDataStore().save(playerData);
        SoundUtils.playTo(player, SoundUtils.UI.SUCCESS);

        player.sendMessage(level == 1
                ? Lang.craftingUnlockedAbility1(currentElement)
                : Lang.craftingUnlockedAbility2(currentElement));

        if (level == 2) {
            elements.applyUpsides(player);
        }
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

    private void cancelCrafting(CraftItemEvent event, Player player, Component message) {
        event.setCancelled(true);
        player.sendMessage(message);
    }
}
