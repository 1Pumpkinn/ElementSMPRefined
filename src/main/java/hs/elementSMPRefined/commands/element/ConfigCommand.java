package hs.elementSMPRefined.commands.element;

import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.managers.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** /element config reload | reset | set <key> <value> | element <element> <key> <value> */
public class ConfigCommand implements ElementSubCommand {
    private final ElementSMPRefined plugin;
    private final ConfigManager configManager;

    public ConfigCommand(ElementSMPRefined plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /element config <action>");
            sender.sendMessage(ChatColor.GRAY + "Actions: reload, reset, set <key> <value>, element <element> <key> <value>");
            return true;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "reload" -> {
                configManager.reload();
                sender.sendMessage(ChatColor.GREEN + "Configuration reloaded successfully!");
            }
            case "reset" -> {
                configManager.reload();
                sender.sendMessage(ChatColor.GREEN + "Configuration reset to file values!");
            }
            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /element config set <key> <value>");
                    return true;
                }
                String key = args[2];
                String value = args[3];

                try {
                    // Try to set the value based on type
                    if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                        configManager.getConfig().set(key, Boolean.parseBoolean(value));
                    } else {
                        try {
                            configManager.getConfig().set(key, Integer.parseInt(value));
                        } catch (NumberFormatException e) {
                            configManager.getConfig().set(key, value);
                        }
                    }
                    plugin.saveConfig();
                    sender.sendMessage(ChatColor.GREEN + "Set " + key + " to " + value);
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Error setting value: " + e.getMessage());
                }
            }
            case "element" -> {
                if (args.length < 5) {
                    sender.sendMessage(ChatColor.RED + "Usage: /element config element <element> <key> <value>");
                    return true;
                }

                Optional<ElementType> elementType = CommandSupport.parseElementType(args[2]);
                if (elementType.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "Invalid element. Valid: " + String.join(", ", CommandSupport.getElementNames()));
                    return true;
                }

                String key = args[3];
                String value = args[4];

                try {
                    setElementConfig(sender, elementType.get(), key, value);
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Error setting element config: " + e.getMessage());
                }
            }
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown action: " + action);
                sender.sendMessage(ChatColor.GRAY + "Valid actions: reload, reset, set, element");
            }
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            // Note: matches original behavior of returning the full action list
            // unfiltered here, unlike every other subcommand's arg-2 completion.
            // Left as-is rather than silently changed - flagged separately.
            return List.of("reload", "reset", "set", "element");
        }
        if (args.length == 3) {
            if (args[1].equalsIgnoreCase("set")) {
                return CommandSupport.getConfigKeys(args[2]);
            }
            if (args[1].equalsIgnoreCase("element")) {
                return CommandSupport.getElementNames(args[2]);
            }
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("element")) {
            return CommandSupport.getElementConfigKeys(args[3]);
        }
        return Collections.emptyList();
    }

    private void setElementConfig(CommandSender sender, ElementType type, String key, String value) {
        String configPath = "elements." + type.name().toLowerCase() + "." + key;

        // Set the value based on type
        Object typedValue;
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            typedValue = Boolean.parseBoolean(value);
        } else {
            try {
                typedValue = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                typedValue = value;
            }
        }

        configManager.getConfig().set(configPath, typedValue);
        plugin.saveConfig();
        sender.sendMessage(ChatColor.GREEN + "Set " + type.name() + "." + key + " to " + value);

        // Reload config to apply changes
        configManager.reload();

        // Update the element configuration in memory
        configManager.getElementConfiguration().setConfigValue(type, key, typedValue);
    }
}
