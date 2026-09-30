package net.rose.elementSMPRefined.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.status.DisarmManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /ability1} (alias {@code /a1}) and {@code /ability2} (alias {@code /a2}).
 * <p>
 * Fires the player's element ability directly - no tap detection, no scheduled
 * delay - so activation happens on the same tick the command is processed.
 * One executor is registered for both commands; the command name picks the slot.
 * <p>
 * Failure feedback (cooldown, missing upgrade, no target) is sent by the ability
 * itself; a player with no element simply does nothing.
 */
public class AbilityCommand implements CommandExecutor {
    private static final Component ABILITY_DISARMED = Component.text(
            "You are disarmed and cannot use abilities!", NamedTextColor.RED);

    private final ElementManager elements;
    private final DisarmManager disarmManager;

    public AbilityCommand(ElementManager elements, DisarmManager disarmManager) {
        this.elements = elements;
        this.disarmManager = disarmManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use abilities.", NamedTextColor.RED));
            return true;
        }

        if (disarmManager.isAbilityDisarmed(player)) {
            player.sendActionBar(ABILITY_DISARMED);
            return true;
        }

        if (command.getName().equalsIgnoreCase("ability2")) {
            elements.useAbility2(player);
        } else {
            elements.useAbility1(player);
        }
        return true;
    }
}
