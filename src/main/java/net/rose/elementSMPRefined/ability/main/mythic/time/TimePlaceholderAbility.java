package net.rose.elementSMPRefined.ability.main.mythic.time;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.managers.ConfigManager;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Time's second ability slot. An element needs two abilities, so this holds the place until the
 * real one is designed. It never casts, so it never starts a cooldown.
 */
public class TimePlaceholderAbility extends BaseAbility {

    public TimePlaceholderAbility(ConfigManager configManager) {
        super(
                "time_placeholder",
                ElementType.TIME,
                2,
                Constants.Cooldown.ABILITY_2_SECONDS,
                2,
                configManager);
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        if (player != null) {
            player.sendMessage(
                    Component.text("Time's second ability isn't ready yet.", NamedTextColor.GRAY));
        }
        return false;
    }

    @Override
    public String getName() {
        return ChatColor.GRAY + "Coming Soon";
    }

    @Override
    public String getDescription() {
        return "Reserved for a future Time ability.";
    }
}
