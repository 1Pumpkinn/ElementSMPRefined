package net.rose.elementevents.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementevents.CustomEventVillager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * /eventvillager &lt;spawn|settype|remove|list|types|reload&gt; - operators only
 * ({@code element.eventvillager}, default op).
 */
public class EventVillagerCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "element.eventvillager";
    private static final List<String> SUBCOMMANDS = List.of("spawn", "settype", "remove", "list", "types", "reload");
    private static final double REMOVE_RANGE = 6.0;

    private final CustomEventVillager villagers;

    public EventVillagerCommand(ElementSMPRefined plugin, CustomEventVillager villagers) {
        this.villagers = villagers;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(usage(label));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "spawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /" + label + " spawn <type> [name]. Types: " + typeList(), NamedTextColor.YELLOW));
                    return true;
                }
                String type = args[1].toLowerCase(Locale.ROOT);
                if (villagers.getConfig().getType(type) == null) {
                    sender.sendMessage(Component.text("Unknown type '" + type + "'. Types: " + typeList(), NamedTextColor.RED));
                    return true;
                }
                String name = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : null;

                // Face the villager back towards the player.
                Location at = player.getLocation().clone();
                at.setYaw(at.getYaw() + 180f);
                villagers.spawn(at, type, name);
                sender.sendMessage(Component.text("Spawned a '" + type + "' event villager.", NamedTextColor.GREEN));
            }
            case "settype" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /" + label + " settype <type> (look at a villager). Types: " + typeList(), NamedTextColor.YELLOW));
                    return true;
                }
                String type = args[1].toLowerCase(Locale.ROOT);
                Villager target = findTarget(player);
                if (target == null) {
                    sender.sendMessage(noTarget());
                } else if (!villagers.setType(target, type, null)) {
                    sender.sendMessage(Component.text("Unknown type '" + type + "'. Types: " + typeList(), NamedTextColor.RED));
                } else {
                    sender.sendMessage(Component.text("Villager is now a '" + type + "' villager.", NamedTextColor.GREEN));
                }
            }
            case "remove" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this.", NamedTextColor.RED));
                    return true;
                }
                Villager target = findTarget(player);
                if (target == null) {
                    sender.sendMessage(noTarget());
                } else {
                    target.remove();
                    sender.sendMessage(Component.text("Event villager removed.", NamedTextColor.GREEN));
                }
            }
            case "list" -> {
                List<Villager> loaded = villagers.getLoaded();
                if (loaded.isEmpty()) {
                    sender.sendMessage(Component.text("No loaded event villagers.", NamedTextColor.YELLOW));
                    return true;
                }
                sender.sendMessage(Component.text("Loaded event villagers (" + loaded.size() + "):", NamedTextColor.GOLD));
                for (Villager v : loaded) {
                    Location l = v.getLocation();
                    sender.sendMessage(Component.text(" - [" + villagers.getType(v) + "] " + l.getWorld().getName()
                            + " " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ(), NamedTextColor.YELLOW));
                }
            }
            case "types" -> sender.sendMessage(Component.text("Villager types: " + typeList(), NamedTextColor.GOLD));
            case "reload" -> {
                if (villagers.reload()) {
                    sender.sendMessage(Component.text("Reloaded event_villagers.yml (" + villagers.getConfig().getTypeIds().size() + " types).", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("event_villagers.yml has an error - kept the old types. Check the console.", NamedTextColor.RED));
                }
            }
            default -> sender.sendMessage(usage(label));
        }
        return true;
    }

    private String typeList() {
        var ids = villagers.getConfig().getTypeIds();
        return ids.isEmpty() ? "(none defined)" : String.join(", ", ids);
    }

    private Component noTarget() {
        return Component.text("Look at an event villager (within " + (int) REMOVE_RANGE + " blocks).", NamedTextColor.RED);
    }

    /** The event villager the player is looking at, else the closest one within range. */
    private Villager findTarget(Player player) {
        Entity looked = player.getTargetEntity((int) REMOVE_RANGE);
        if (looked != null && villagers.isEventVillager(looked)) {
            return (Villager) looked;
        }
        return player.getNearbyEntities(REMOVE_RANGE, REMOVE_RANGE, REMOVE_RANGE).stream()
                .filter(villagers::isEventVillager)
                .map(e -> (Villager) e)
                .min(Comparator.comparingDouble(v -> v.getLocation().distanceSquared(player.getLocation())))
                .orElse(null);
    }

    private Component usage(String label) {
        return Component.text("Usage: /" + label + " <spawn <type> [name]|settype <type>|remove|list|types|reload>", NamedTextColor.YELLOW);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) return List.of();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("spawn") || args[0].equalsIgnoreCase("settype"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return villagers.getConfig().getTypeIds().stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
