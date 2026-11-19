package net.rose.elementSMPRefined.weapon.disc;

import static net.kyori.adventure.text.Component.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** {@code /throwingdisc <give [player]|status|reset confirm>} - OP by default. */
final class ThrowingDiscCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("give", "status", "reset");

    private final ThrowingDiscSystem system;

    ThrowingDiscCommand(ThrowingDiscSystem system) {
        this.system = system;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("element.admin")) {
            sender.sendMessage(text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(usage());
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, args);
            case "status" -> status(sender);
            case "reset" -> reset(sender, args);
            default -> sender.sendMessage(usage());
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(text("Player not found.", NamedTextColor.RED));
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            sender.sendMessage(usage());
            return;
        }
        if (system.registry().isIssued()) {
            sender.sendMessage(
                    text(
                            "The throwing disc already exists. If it is truly lost, use /throwingdisc reset confirm first.",
                            NamedTextColor.RED));
            return;
        }
        system.registry().markIssued();
        system.give(target, ThrowingDisc.make(system.plugin(), DiscTier.DIAMOND));
        target.sendMessage(text("You have been given the Diamond Throwing Disc.", NamedTextColor.GREEN));
        if (target != sender) {
            sender.sendMessage(text("Gave the throwing disc to " + target.getName() + ".", NamedTextColor.GREEN));
        }
    }

    private void status(CommandSender sender) {
        boolean issued = system.registry().isIssued();
        sender.sendMessage(
                text("Throwing disc: ", NamedTextColor.GRAY)
                        .append(
                                issued
                                        ? text("already exists", NamedTextColor.YELLOW)
                                        : text("not issued yet (craftable)", NamedTextColor.GREEN)));
        UUID flying = system.flyingOwner();
        if (flying != null) {
            Player owner = Bukkit.getPlayer(flying);
            sender.sendMessage(
                    text(
                            "In the air right now, thrown by "
                                    + (owner != null ? owner.getName() : flying),
                            NamedTextColor.GRAY));
        }
    }

    private void reset(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            sender.sendMessage(
                    text(
                            "This lets a new disc be crafted or given, so only use it if the disc is lost. "
                                    + "Run /throwingdisc reset confirm to do it.",
                            NamedTextColor.YELLOW));
            return;
        }
        system.registry().reset();
        sender.sendMessage(text("Throwing disc reset: it can be crafted or given again.", NamedTextColor.GREEN));
    }

    private static Component usage() {
        return text("Usage: /throwingdisc <give [player]|status|reset confirm>", NamedTextColor.RED);
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("element.admin")) return out;
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String sub : SUBCOMMANDS) if (sub.startsWith(prefix)) out.add(sub);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(p.getName());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            out.add("confirm");
        }
        return out;
    }
}
