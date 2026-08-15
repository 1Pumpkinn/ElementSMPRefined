package hs.elementSMPRefined.initializers;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.commands.ElementCommand;
import hs.elementSMPRefined.commands.ElementInfoCommand;
import hs.elementSMPRefined.commands.ManaCommand;
import hs.elementSMPRefined.commands.ToggleRecipeCommand;
import hs.elementSMPRefined.commands.TrustCommand;
import hs.elementSMPRefined.commands.UtilCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles registration of all plugin commands.
 * Centralizes command registration logic away from the main class.
 */
public class CommandInitializer {
    private final ElementSMPRefined plugin;

    public CommandInitializer(JavaPlugin plugin) {
        this.plugin = (ElementSMPRefined) plugin;
    }

    public void registerCommands() {
        plugin.getLogger().info("Registering commands...");

        CommandRegister.register(plugin)
                .command("elements", new ElementInfoCommand(plugin))
                .command("trust", new TrustCommand(plugin, plugin.getTrustManager()))
                .command("element", new ElementCommand(plugin))
                .command("mana", new ManaCommand(plugin.getManaManager(), plugin.getConfigManager()))
                .command("util", new UtilCommand(plugin))
                .command("togglerecipe", new ToggleRecipeCommand(plugin));
    }

    private static class CommandRegister {
        private final ElementSMPRefined plugin;

        private CommandRegister(ElementSMPRefined plugin) {
            this.plugin = plugin;
        }

        static CommandRegister register(ElementSMPRefined plugin) {
            return new CommandRegister(plugin);
        }

        CommandRegister command(String name, org.bukkit.command.CommandExecutor executor) {
            var cmd = plugin.getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(executor);
                if (executor instanceof org.bukkit.command.TabCompleter completer) {
                    cmd.setTabCompleter(completer);
                }
            }
            return this;
        }
    }
}