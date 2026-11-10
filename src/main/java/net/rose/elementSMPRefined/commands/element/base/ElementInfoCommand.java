package net.rose.elementSMPRefined.commands.element.base;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.element.Element;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.ElementManager;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /elements <element> - shows an element's description, passives, and abilities.
 * Reads everything straight from the registered {@link Element}, so this command
 * never needs updating when an element's text or costs change elsewhere.
 *
 * <p>Elements listed under {@code element_info.hidden_elements} in config.yml (unreleased ones)
 * are left out of the list and tab completion and can't be looked up, except by players with
 * {@code element.admin}, who see them marked as hidden.
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
            sender.sendMessage(Lang.ELEMENT_INFO_PLAYERS_ONLY);
            return true;
        }

        if (args.length == 0) {
            listElements(player);
            return true;
        }

        showElementDetails(player, args[0]);
        return true;
    }

    private void listElements(Player player) {
        List<String> names = new ArrayList<>();
        for (ElementType type : visibleTypes(player)) {
            String name = type.name().toLowerCase();
            names.add(isHidden(type) ? name + " (hidden)" : name);
        }
        player.sendMessage(
                Component.text("Elements: " + String.join(", ", names), NamedTextColor.GRAY));
        player.sendMessage(
                Component.text(
                        "Use /elements <element> to see its passives and abilities.",
                        NamedTextColor.GRAY));
    }

    private void showElementDetails(Player player, String elementName) {
        ElementType type;
        try {
            type = ElementType.valueOf(elementName.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(
                    Component.text("Unknown element: " + elementName, NamedTextColor.RED));
            return;
        }

        if (!canView(player, type)) {
            player.sendMessage(
                    Component.text("Unknown element: " + elementName, NamedTextColor.RED));
            return;
        }

        Element element = elementManager.get(type);
        if (element == null) {
            player.sendMessage(
                    Component.text(
                            "No information available for " + capitalize(type.name()),
                            NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.empty());
        Component title =
                Component.text(capitalize(type.name()), NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD);
        if (isHidden(type)) {
            title = title.append(Component.text(" (hidden)", NamedTextColor.RED));
        }
        player.sendMessage(title);

        if (!element.getPassiveBenefits().isEmpty()) {
            player.sendMessage(Component.text("Passives", NamedTextColor.YELLOW));
            for (String passive : element.getPassiveBenefits()) {
                player.sendMessage(Component.text("- " + passive, NamedTextColor.GRAY));
            }
        }

        sendAbility(player, 1, element.getAbility1Name(), element.getAbility1Description());
        sendAbility(player, 2, element.getAbility2Name(), element.getAbility2Description());
    }

    private void sendAbility(Player player, int slot, String name, String description) {
        player.sendMessage(
                Component.text("Ability " + slot + ": ", NamedTextColor.YELLOW)
                        .append(Component.text(strip(name), NamedTextColor.WHITE)));
        player.sendMessage(Component.text(strip(description), NamedTextColor.GRAY));
    }

    private boolean isHidden(ElementType type) {
        return configManager.isInfoElementHidden(type);
    }

    private boolean canView(CommandSender sender, ElementType type) {
        return !isHidden(type) || sender.hasPermission("element.admin");
    }

    private List<ElementType> visibleTypes(CommandSender sender) {
        List<ElementType> types = new ArrayList<>();
        for (ElementType type : elementManager.getElementRegistry().getAllTypes()) {
            if (canView(sender, type)) {
                types.add(type);
            }
        }
        return types;
    }

    private static String capitalize(String name) {
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    private static String strip(String legacyText) {
        return org.bukkit.ChatColor.stripColor(legacyText);
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String input = args[0].toLowerCase();

            for (ElementType type : visibleTypes(sender)) {
                String name = type.name().toLowerCase();
                if (name.startsWith(input)) {
                    completions.add(name);
                }
            }
            return completions;
        }
        return List.of();
    }
}
