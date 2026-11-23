package net.rose.elementSMPRefined.ability.passive.mythic.time;

import net.rose.elementSMPRefined.ability.main.mythic.time.TimePlaceholderAbility;
import net.rose.elementSMPRefined.ability.main.mythic.time.TimeRingAbility;
import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class TimeElement extends BaseElement {

    public TimeElement(JavaPlugin plugin, ConfigManager configManager) {
        super(
                plugin,
                ElementType.TIME,
                new TimeRingAbility(plugin, configManager),
                new TimePlaceholderAbility(configManager));
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {}

    @Override
    public String getDisplayName() {
        return ChatColor.LIGHT_PURPLE + "Time";
    }
}
