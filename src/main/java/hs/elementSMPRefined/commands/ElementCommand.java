package hs.elementSMPRefined.commands;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.commands.element.BotCommand;
import hs.elementSMPRefined.commands.element.CommandSupport;
import hs.elementSMPRefined.commands.element.ConfigCommand;
import hs.elementSMPRefined.commands.element.DebugCommand;
import hs.elementSMPRefined.commands.element.ElementSubCommand;
import hs.elementSMPRefined.commands.element.GiveCoreCommand;
import hs.elementSMPRefined.commands.element.ParticlesCommand;
import hs.elementSMPRefined.commands.element.RollCommand;
import hs.elementSMPRefined.commands.element.SetCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /element dispatcher. Each subcommand is its own class under
 * {@code commands.element}, owning both its execution and its own tab
 * completion - see {@link ElementSubCommand}. This class just routes to them
 * and holds the two pieces of behavior ("particles" needs no admin
 * permission, everything else does) that apply across all of them.
 */
public class ElementCommand implements CommandExecutor, TabCompleter {
    private final Map<String, ElementSubCommand> subCommands;

    public ElementCommand(ElementSMPRefined plugin) {
        this.subCommands = initializeSubCommands(plugin);
    }

    private Map<String, ElementSubCommand> initializeSubCommands(ElementSMPRefined plugin) {
        Map<String, ElementSubCommand> commands = new LinkedHashMap<>();
        commands.put("particles", new ParticlesCommand(plugin));
        commands.put("set", new SetCommand(plugin.getElementManager()));
        commands.put("debug", new DebugCommand(plugin.getDataStore(), plugin.getElementManager()));
        commands.put("roll", new RollCommand(plugin, plugin.getElementManager()));
        commands.put("config", new ConfigCommand(plugin, plugin.getConfigManager()));
        commands.put("givecore", new GiveCoreCommand(plugin));
        commands.put("bot", new BotCommand(plugin));
        return commands;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Particles is the one subcommand any player can use - it's a cosmetic
        // preview, not an admin action - so it's dispatched before the
        // permission gate below rather than being gated by it.
        if (args.length > 0 && args[0].equalsIgnoreCase("particles")) {
            ElementSubCommand particles = subCommands.get("particles");
            if (particles != null) {
                return particles.execute(sender, args);
            }
        }

        if (!sender.hasPermission("element.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        ElementSubCommand subCommand = subCommands.get(args[0].toLowerCase());
        if (subCommand != null) {
            return subCommand.execute(sender, args);
        }

        sendHelp(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            if (sender.hasPermission("element.admin") || sender.hasPermission("element.particles") || sender instanceof Player) {
                return CommandSupport.filterStartingWith(subCommands.keySet(), args[0]);
            }
            return Collections.emptyList();
        }

        // Same bypass as onCommand: particles tab-completes for everyone.
        if (args[0].equalsIgnoreCase("particles")) {
            ElementSubCommand particles = subCommands.get("particles");
            return particles != null ? particles.tabComplete(sender, args) : Collections.emptyList();
        }

        if (!sender.hasPermission("element.admin")) {
            return Collections.emptyList();
        }

        ElementSubCommand subCommand = subCommands.get(args[0].toLowerCase());
        if (subCommand != null) {
            return subCommand.tabComplete(sender, args);
        }

        return Collections.emptyList();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== Element Admin Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/element particles <preset> - Preview a particle pattern at your feet", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/element set <player> <element> - Set player's element", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/element debug <player> - Debug player's element data", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/element roll - Roll for a new element (OP only)", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/element config <action> - Configuration management", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  reload | reset [key] | reset element <element> [key]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  set <key> <value> | element <element> <key> <value>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/element givecore <player> <element> - Give a player an element core item", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/element bot <spawn|stop> [element] - Spawn or remove an elemental 1v1 bot", NamedTextColor.YELLOW));
    }
}
