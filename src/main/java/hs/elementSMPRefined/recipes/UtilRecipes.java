package hs.elementSMPRefined.recipes;

import hs.elementSMPRefined.items.AdvancedRerollerItem;
import hs.elementSMPRefined.items.RerollerItem;
import hs.elementSMPRefined.items.Upgrader1Item;
import hs.elementSMPRefined.items.Upgrader2Item;
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

