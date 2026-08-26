package hs.elementSMPRefined.recipes;

import hs.elementSMPRefined.items.recipes.AdvancedRerollerItem;
import hs.elementSMPRefined.items.recipes.RerollerItem;
import hs.elementSMPRefined.items.recipes.Upgrader1Item;
import hs.elementSMPRefined.items.recipes.Upgrader2Item;
import hs.elementSMPRefined.ElementSMPRefined;

public class UtilRecipes {
    public static void registerRecipes(ElementSMPRefined plugin) {
        Upgrader1Item.registerRecipe(plugin);
        Upgrader2Item.registerRecipe(plugin);
        RerollerItem.registerRecipe(plugin);
        
        if (plugin.getConfigManager().isAdvancedRerollerRecipeEnabled()) {
            AdvancedRerollerItem.registerRecipe(plugin);
        }
    }
}

