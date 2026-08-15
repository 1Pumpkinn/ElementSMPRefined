package hs.elementSMPRefined.util.example;

import hs.elementSMPRefined.API.ability.BaseAbility;
import hs.elementSMPRefined.API.element.ElementContext;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Template for a new ability. Copy this class, rename it, and fill in
 * {@link #execute}. Extending {@link BaseAbility} gives you mana cost,
 * cooldown, required upgrade level, and active-player tracking for free -
 * you only need to describe the ability and implement what it does.
 */
public class ExampleAbility extends BaseAbility {

    public ExampleAbility() {
        // abilityId, manaCost, cooldownSeconds, requiredUpgradeLevel
        super("example_ability", 50, 0, 1);
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();

        // Toggle off if already active - useful for channelled/held abilities.
        if (isActiveFor(player)) {
            setActive(player, false);
            player.sendMessage(ChatColor.RED + "Example ability deactivated");
            return true;
        }

        setActive(player, true);
        player.sendMessage(ChatColor.GREEN + "Example ability activated");

        // Add your ability logic here: deal damage, apply effects, spawn particles, etc.

        return true;
    }

    @Override
    public String getName() {
        return ChatColor.WHITE + "Example Ability";
    }

    @Override
    public String getDescription() {
        return "An example ability template for creating new abilities";
    }
}
