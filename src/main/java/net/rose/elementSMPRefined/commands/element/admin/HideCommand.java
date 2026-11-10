package net.rose.elementSMPRefined.commands.element.admin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.commands.supporters.CommandSupport;
import net.rose.elementSMPRefined.commands.supporters.ElementSubCommand;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;

import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * /element hide <element> | hide list, and /element unhide <element>.
 * Hidden elements are left out of /elements for everyone without element.admin, so elements that
 * aren't released yet don't show up. The list is stored in config.yml under
 * element_info.hidden_elements.
 */
public class HideCommand implements ElementSubCommand {
    private final ConfigManager configManager;
    private final boolean hiding;

    public HideCommand(ConfigManager configManager, boolean hiding) {
        this.configManager = configManager;
        this.hiding = hiding;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        String label = hiding ? "hide" : "unhide";
        if (args.length < 2) {
            sender.sendMessage(
                    Component.text(
                            "Usage: /element " + label + " <element>" + (hiding ? " | list" : ""),
                            NamedTextColor.YELLOW));
            return true;
        }

        if (hiding && args[1].equalsIgnoreCase("list")) {
            sendHiddenList(sender);
            return true;
        }

        Optional<ElementType> parsed = CommandSupport.parseElementType(args[1]);
        if (parsed.isEmpty()) {
            sender.sendMessage(
                    Component.text("Unknown element: " + args[1], NamedTextColor.RED));
            return true;
        }

        ElementType type = parsed.get();
        String name = type.name().toLowerCase(Locale.ROOT);
        if (!configManager.setInfoElementHidden(type, hiding)) {
            sender.sendMessage(
                    Component.text(
                            name + (hiding ? " is already hidden." : " is not hidden."),
                            NamedTextColor.YELLOW));
            return true;
        }

        sender.sendMessage(
                Component.text(
                        name + (hiding ? " is now hidden from /elements." : " is now shown in /elements."),
                        NamedTextColor.GREEN));
        return true;
    }

    private void sendHiddenList(CommandSender sender) {
        String names =
                configManager.getHiddenInfoElements().stream()
                        .map(type -> type.name().toLowerCase(Locale.ROOT))
                        .collect(Collectors.joining(", "));
        sender.sendMessage(
                Component.text(
                        names.isEmpty() ? "No elements are hidden." : "Hidden elements: " + names,
                        NamedTextColor.GRAY));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 2) {
            return List.of();
        }
        if (hiding) {
            List<String> options = new java.util.ArrayList<>();
            options.add("list");
            for (String name : CommandSupport.getElementNames()) {
                ElementType.parse(name)
                        .filter(type -> !configManager.isInfoElementHidden(type))
                        .ifPresent(type -> options.add(name));
            }
            return CommandSupport.filterStartingWith(options, args[1]);
        }
        List<String> hidden =
                configManager.getHiddenInfoElements().stream()
                        .map(type -> type.name().toLowerCase(Locale.ROOT))
                        .toList();
        return CommandSupport.filterStartingWith(hidden, args[1]);
    }
}
