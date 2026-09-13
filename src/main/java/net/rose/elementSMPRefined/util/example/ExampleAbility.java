package net.rose.elementSMPRefined.util.example;

import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Template for a new ability. Copy this class, rename it, and fill in
 * {@link #execute}. Extending {@link BaseAbility} gives you mana cost,
 * cooldown, required upgrade level, and active-player tracking for free -
 * you only need to describe the ability and implement what it does.
 * <p>
 * You never call execute() yourself - BaseElement.ability1()/ability2() do,
 * after already checking upgrade level and spending mana. If you return
 * false here, that spent mana is automatically refunded (treat false as
 * "the cast didn't actually happen, e.g. no valid target").
 */
public class ExampleAbility extends BaseAbility {

    public ExampleAbility() {
        // abilityId, manaCost, cooldownSeconds, requiredUpgradeLevel
        super("example_ability", 50, 0, 1);
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();

        // isActiveFor/setActive just track "is this ability currently on" per
        // player - handy for toggled/held abilities like this one. For a
        // one-shot ability (e.g. a single burst of damage) you can ignore
        // these and just run your effect once, then return true.
        if (isActiveFor(player)) {
            setActive(player, false);
            player.sendMessage(ChatColor.RED + "Example ability deactivated");
            return true;
        }

        setActive(player, true);
        player.sendMessage(ChatColor.GREEN + "Example ability activated");

        // Ability logic goes here: deal damage, apply effects, spawn particles,
        // etc. context also gives you managers (mana, trust, config) and the
        // caster's upgrade level via context.getUpgradeLevel().

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