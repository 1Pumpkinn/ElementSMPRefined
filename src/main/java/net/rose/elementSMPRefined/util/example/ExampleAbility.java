package net.rose.elementSMPRefined.util.example;

import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.lang.Lang;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Template for a new ability. Copy this class, rename it, and fill in
 * {@link #execute}. Extending {@link BaseAbility} gives you cooldown tracking,
 * required upgrade level, and active-player tracking for free - you only need
 * to describe the ability and implement what it does.
 * <p>
 * You never call execute() yourself - BaseElement.ability1()/ability2() do,
 * after already checking upgrade level and cooldown. The cooldown is only
 * started if this returns true, so a failed cast (no valid target, etc.)
 * stays free - just return false and nothing is spent.
 */
public class ExampleAbility extends BaseAbility {

    public ExampleAbility() {
        // abilityId, cooldownSeconds, requiredUpgradeLevel
        super("example_ability", 8, 1);
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
            player.sendMessage(Lang.EXAMPLE_EXAMPLE_ABILITY_DEACTIVATED);
            return true;
        }

        setActive(player, true);
        player.sendMessage(Lang.EXAMPLE_EXAMPLE_ABILITY_ACTIVATED);

        // Ability logic goes here: deal damage, apply effects, spawn particles,
        // etc. context also gives you managers (cooldown, trust, config) and the
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