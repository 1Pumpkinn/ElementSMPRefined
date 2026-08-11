package hs.elementSMPRefined.commands;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.data.DataStore;
import hs.elementSMPRefined.elements.ElementType;
import hs.elementSMPRefined.gui.ElementSelectionGUI;
import hs.elementSMPRefined.managers.ConfigManager;
import hs.elementSMPRefined.managers.ElementManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class ElementCommand implements CommandExecutor, TabCompleter {
    private final ElementSMPRefined plugin;
    private final DataStore dataStore;
    private final ElementManager elementManager;
    private final ConfigManager configManager;
    private final Map<String, SubCommand> subCommands;

    public ElementCommand(ElementSMPRefined plugin) {
        this.plugin = plugin;
        this.dataStore = plugin.getDataStore();
        this.elementManager = plugin.getElementManager();
        this.configManager = plugin.getConfigManager();
        this.subCommands = initializeSubCommands();
    }

    private Map<String, SubCommand> initializeSubCommands() {
        Map<String, SubCommand> commands = new HashMap<>();
        commands.put("set", new SetCommand());
        commands.put("debug", new DebugCommand());
        commands.put("roll", new RollCommand());
        commands.put("config", new ConfigCommand());
        return commands;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("element.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        SubCommand subCommand = subCommands.get(args[0].toLowerCase());
        if (subCommand != null) {
            return subCommand.execute(sender, args);
        }

        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Element Admin Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/element set <player> <element> - Set player's element");
        sender.sendMessage(ChatColor.YELLOW + "/element debug <player> - Debug player's element data");
        sender.sendMessage(ChatColor.YELLOW + "/element roll - Roll for a new element (OP only)");
        sender.sendMessage(ChatColor.YELLOW + "/element config <action> - Configuration management");
        sender.sendMessage(ChatColor.GRAY + "  Actions: reload, reset, set <key> <value>, element <element> <key> <value>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("element.admin")) {
            return Collections.emptyList();
        }

        return switch (args.length) {
            case 1 -> filterStartingWith(subCommands.keySet(), args[0]);
            case 2 -> {
                String subCmd = args[0].toLowerCase();
                if (subCommands.containsKey(subCmd)) {
                    if (subCmd.equals("roll")) {
                        yield Collections.emptyList();
                    }
                    if (subCmd.equals("config")) {
                        yield List.of("reload", "reset", "set", "element");
                    }
                    yield getOnlinePlayerNames(args[1]);
                }
                yield Collections.emptyList();
            }
            case 3 -> {
                String subCmd = args[0].toLowerCase();
                if (subCmd.equals("set")) {
                    yield filterStartingWith(getElementNames(), args[2]);
                }
                if (subCmd.equals("config") && args[1].equalsIgnoreCase("set")) {
                    yield getConfigKeys(args[2]);
                }
                if (subCmd.equals("config") && args[1].equalsIgnoreCase("element")) {
                    yield getElementNames(args[2]);
                }
                yield Collections.emptyList();
            }
            case 4 -> {
                String subCmd = args[0].toLowerCase();
                if (subCmd.equals("config") && args[1].equalsIgnoreCase("element")) {
                    yield getElementConfigKeys(args[3]);
                }
                yield Collections.emptyList();
            }
            default -> Collections.emptyList();
        };
    }

    private List<String> getConfigKeys(String prefix) {
        return List.of("mana.max", "mana.regen_per_second", "status_effects.enabled", 
                "status_effects.damage_per_tick", "status_effects.notification_messages",
                "recipes.advanced_reroller_enabled").stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .toList();
    }

    private List<String> getElementConfigKeys(String prefix) {
        return List.of("ability1_cost", "ability2_cost", "is_basic", "enabled", "display_name", "description", "color")
                .stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .toList();
    }

    private List<String> filterStartingWith(Collection<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    private List<String> getOnlinePlayerNames(String prefix) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    private List<String> getElementNames() {
        return Arrays.stream(ElementType.values())
                .map(type -> type.name().toLowerCase())
                .collect(Collectors.toList());
    }

    private List<String> getElementNames(String prefix) {
        return Arrays.stream(ElementType.values())
                .map(type -> type.name().toLowerCase())
                .filter(name -> name.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    private Optional<ElementType> parseElementType(String input) {
        try {
            return Optional.of(ElementType.valueOf(input.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private interface SubCommand {
        boolean execute(CommandSender sender, String[] args);
    }

    private class SetCommand implements SubCommand {
        @Override
        public boolean execute(CommandSender sender, String[] args) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /element set <player> <element>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player '" + args[1] + "' not found.");
                return true;
            }

            Optional<ElementType> elementType = parseElementType(args[2]);
            if (elementType.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "Invalid element. Valid: " + String.join(", ", getElementNames()));
                return true;
            }

            plugin.getLogger().info(String.format("[ElementCommand] Setting element for %s (%s) to %s",
                    target.getName(), target.getUniqueId(), elementType.get().name()));

            elementManager.setElement(target, elementType.get());

            sender.sendMessage(ChatColor.GREEN + "Set " + target.getName() + "'s element to " +
                    ChatColor.AQUA + elementType.get().name());
            target.sendMessage(ChatColor.GREEN + "Your element has been set to " +
                    ChatColor.AQUA + elementType.get().name() + ChatColor.GREEN + " by an admin.");

            return true;
        }
    }

    private class DebugCommand implements SubCommand {
        @Override
        public boolean execute(CommandSender sender, String[] args) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /element debug <player>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player '" + args[1] + "' not found.");
                return true;
            }

            sender.sendMessage(ChatColor.GOLD + "=== Element Debug for " + target.getName() + " ===");

            ElementType managerElement = elementManager.getPlayerElement(target);
            sender.sendMessage(ChatColor.YELLOW + "ElementManager reports: " +
                    (managerElement != null ? managerElement.name() : "null"));

            dataStore.invalidateCache(target.getUniqueId());
            ElementType reloadedElement = elementManager.getPlayerElement(target);
            sender.sendMessage(ChatColor.YELLOW + "After cache invalidation: " +
                    (reloadedElement != null ? reloadedElement.name() : "null"));

            return true;
        }
    }

    private class RollCommand implements SubCommand {
        @Override
        public boolean execute(CommandSender sender, String[] args) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
                return true;
            }

            if (!player.isOp()) {
                sender.sendMessage(ChatColor.RED + "You must be OP to use this command.");
                return true;
            }

            if (elementManager.isCurrentlyRolling(player)) {
                player.sendMessage(ChatColor.RED + "You are already rolling for an element!");
                return true;
            }

            new ElementSelectionGUI(plugin, player, true).open();
            player.sendMessage(ChatColor.GREEN + "Rolling for a new element...");

            return true;
        }
    }

    private class ConfigCommand implements SubCommand {
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
                    break;
                }
                case "reset" -> {
                    configManager.reload();
                    sender.sendMessage(ChatColor.GREEN + "Configuration reset to file values!");
                    break;
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
                    break;
                }
                case "element" -> {
                    if (args.length < 5) {
                        sender.sendMessage(ChatColor.RED + "Usage: /element config element <element> <key> <value>");
                        return true;
                    }
                    
                    Optional<ElementType> elementType = parseElementType(args[2]);
                    if (elementType.isEmpty()) {
                        sender.sendMessage(ChatColor.RED + "Invalid element. Valid: " + String.join(", ", getElementNames()));
                        return true;
                    }
                    
                    String key = args[3];
                    String value = args[4];
                    
                    try {
                        setElementConfig(sender, elementType.get(), key, value);
                    } catch (Exception e) {
                        sender.sendMessage(ChatColor.RED + "Error setting element config: " + e.getMessage());
                    }
                    break;
                }
                default -> {
                    sender.sendMessage(ChatColor.RED + "Unknown action: " + action);
                    sender.sendMessage(ChatColor.GRAY + "Valid actions: reload, reset, set, element");
                    break;
                }
            }

            return true;
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
}