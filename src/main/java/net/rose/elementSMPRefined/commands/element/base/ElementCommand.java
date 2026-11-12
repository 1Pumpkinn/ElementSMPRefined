package net.rose.elementSMPRefined.commands.element.base;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.commands.element.admin.CastAllCommand;
import net.rose.elementSMPRefined.commands.element.admin.ConfigCommand;
import net.rose.elementSMPRefined.commands.element.admin.DataCommand;
import net.rose.elementSMPRefined.commands.element.admin.DebugCommand;
import net.rose.elementSMPRefined.commands.element.admin.EventCommand;
import net.rose.elementSMPRefined.commands.element.admin.HideCommand;
import net.rose.elementSMPRefined.commands.element.admin.PackCommand;
import net.rose.elementSMPRefined.commands.element.admin.SetCommand;
import net.rose.elementSMPRefined.commands.supporters.CommandSupport;
import net.rose.elementSMPRefined.commands.supporters.ElementSubCommand;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /element dispatcher. Each subcommand is its own class under
 * {@code commands.element}, owning both its execution and its own tab
 * completion - see {@link ElementSubCommand}. This class just routes to them
 * and applies the admin permission check to all of them.
 */
public class ElementCommand implements CommandExecutor, TabCompleter {
    private final Map<String, ElementSubCommand> subCommands;

    public ElementCommand(ElementSMPRefined plugin) {
        this.subCommands = initializeSubCommands(plugin);
    }

    private Map<String, ElementSubCommand> initializeSubCommands(ElementSMPRefined plugin) {
        Map<String, ElementSubCommand> commands = new LinkedHashMap<>();
        commands.put("set", new SetCommand(plugin.getElementManager()));
        commands.put(
                "debug",
                new DebugCommand(
                        plugin.getDataStore(),
                        plugin.getElementManager(),
                        plugin.getCooldownManager(),
                        plugin.getDisarmManager(),
                        plugin.getStatusEffectManager(),
                        plugin.getTeamManager()));
        commands.put(
                "data",
                new DataCommand(
                        plugin.getDataStore(),
                        plugin.getElementManager(),
                        plugin.getCooldownManager(),
                        plugin.getDisarmManager(),
                        plugin.getStatusEffectManager(),
                        plugin.getTeamManager()));
        commands.put("config", new ConfigCommand(plugin, plugin.getConfigManager()));
        commands.put("castall", new CastAllCommand(plugin));
        commands.put("event", new EventCommand(plugin));
        commands.put("pack", new PackCommand(plugin.getResourcePackManager()));
        commands.put("hide", new HideCommand(plugin.getConfigManager(), true));
        commands.put("unhide", new HideCommand(plugin.getConfigManager(), false));
        return commands;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("element.admin")) {
            sender.sendMessage(
                    Component.text(
                            "You don't have permission to use this command.", NamedTextColor.RED));
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
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return List.of();
        }

        if (args.length == 1) {
            if (sender.hasPermission("element.admin")) {
                return CommandSupport.filterStartingWith(subCommands.keySet(), args[0]);
            }
            return List.of();
        }

        if (!sender.hasPermission("element.admin")) {
            return List.of();
        }

        ElementSubCommand subCommand = subCommands.get(args[0].toLowerCase());
        if (subCommand != null) {
            return subCommand.tabComplete(sender, args);
        }

        return List.of();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== Element Admin Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(
                Component.text(
                        "/element set <player> <element> - Set player's element",
                        NamedTextColor.YELLOW));
        sender.sendMessage(
                Component.text(
                        "/element data [player] - Show a player's stored data and live state"
                            + " (read-only)",
                        NamedTextColor.YELLOW));
        sender.sendMessage(
                Component.text(
                        "/element debug [player] - Debug a player's element data (defaults to"
                            + " yourself)",
                        NamedTextColor.YELLOW));
        sender.sendMessage(
                Component.text(
                        "/element castall <1|2> [players...|*] [at <target>] - Fire an ability for"
                            + " several players on the same tick (testing)",
                        NamedTextColor.YELLOW));
        sender.sendMessage(
                Component.text(
                        "/element event <start <event_name> [duration]|end|cancel|status|give"
                            + " <life|death> [player]> - Server events (life_death)",
                        NamedTextColor.YELLOW));
        sender.sendMessage(
                Component.text(
                        "/element pack <status|resend [player|*]|reload> - Built-in resource pack",
                        NamedTextColor.YELLOW));
        sender.sendMessage(
                Component.text(
                        "/element hide <element|list> | unhide <element> - Hide unreleased elements"
                            + " from /elements",
                        NamedTextColor.YELLOW));
        sender.sendMessage(
                Component.text(
                        "/element config <action> - Configuration management",
                        NamedTextColor.YELLOW));
        sender.sendMessage(
                Component.text("  reload | reset [key] | set <key> <value>", NamedTextColor.GRAY));
    }
}
