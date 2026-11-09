package net.rose.elementSMPRefined.commands.element.admin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.commands.supporters.CommandSupport;
import net.rose.elementSMPRefined.commands.supporters.ElementSubCommand;
import net.rose.elementSMPRefined.util.server.pack.ResourcePackManager;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /element pack <status|resend [player|*]|reload>} - inspect the built-in resource pack
 * server, resend the pack to players, or restart the server after config.yml changes.
 */
public class PackCommand implements ElementSubCommand {
    private static final List<String> ACTIONS = List.of("status", "resend", "reload");

    private final ResourcePackManager packManager;

    public PackCommand(ResourcePackManager packManager) {
        this.packManager = packManager;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        String action = args.length < 2 ? "status" : args[1].toLowerCase();
        switch (action) {
            case "status" -> status(sender);
            case "resend" -> resend(sender, args);
            case "reload" -> {
                packManager.reload();
                sender.sendMessage(
                        Component.text(
                                packManager.isRunning()
                                        ? "Resource pack rebuilt and resent to everyone online."
                                        : "Resource pack is not running - check the console.",
                                packManager.isRunning()
                                        ? NamedTextColor.GREEN
                                        : NamedTextColor.RED));
            }
            default ->
                    sender.sendMessage(
                            Component.text(
                                    "Usage: /element pack <status|resend [player|*]|reload>",
                                    NamedTextColor.RED));
        }
        return true;
    }

    private void status(CommandSender sender) {
        if (!packManager.isRunning()) {
            sender.sendMessage(
                    Component.text(
                            "Resource pack is not running (disabled, bad config or the port is"
                                    + " taken - see the console).",
                            NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text("Resource pack is live", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("  URL: " + packManager.getUrl(), NamedTextColor.YELLOW));
        sender.sendMessage(
                Component.text("  SHA-1: " + packManager.getSha1(), NamedTextColor.YELLOW));
        sender.sendMessage(
                Component.text(
                        "  Size: " + packManager.getSizeBytes() / 1024 + " KB",
                        NamedTextColor.YELLOW));
    }

    private void resend(CommandSender sender, String[] args) {
        if (!packManager.isRunning()) {
            sender.sendMessage(Component.text("Resource pack is not running.", NamedTextColor.RED));
            return;
        }
        if (args.length < 3 || args[2].equals("*")) {
            packManager.sendToAll();
            sender.sendMessage(
                    Component.text("Resource pack resent to everyone online.", NamedTextColor.GREEN));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + args[2], NamedTextColor.RED));
            return;
        }
        packManager.send(target);
        sender.sendMessage(
                Component.text("Resource pack resent to " + target.getName() + ".", NamedTextColor.GREEN));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return CommandSupport.filterStartingWith(ACTIONS, args[1]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("resend")) {
            List<String> options = new ArrayList<>(CommandSupport.getOnlinePlayerNames(args[2]));
            if ("*".startsWith(args[2])) {
                options.add("*");
            }
            return options;
        }
        return List.of();
    }
}
