package net.rose.elementSMPRefined.ability.main.upgraded.storm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import org.bukkit.ChatColor;

/**
 * Stand-in for Storm's second ability, which isn't designed yet. {@code BaseElement} needs two
 * abilities, so this keeps the element working: casting it does nothing and costs no cooldown
 * (returning false). Replace it with the real ability and update {@code StormElement}.
 */
public class StormAbility2Placeholder extends BaseAbility {

    public StormAbility2Placeholder(ConfigManager configManager) {
        super("storm_ability_2_placeholder", ElementType.STORM, 2, 1, 2, configManager);
    }

    @Override
    public boolean execute(ElementContext context) {
        context.getPlayer().sendMessage(Component.text("Storm's second ability isn't ready yet.", NamedTextColor.GRAY));
        return false;
    }

    @Override
    public String getName() {
        return ChatColor.GRAY + "Coming Soon";
    }

    @Override
    public String getDescription() {
        return "Storm's second ability is not implemented yet.";
    }
}