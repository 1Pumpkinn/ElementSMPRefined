package hs.elementSMPRefined.commands;

import hs.elementPlugin.ElementPlugin;
import hs.elementPlugin.items.AdvancedRerollerItem;
import hs.elementPlugin.items.RerollerItem;
import hs.elementPlugin.items.Upgrader1Item;
import hs.elementPlugin.items.Upgrader2Item;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ToggleRecipeCommand implements CommandExecutor, TabCompleter {
    private final ElementPlugin plugin;

    public ToggleRecipeCommand(ElementPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("element.admin")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Usage: /togglerecipe <upgrader1|upgrader2|reroller|advancedreroller>");
            return true;
        }

        String recipeType = args[0].toLowerCase();
        NamespacedKey recipeKey;
        boolean newState;

        switch (recipeType) {
            case "upgrader1":
                recipeKey = new NamespacedKey(plugin, Upgrader1Item.KEY);
                newState = toggleRecipe(recipeKey, () -> Upgrader1Item.registerRecipe(plugin));
                player.sendMessage(ChatColor.GREEN + "Upgrader I recipe has been " +
                        (newState ? ChatColor.BOLD + "ENABLED" : ChatColor.RED + "DISABLED"));
                break;

            case "upgrader2":
                recipeKey = new NamespacedKey(plugin, Upgrader2Item.KEY);
                newState = toggleRecipe(recipeKey, () -> Upgrader2Item.registerRecipe(plugin));
                player.sendMessage(ChatColor.GREEN + "Upgrader II recipe has been " +
                        (newState ? ChatColor.BOLD + "ENABLED" : ChatColor.RED + "DISABLED"));
                break;

            case "reroller":
                recipeKey = new NamespacedKey(plugin, RerollerItem.KEY);
                newState = toggleRecipe(recipeKey, () -> RerollerItem.registerRecipe(plugin));
                player.sendMessage(ChatColor.GREEN + "Reroller recipe has been " +
                        (newState ? ChatColor.BOLD + "ENABLED" : ChatColor.RED + "DISABLED"));
                break;

            case "advancedreroller":
                recipeKey = new NamespacedKey(plugin, AdvancedRerollerItem.KEY);
                boolean currentState = plugin.getConfigManager().isAdvancedRerollerRecipeEnabled();
                newState = !currentState;

                plugin.getConfigManager().setAdvancedRerollerRecipeEnabled(newState);

                if (newState) {
                    AdvancedRerollerItem.registerRecipe(plugin);
                    player.sendMessage(ChatColor.GREEN + "Advanced Reroller recipe has been " +
                            ChatColor.BOLD + "ENABLED");
                } else {
                    plugin.getServer().removeRecipe(recipeKey);
                    player.sendMessage(ChatColor.GREEN + "Advanced Reroller recipe has been " +
                            ChatColor.RED + "DISABLED");
                }
                break;

            default:
                player.sendMessage(ChatColor.RED + "Invalid recipe type! Use: upgrader1, upgrader2, reroller, or advancedreroller");
                return true;
        }

        return true;
    }

    /**
     * Toggle a recipe on/off
     * @param key The recipe key
     * @param registerAction Action to register the recipe
     * @return true if recipe is now enabled, false if disabled
     */
    private boolean toggleRecipe(NamespacedKey key, Runnable registerAction) {
        // Check if recipe exists
        boolean exists = plugin.getServer().getRecipe(key) != null;

        if (exists) {
            // Recipe exists, remove it
            plugin.getServer().removeRecipe(key);
            return false;
        } else {
            // Recipe doesn't exist, register it
            registerAction.run();
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> recipeTypes = Arrays.asList("upgrader1", "upgrader2", "reroller", "advancedreroller");
            String input = args[0].toLowerCase();

            completions = recipeTypes.stream()
                    .filter(type -> type.startsWith(input))
                    .collect(Collectors.toList());
        }

        return completions;
    }
}