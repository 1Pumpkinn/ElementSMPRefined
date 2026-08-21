package hs.elementSMPRefined.commands;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.data.DataStore;
import hs.elementSMPRefined.API.element.ElementId;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.gui.ElementSelectionGUI;
import hs.elementSMPRefined.items.ElementCoreItem;
import hs.elementSMPRefined.managers.ConfigManager;
import hs.elementSMPRefined.managers.ElementManager;
import hs.elementSMPRefined.util.visual.ParticlePreset;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
        commands.put("particles", new ParticlesCommand());
        commands.put("set", new SetCommand());
        commands.put("debug", new DebugCommand());
        commands.put("roll", new RollCommand());
        commands.put("config", new ConfigCommand());
        commands.put("givecore", new GiveCoreCommand());
        return commands;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("particles")) {
            SubCommand subCommand = subCommands.get("particles");
            if (subCommand != null) {
                return subCommand.execute(sender, args);
            }
        }

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
        sender.sendMessage(ChatColor.YELLOW + "/element particles <preset> - Preview a particle pattern at your feet");
        sender.sendMessage(ChatColor.YELLOW + "/element set <player> <element> - Set player's element");
        sender.sendMessage(ChatColor.YELLOW + "/element debug <player> - Debug player's element data");
        sender.sendMessage(ChatColor.YELLOW + "/element roll - Roll for a new element (OP only)");
        sender.sendMessage(ChatColor.YELLOW + "/element config <action> - Configuration management");
        sender.sendMessage(ChatColor.GRAY + "  Actions: reload, reset, set <key> <value>, element <element> <key> <value>");
        sender.sendMessage(ChatColor.YELLOW + "/element givecore <player> <element> - Give a player an element core item");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            if (sender.hasPermission("element.admin") || sender.hasPermission("element.particles") || sender instanceof Player) {
                return filterStartingWith(subCommands.keySet(), args[0]);
            }
            return Collections.emptyList();
        }

        if (args[0].equalsIgnoreCase("particles")) {
            return getParticlesTabCompletion(args);
        }

        if (!sender.hasPermission("element.admin")) {
            return Collections.emptyList();
        }

        return switch (args.length) {
            case 2 -> {
                String subCmd = args[0].toLowerCase();
                if (subCmd.equals("roll")) {
                    yield Collections.emptyList();
                }
                if (subCmd.equals("config")) {
                    yield List.of("reload", "reset", "set", "element");
                }
                if (subCommands.containsKey(subCmd)) {
                    yield getOnlinePlayerNames(args[1]);
                }
                yield Collections.emptyList();
            }
            case 3 -> {
                String subCmd = args[0].toLowerCase();
                if (subCmd.equals("set")) {
                    yield filterStartingWith(getAllElementNames(), args[2]);
                }
                if (subCmd.equals("givecore")) {
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

    private List<String> getParticlesTabCompletion(String[] args) {
        if (args.length == 2) {
            return ParticlePreset.filterNames(args[1]);
        }

        if (args.length >= 3 && args.length <= 5) {
            return filterStartingWith(List.of("0.5", "1", "1.5", "2", "2.5", "3", "4", "5", "6", "8", "10"), args[args.length - 1]);
        }

        if (args.length == 6) {
            return filterStartingWith(List.of("white", "red", "orange", "yellow", "green", "lime", "aqua", "cyan",
                    "blue", "purple", "magenta", "pink", "black", "gray", "grey", "silver", "#ff0000", "#00ff00", "#0000ff"), args[5]);
        }

        if (args.length == 7) {
            return filterStartingWith(getParticleNameSuggestions(), args[6]);
        }

        return Collections.emptyList();
    }

    private List<String> getParticleNameSuggestions() {
        List<String> suggestions = new ArrayList<>();
        for (Particle particle : Particle.values()) {
            suggestions.add(particle.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        }
        suggestions.add("dust");
        suggestions.add("glow");
        suggestions.add("soul-fire-flame");
        suggestions.add("dripping-lava");
        return suggestions.stream().distinct().toList();
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

    /**
     * Resolves either a bare builtin name ("fire") or a namespaced addon ID
     * ("elementevents:storm") to an {@link ElementId}. Used by {@code /element set}
     * so admins can target addon elements, not just the 8 builtins.
     */
    private Optional<ElementId> parseElementId(String input) {
        Optional<ElementType> builtin = parseElementType(input);
        if (builtin.isPresent()) {
            return Optional.of(ElementId.builtin(builtin.get()));
        }
        try {
            ElementId id = ElementId.parse(input);
            return elementManager.getElementRegistry().isRegistered(id) ? Optional.of(id) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Builtin names plus every registered addon element's "namespace:key" ID. */
    private List<String> getAllElementNames() {
        List<String> names = new ArrayList<>(getElementNames());
        for (ElementId id : elementManager.getElementRegistry().getAllIds()) {
            if (id.toBuiltinType() == null) {
                names.add(id.toString());
            }
        }
        return names;
    }

    private interface SubCommand {
        boolean execute(CommandSender sender, String[] args);
    }

    private class ParticlesCommand implements SubCommand {
        @Override
        public boolean execute(CommandSender sender, String[] args) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
                return true;
            }

            if (args.length == 1) {
                sendPresetList(player);
                return true;
            }

            Optional<ParticlePreset> preset = ParticlePreset.fromName(args[1]);
            if (preset.isEmpty()) {
                sendPresetList(player);
                return true;
            }

            ParticlePreset.PresetOptions options = parsePresetOptions(args);
            preset.get().play(player, plugin, options);
            player.sendMessage(ChatColor.GREEN + "Playing particle preset: " + ChatColor.AQUA + preset.get().getKey());
            return true;
        }

        private ParticlePreset.PresetOptions parsePresetOptions(String[] args) {
            ParticlePreset preset = ParticlePreset.fromName(args[1]).orElse(ParticlePreset.CIRCLE);
            ParticlePreset.PresetOptions defaults = ParticlePreset.PresetOptions.defaults(preset);

            double size = defaults.sizeOr(1.0);
            double length = defaults.lengthOr(0.0);
            double width = defaults.widthOr(1.0);
            Particle particle = defaults.particle();
            Color color = defaults.color();

            for (int i = 2; i < args.length; i++) {
                String arg = args[i];
                if (arg == null || arg.isBlank()) {
                    continue;
                }

                if (arg.startsWith("size=") || arg.equalsIgnoreCase("size")) {
                    String value = arg.contains("=") ? arg.substring(arg.indexOf('=') + 1) : (i + 1 < args.length ? args[++i] : "");
                    if (!value.isBlank()) {
                        try {
                            size = Double.parseDouble(value);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    continue;
                }

                if (arg.startsWith("length=") || arg.equalsIgnoreCase("length")) {
                    String value = arg.contains("=") ? arg.substring(arg.indexOf('=') + 1) : (i + 1 < args.length ? args[++i] : "");
                    if (!value.isBlank()) {
                        try {
                            length = Double.parseDouble(value);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    continue;
                }

                if (arg.startsWith("width=") || arg.equalsIgnoreCase("width")) {
                    String value = arg.contains("=") ? arg.substring(arg.indexOf('=') + 1) : (i + 1 < args.length ? args[++i] : "");
                    if (!value.isBlank()) {
                        try {
                            width = Double.parseDouble(value);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    continue;
                }

                if (arg.startsWith("color=") || arg.equalsIgnoreCase("color")) {
                    String value = arg.contains("=") ? arg.substring(arg.indexOf('=') + 1) : (i + 1 < args.length ? args[++i] : "");
                    color = parseColor(value);
                    continue;
                }

                if (arg.startsWith("particle=") || arg.equalsIgnoreCase("particle")) {
                    String value = arg.contains("=") ? arg.substring(arg.indexOf('=') + 1) : (i + 1 < args.length ? args[++i] : "");
                    particle = parseParticle(value);
                    continue;
                }

                if (i == 2) {
                    try {
                        size = Double.parseDouble(arg);
                    } catch (NumberFormatException ignored) {
                    }
                    continue;
                }
                if (i == 3) {
                    try {
                        length = Double.parseDouble(arg);
                    } catch (NumberFormatException ignored) {
                    }
                    continue;
                }
                if (i == 4) {
                    try {
                        width = Double.parseDouble(arg);
                    } catch (NumberFormatException ignored) {
                    }
                    continue;
                }
                if (i == 5) {
                    color = parseColor(arg);
                    continue;
                }
                if (i == 6) {
                    particle = parseParticle(arg);
                }
            }

            return new ParticlePreset.PresetOptions(size, length, width, particle, color);
        }

        private Color parseColor(String input) {
            if (input == null || input.isBlank()) {
                return null;
            }

            String normalized = input.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "white" -> Color.WHITE;
                case "silver", "lightgray" -> Color.SILVER;
                case "gray", "grey" -> Color.GRAY;
                case "black" -> Color.BLACK;
                case "red" -> Color.RED;
                case "orange" -> Color.ORANGE;
                case "yellow" -> Color.YELLOW;
                case "green" -> Color.GREEN;
                case "lime" -> Color.LIME;
                case "aqua", "cyan" -> Color.AQUA;
                case "blue" -> Color.BLUE;
                case "purple", "magenta" -> Color.PURPLE;
                case "pink" -> Color.FUCHSIA;
                default -> {
                    if (normalized.startsWith("#") && normalized.length() == 7) {
                        try {
                            int r = Integer.parseInt(normalized.substring(1, 3), 16);
                            int g = Integer.parseInt(normalized.substring(3, 5), 16);
                            int b = Integer.parseInt(normalized.substring(5, 7), 16);
                            yield Color.fromRGB(r, g, b);
                        } catch (NumberFormatException ignored) {
                            yield null;
                        }
                    }
                    yield null;
                }
            };
        }

        private Particle parseParticle(String input) {
            if (input == null || input.isBlank()) {
                return null;
            }

            String normalized = input.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            try {
                return Particle.valueOf(normalized);
            } catch (IllegalArgumentException e) {
                return switch (normalized) {
                    case "DUST", "REDSTONE" -> Particle.DUST;
                    case "GLOW" -> Particle.GLOW;
                    case "SOULFIRE" -> Particle.SOUL_FIRE_FLAME;
                    case "DRIPLAVA" -> Particle.DRIPPING_LAVA;
                    default -> null;
                };
            }
        }

        private void sendPresetList(Player player) {
            player.sendMessage(ChatColor.GOLD + "=== Element Particle Presets ===");
            player.sendMessage(ChatColor.YELLOW + "Available: " + String.join(", ", ParticlePreset.getNames()));
            player.sendMessage(ChatColor.GRAY + "Usage: /element particles <preset> [size] [length] [width] [color] [particle]");
            player.sendMessage(ChatColor.GRAY + "Also supported: size=2.5 length=0 width=36 color=red particle=dust");
            player.sendMessage(ChatColor.GRAY + "Example: /element particles circle 2.5 0 36 red dust");
        }
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

            Optional<ElementId> elementId = parseElementId(args[2]);
            if (elementId.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "Invalid element. Valid: " + String.join(", ", getAllElementNames()));
                return true;
            }

            ElementId id = elementId.get();
            elementManager.setElement(target, id);

            var element = elementManager.getElementRegistry().get(id);
            String displayName = element != null ? element.getDisplayName() : id.toString();

            sender.sendMessage(ChatColor.GREEN + "Set " + target.getName() + "'s element to " +
                    ChatColor.AQUA + displayName);
            target.sendMessage(ChatColor.GREEN + "Your element has been set to " +
                    ChatColor.AQUA + displayName + ChatColor.GREEN + " by an admin.");

            return true;
        }
    }

    private class GiveCoreCommand implements SubCommand {
        @Override
        public boolean execute(CommandSender sender, String[] args) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /element givecore <player> <element>");
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

            ItemStack core = ElementCoreItem.createCore(plugin, elementType.get());
            if (core == null) {
                sender.sendMessage(ChatColor.RED + elementType.get().name() + " doesn't have a core item.");
                return true;
            }

            target.getInventory().addItem(core);

            sender.sendMessage(ChatColor.GREEN + "Gave " + target.getName() + " a " +
                    ChatColor.AQUA + elementType.get().name() + ChatColor.GREEN + " core.");
            target.sendMessage(ChatColor.GREEN + "You received a " +
                    ChatColor.AQUA + elementType.get().name() + ChatColor.GREEN + " core from an admin.");

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
            sender.sendMessage(ChatColor.YELLOW + "ElementManager reports (builtin type): " +
                    (managerElement != null ? managerElement.name() : "null"));

            ElementId managerElementId = elementManager.getPlayerElementId(target);
            sender.sendMessage(ChatColor.YELLOW + "ElementManager reports (element ID): " +
                    (managerElementId != null ? managerElementId.toString() : "null"));

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