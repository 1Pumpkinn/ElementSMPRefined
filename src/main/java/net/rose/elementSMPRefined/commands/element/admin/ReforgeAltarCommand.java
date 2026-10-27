package net.rose.elementSMPRefined.commands.element.admin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.altar.ReforgeAltarManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /reforgealtar &lt;spawn|here|remove|status|tp&gt; - admin tools for testing the Reforge Altar. */
public class ReforgeAltarCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("spawn", "here", "remove", "status", "tp");

    private final ReforgeAltarManager altar;

    public ReforgeAltarCommand(ReforgeAltarManager altar) {
        this.altar = altar;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("element.admin")) {
            sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /" + label + " <spawn|here|remove|status|tp>", NamedTextColor.YELLOW));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "spawn" -> {
                if (altar.spawnRandom()) {
                    sender.sendMessage(Component.text("Searching for a site - the altar will appear in a moment.", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("An altar already exists (or one is being placed). Use /" + label + " remove first.", NamedTextColor.RED));
                }
            }
            case "here" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this.", NamedTextColor.RED));
                    return true;
                }
                Location loc = player.getLocation();
                // Floor layer sits at the player's feet level (on top of the ground).
                if (altar.spawnAt(player.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
                    sender.sendMessage(Component.text("Placing the altar on you.", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("An altar already exists (or one is being placed). Use /" + label + " remove first.", NamedTextColor.RED));
                }
            }
            case "remove" -> {
                if (altar.completeAltar()) {
                    sender.sendMessage(Component.text("Altar removed and terrain restored.", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("There is no active altar.", NamedTextColor.RED));
                }
            }
            case "status" -> sendStatus(sender);
            case "tp" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this.", NamedTextColor.RED));
                    return true;
                }
                Location loc = altar.getLodestoneLocation();
                if (loc == null) {
                    sender.sendMessage(Component.text("There is no active altar.", NamedTextColor.RED));
                } else {
                    player.teleportAsync(loc.clone().add(0.5, 1.0, 3.5));
                }
            }
            default -> sender.sendMessage(Component.text("Usage: /" + label + " <spawn|here|remove|status|tp>", NamedTextColor.YELLOW));
        }
        return true;
    }

    private void sendStatus(CommandSender sender) {
        var active = altar.getActiveAltar();
        if (active == null) {
            sender.sendMessage(Component.text("No active altar.", NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("Active altar at " + active.lodestoneX() + ", " + active.lodestoneY() + ", "
                    + active.lodestoneZ() + " in " + active.worldName() + " (recipe: " + active.recipe().name() + ")", NamedTextColor.GOLD));
        }
        long msLeft = altar.getNextSpawnAtMillis() - System.currentTimeMillis();
        if (altar.getNextSpawnAtMillis() <= 0) {
            sender.sendMessage(Component.text("Next altar: not scheduled yet.", NamedTextColor.YELLOW));
        } else if (msLeft <= 0) {
            sender.sendMessage(Component.text("Next altar: due now.", NamedTextColor.YELLOW));
        } else {
            long minutes = msLeft / 60_000L;
            sender.sendMessage(Component.text("Next altar in " + (minutes / 60) + "h " + (minutes % 60) + "m.", NamedTextColor.YELLOW));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("element.admin")) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
