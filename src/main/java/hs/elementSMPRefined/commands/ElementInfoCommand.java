package hs.elementSMPRefined.commands;

import hs.elementSMPRefined.API.element.Element;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.managers.ConfigManager;
import hs.elementSMPRefined.managers.ElementManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * /elements <element> - shows an element's description, passives, and abilities.
 * Reads everything straight from the registered {@link Element}, so this command
 * never needs updating when an element's text or costs change elsewhere.
 */
public class ElementInfoCommand implements CommandExecutor, TabCompleter {

    private final ElementManager elementManager;
    private final ConfigManager configManager;

    public ElementInfoCommand(ElementSMPRefined plugin) {
        this.elementManager = plugin.getElementManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /elements <element>")
                    .color(NamedTextColor.YELLOW));
            return true;
        }

        showElementDetails(player, args[0]);
        return true;
    }

    private void showElementDetails(Player player, String elementName) {
        ElementType type;
        try {
            type = ElementType.valueOf(elementName.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("\u274C Unknown element: " + elementName)
                    .color(NamedTextColor.RED));
            player.sendMessage(Component.text("Use /elements <element>")
                    .color(NamedTextColor.GRAY));
            return;
        }

        Element element = elementManager.get(type);
        if (element == null) {
            player.sendMessage(Component.text("\u274C No information available for " + type.name())
                    .color(NamedTextColor.RED));
            return;
        }

        // Header
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("\u2726 " + type.name() + " ELEMENT \u2726")
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        player.sendMessage(Component.empty());

        // Description
        player.sendMessage(Component.text("\uD83D\uDCD6 " + strip(element.getDescription()))
                .color(NamedTextColor.GRAY));
        player.sendMessage(Component.empty());

        // Passive Benefits
        if (!element.getPassiveBenefits().isEmpty()) {
            player.sendMessage(Component.text("\u2B50 Passive Benefits:")
                    .color(NamedTextColor.GOLD)
                    .decorate(TextDecoration.BOLD));
            for (String upside : element.getPassiveBenefits()) {
                player.sendMessage(Component.text("  \u2022 " + upside)
                        .color(NamedTextColor.GREEN));
            }
            player.sendMessage(Component.empty());
        }

        // Abilities
        player.sendMessage(Component.text("\u26A1 Abilities:")
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));

        player.sendMessage(Component.text("  \u2460 " + strip(element.getAbility1Name()))
                .color(NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD));
        player.sendMessage(Component.text("     " + strip(element.getAbility1Description()))
                .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("     Mana: " + configManager.getAbility1Cost(type))
                .color(NamedTextColor.YELLOW));

        player.sendMessage(Component.text("  \u2461 " + strip(element.getAbility2Name()))
                .color(NamedTextColor.LIGHT_PURPLE)
                .decorate(TextDecoration.BOLD));
        player.sendMessage(Component.text("     " + strip(element.getAbility2Description()))
                .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("     Mana: " + configManager.getAbility2Cost(type))
                .color(NamedTextColor.YELLOW));

        player.sendMessage(Component.empty());
    }

    /**
     * Element/ability text carries legacy '{@literal §}' color codes for use in
     * chat messages elsewhere; strip them here since this command builds its own
     * Adventure {@link Component} colors instead.
     */
    private static String strip(String legacyText) {
        return org.bukkit.ChatColor.stripColor(legacyText);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String input = args[0].toLowerCase();

            for (ElementType type : elementManager.getElementRegistry().getAllTypes()) {
                String name = type.name().toLowerCase();
                if (name.startsWith(input)) {
                    completions.add(name);
                }
            }
            return completions;
        }
        return Collections.emptyList();
    }
}
