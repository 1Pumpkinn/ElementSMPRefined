package net.rose.elementSMPRefined.commands;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.managers.ConfigManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /dimension <enable|disable|status> <nether|end|all>}
 * <p>
 * Flips the {@code dimensions.nether_disabled} / {@code dimensions.end_disabled}
 * config flags that {@link net.rose.elementSMPRefined.util.server.DimensionDisable}
 * reads on every portal event. No listener reference needed here since that
 * class checks {@link ConfigManager} directly rather than caching state.
 */
public class DimensionCommand implements CommandExecutor, TabCompleter {
    private final ElementSMPRefined plugin;

    public DimensionCommand(ElementSMPRefined plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("element.admin")) {
            sender.sendMessage(Lang.DIMENSION_CMD_USAGE);
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage(Lang.DIMENSION_CMD_USAGE);
            return true;
        }

        String action = args[0].toLowerCase();
        String target = args[1].toLowerCase();
        ConfigManager configManager = plugin.getConfigManager();

        switch (action) {
            case "status" -> sender.sendMessage(Lang.dimensionCmdStatus(
                    configManager.isNetherDisabled(), configManager.isEndDisabled()));
            case "enable" -> applyToggle(sender, configManager, target, false);
            case "disable" -> applyToggle(sender, configManager, target, true);
            default -> sender.sendMessage(Lang.DIMENSION_CMD_USAGE);
        }

        return true;
    }

    private void applyToggle(CommandSender sender, ConfigManager configManager, String target, boolean disabled) {
        switch (target) {
            case "nether" -> {
                configManager.setNetherDisabled(disabled);
                sender.sendMessage(Lang.dimensionCmdToggled("Nether", disabled));
            }
            case "end" -> {
                configManager.setEndDisabled(disabled);
                sender.sendMessage(Lang.dimensionCmdToggled("End", disabled));
            }
            case "all" -> {
                configManager.setNetherDisabled(disabled);
                configManager.setEndDisabled(disabled);
                sender.sendMessage(Lang.dimensionCmdToggled("Nether", disabled));
                sender.sendMessage(Lang.dimensionCmdToggled("End", disabled));
            }
            default -> sender.sendMessage(Lang.DIMENSION_CMD_INVALID_TARGET);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> actions = Arrays.asList("enable", "disable", "status");
            String input = args[0].toLowerCase();
            return actions.stream()
                    .filter(a -> a.startsWith(input))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            List<String> targets = Arrays.asList("nether", "end", "all");
            String input = args[1].toLowerCase();
            return targets.stream()
                    .filter(t -> t.startsWith(input))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}