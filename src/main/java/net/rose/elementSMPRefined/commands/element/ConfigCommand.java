package net.rose.elementSMPRefined.commands.element;

import net.rose.elementSMPRefined.API.element.ElementType;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.managers.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * /element config reload | reset [key] | reset element <element> [key]
 *              | set <key> <value> | element <element> <key> <value>
 *
 * "reset" always restores the value(s) the plugin ships with (config.yml
 * bundled in the jar), never just whatever happens to already be on disk -
 * see {@link ConfigManager#resetToDefault}.
 */
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
            sendUsage(sender);
            return true;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "reload" -> {
                configManager.reload();
                sender.sendMessage(ChatColor.GREEN + "Configuration reloaded successfully!");
            }
            case "reset" -> handleReset(sender, args);
            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /element config set <key> <value>");
                    return true;
                }
                String key = args[2];
                String value = args[3];

                try {
                    configManager.getConfig().set(key, parseValue(value));
                    plugin.saveConfig();
                    configManager.reload();
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
                sendUsage(sender);
            }
        }

        return true;
    }

    private void handleReset(CommandSender sender, String[] args) {
        // /element config reset
        if (args.length == 2) {
            configManager.resetAllToDefault();
            sender.sendMessage(ChatColor.GREEN + "Configuration reset to default values!");
            return;
        }

        // /element config reset element ...
        if (args[2].equalsIgnoreCase("element")) {
            if (args.length < 4) {
                sender.sendMessage(ChatColor.RED + "Usage: /element config reset element <element> [key]");
                return;
            }

            Optional<ElementType> elementType = CommandSupport.parseElementType(args[3]);
            if (elementType.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "Invalid element. Valid: " + String.join(", ", CommandSupport.getElementNames()));
                return;
            }
            String elementName = elementType.get().name().toLowerCase();

            if (args.length == 4) {
                // Reset the whole element section
                boolean reset = configManager.resetSectionToDefault("elements." + elementName);
                if (reset) {
                    sender.sendMessage(ChatColor.GREEN + "Reset all of " + elementType.get().name() + " to default values!");
                } else {
                    sender.sendMessage(ChatColor.RED + "No default config exists for " + elementType.get().name() + ".");
                }
                return;
            }

            // Reset a single key on that element
            String key = args[4];
            String path = "elements." + elementName + "." + key;
            boolean reset = configManager.resetToDefault(path);
            if (reset) {
                Object def = configManager.getDefaultValue(path);
                sender.sendMessage(ChatColor.GREEN + "Reset " + elementType.get().name() + "." + key + " to default (" + def + ").");
                configManager.getElementConfiguration().setConfigValue(elementType.get(), key, def);
            } else {
                sender.sendMessage(ChatColor.RED + "No default value exists for '" + key + "' on " + elementType.get().name() + ".");
            }
            return;
        }

        // /element config reset <key>
        String key = args[2];
        boolean reset = configManager.resetToDefault(key);
        if (reset) {
            sender.sendMessage(ChatColor.GREEN + "Reset " + key + " to default (" + configManager.getDefaultValue(key) + ").");
        } else {
            sender.sendMessage(ChatColor.RED + "No default value exists for '" + key + "'.");
        }
    }

    private Object parseValue(String value) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(value);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return CommandSupport.filterStartingWith(List.of("reload", "reset", "set", "element"), args[1]);
        }

        String action = args[1].toLowerCase();

        if (args.length == 3) {
            if (action.equals("set")) {
                return CommandSupport.getConfigKeys(args[2]);
            }
            if (action.equals("element")) {
                return CommandSupport.getElementNames(args[2]);
            }
            if (action.equals("reset")) {
                // Both plain global keys and the "element" sub-action are valid here.
                List<String> options = new ArrayList<>(CommandSupport.getConfigKeys(args[2]));
                options.addAll(CommandSupport.filterStartingWith(List.of("element"), args[2]));
                return options;
            }
        }

        if (args.length == 4) {
            if (action.equals("set")) {
                return defaultValueSuggestion(args[2]);
            }
            if (action.equals("element")) {
                return CommandSupport.getElementConfigKeys(args[3]);
            }
            if (action.equals("reset") && args[2].equalsIgnoreCase("element")) {
                return CommandSupport.getElementNames(args[3]);
            }
        }

        if (args.length == 5) {
            if (action.equals("element")) {
                return defaultValueSuggestion("elements." + args[2].toLowerCase() + "." + args[3]);
            }
            if (action.equals("reset") && args[2].equalsIgnoreCase("element")) {
                return CommandSupport.getElementConfigKeys(args[4]);
            }
        }

        return Collections.emptyList();
    }

    /** Suggests the shipped default value for a dotted path, so admins can tab-complete back to it. */
    private List<String> defaultValueSuggestion(String path) {
        Object def = configManager.getDefaultValue(path);
        if (def == null) {
            return Collections.emptyList();
        }
        if (def instanceof Boolean b) {
            return List.of(def.toString(), String.valueOf(!b));
        }
        return List.of(def.toString());
    }

    private void setElementConfig(CommandSender sender, ElementType type, String key, String value) {
        String configPath = "elements." + type.name().toLowerCase() + "." + key;
        Object typedValue = parseValue(value);

        configManager.getConfig().set(configPath, typedValue);
        plugin.saveConfig();
        sender.sendMessage(ChatColor.GREEN + "Set " + type.name() + "." + key + " to " + value);

        // Reload config to apply changes
        configManager.reload();

        // Update the element configuration in memory
        configManager.getElementConfiguration().setConfigValue(type, key, typedValue);
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Usage: /element config <action>");
        sender.sendMessage(ChatColor.GRAY + "Actions:");
        sender.sendMessage(ChatColor.GRAY + "  reload");
        sender.sendMessage(ChatColor.GRAY + "  reset [key]  -  omit key to reset everything");
        sender.sendMessage(ChatColor.GRAY + "  reset element <element> [key]");
        sender.sendMessage(ChatColor.GRAY + "  set <key> <value>");
        sender.sendMessage(ChatColor.GRAY + "  element <element> <key> <value>");
    }
}
