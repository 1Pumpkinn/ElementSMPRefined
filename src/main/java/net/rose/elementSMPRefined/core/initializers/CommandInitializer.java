package net.rose.elementSMPRefined.core.initializers;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.commands.element.base.AbilityCommand;
import net.rose.elementSMPRefined.commands.element.base.ElementCommand;
import net.rose.elementSMPRefined.commands.element.base.ElementInfoCommand;
import net.rose.elementSMPRefined.commands.server.DimensionCommand;
import net.rose.elementSMPRefined.commands.server.GraceCommand;
import net.rose.elementSMPRefined.commands.TrustCommand;
import net.rose.elementSMPRefined.commands.element.admin.UtilCommand;
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

        AbilityCommand abilityCommand = new AbilityCommand(plugin.getElementManager(), plugin.getDisarmManager());

        CommandRegister.register(plugin)
                .command("ability1", abilityCommand)
                .command("ability2", abilityCommand)
                .command("elements", new ElementInfoCommand(plugin))
                .command("trust", new TrustCommand(plugin, plugin.getTrustManager()))
                .command("element", new ElementCommand(plugin))
                .command("util", new UtilCommand(plugin))
                .command("grace", new GraceCommand(plugin))
                .command("dimension", new DimensionCommand(plugin));
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