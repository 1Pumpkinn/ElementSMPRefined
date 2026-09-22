package net.rose.elementSMPRefined.listeners.item;

import net.kyori.adventure.text.Component;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.data.PlayerData;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.core.API.element.ElementId;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.items.ItemKeys;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.util.bukkit.ItemUtil;
import net.rose.elementSMPRefined.util.visual.SoundUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

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
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack result = event.getRecipe() == null ? null : event.getRecipe().getResult();
        if (result == null) return;

        Integer upgraderLevel = ItemUtil.getTag(result, ItemKeys.upgraderLevel(plugin), PersistentDataType.INTEGER)
                .orElse(null);

        if (upgraderLevel != null) {
            handleUpgraderCrafting(event, player, upgraderLevel);
            return;
        }

        Byte isElementItem = ItemUtil.getTag(result, ItemKeys.elementItem(plugin), PersistentDataType.BYTE)
                .orElse(null);

        if (isElementItem == null || isElementItem != 1) return;

        String typeString = ItemUtil.getTag(result, ItemKeys.elementType(plugin), PersistentDataType.STRING)
                .orElse(null);
        // Skip straight to a no-op instead of always paying for an
        // exception when the item simply has no element-type tag set.
        if (typeString == null) return;

        try {
            ElementType type = ElementType.valueOf(typeString);
            if (isBasicElement(type)) {
                handleBasicElementCrafting(event, player, type);
            }
        } catch (IllegalArgumentException ignored) {
            // Tag held a name that isn't a real ElementType - ignore.
        }
    }

    private void handleUpgraderCrafting(CraftItemEvent event, Player player, int level) {
        PlayerData playerData = elements.data(player.getUniqueId());
        ElementId currentElementId = playerData.getCurrentElementId();

        if (currentElementId == null) {
            cancelCrafting(event, player, Lang.CRAFTING_NO_ELEMENT_YET);
            return;
        }

        if (level == 2 && playerData.getUpgradeLevel(currentElementId) < 1) {
            cancelCrafting(event, player, Lang.CRAFTING_UPGRADER_2_REQUIRES_UPGRADER_1);
            return;
        }

        if (level <= playerData.getUpgradeLevel(currentElementId)) {
            cancelCrafting(event, player, Lang.CRAFTING_UPGRADE_ALREADY_OWNED);
            return;
        }

        consumeRecipeIngredients(event);
        event.getInventory().setResult(null);

        playerData.setUpgradeLevel(currentElementId, level);
        plugin.getDataStore().save(playerData);
        SoundUtils.playTo(player, SoundUtils.UI.SUCCESS);

        player.sendMessage(level == 1
                ? Lang.craftingUnlockedAbility1(currentElementId)
                : Lang.craftingUnlockedAbility2(currentElementId));

        if (level == 2) {
            elements.applyUpsides(player);
        }
    }

    private void handleBasicElementCrafting(CraftItemEvent event, Player player, ElementType type) {
        PlayerData playerData = elements.data(player.getUniqueId());

        if (playerData.hasElementItem(type)) {
            cancelCrafting(event, player, Lang.CRAFTING_ITEM_ALREADY_CRAFTED);
            return;
        }

        consumeRecipeIngredients(event);
        event.setCancelled(true);

        player.getInventory().addItem(event.getRecipe().getResult());

        playerData.addElementItem(type);
        playerData.setCurrentElementUpgradeLevel(0);
        plugin.getDataStore().save(playerData);

        SoundUtils.playTo(player, SoundUtils.UI.ROLL);
        player.sendMessage(Lang.craftingCraftedElementItem(type.name()));
        player.sendMessage(Lang.CRAFTING_UPGRADES_RESET);
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

    /**
     * Delegates to {@link ElementManager#getBasicElements()} (config.yml-aware)
     * instead of a hardcoded AIR/WATER/FIRE/EARTH check, so a server that
     * reconfigures which elements count as "basic" gets consistent behavior
     * here too instead of this listener silently keeping the old default set.
     */
    private boolean isBasicElement(ElementType type) {
        return Arrays.asList(elements.getBasicElements()).contains(type);
    }
}