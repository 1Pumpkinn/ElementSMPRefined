package net.rose.elementSMPRefined.commands.element;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.commands.supporters.CommandSupport;
import net.rose.elementSMPRefined.commands.supporters.ElementSubCommand;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.lang.Lang;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * /element config reload | reset [key] | set <key> <value>
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
                sender.sendMessage(Lang.CONFIG_CONFIGURATION_RELOADED_SUCCESSFULLY);
            }
            case "reset" -> handleReset(sender, args);
            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage(Lang.CONFIG_USAGE_ELEMENT_CONFIG_SET_KEY);
                    return true;
                }
                String key = args[2];
                String value = args[3];

                try {
                    configManager.getConfig().set(key, parseValue(value));
                    plugin.saveConfig();
                    configManager.reload();
                    sender.sendMessage(Lang.configSet(key, value));
                } catch (Exception e) {
                    sender.sendMessage(Lang.configErrorSettingValue(e.getMessage()));
                }
            }
            default -> {
                sender.sendMessage(Lang.configUnknownAction(action));
                sendUsage(sender);
            }
        }

        return true;
    }

    private void handleReset(CommandSender sender, String[] args) {
        // /element config reset
        if (args.length == 2) {
            configManager.resetAllToDefault();
            sender.sendMessage(Lang.CONFIG_CONFIGURATION_RESET_DEFAULT_VALUES);
            return;
        }

        // /element config reset <key>
        String key = args[2];
        boolean reset = configManager.resetToDefault(key);
        if (reset) {
            sender.sendMessage(Lang.configReset2(key, configManager.getDefaultValue(key)));
        } else {
            sender.sendMessage(Lang.configNoDefaultValueExists2(key));
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
            return CommandSupport.filterStartingWith(List.of("reload", "reset", "set"), args[1]);
        }

        String action = args[1].toLowerCase();

        if (args.length == 3 && (action.equals("set") || action.equals("reset"))) {
            return CommandSupport.getConfigKeys(args[2]);
        }

        if (args.length == 4 && action.equals("set")) {
            return defaultValueSuggestion(args[2]);
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

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Lang.CONFIG_USAGE_ELEMENT_CONFIG_ACTION);
        sender.sendMessage(Lang.CONFIG_ACTIONS);
        sender.sendMessage(Lang.CONFIG_RELOAD);
        sender.sendMessage(Lang.CONFIG_RESET_KEY_OMIT_KEY_RESET);
        sender.sendMessage(Lang.CONFIG_SET_KEY_VALUE);
    }
}
