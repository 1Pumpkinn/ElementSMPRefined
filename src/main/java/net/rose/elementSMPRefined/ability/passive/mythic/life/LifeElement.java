package net.rose.elementSMPRefined.ability.passive.mythic.life;

import net.rose.elementSMPRefined.ability.main.mythic.life.LifeTotemAbility;
import net.rose.elementSMPRefined.ability.main.mythic.life.SporeBlossomAbility;
import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class LifeElement extends BaseElement {

    public LifeElement(JavaPlugin plugin, ConfigManager configManager) {
        super(
                plugin,
                ElementType.LIFE,
                new LifeTotemAbility(plugin, configManager),
                new SporeBlossomAbility(plugin, configManager));
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {}

    @Override
    public String getDisplayName() {
        return ChatColor.GREEN + "Life";
    }

    @Override
    public List<String> getPassiveBenefits() {
        return List.of(
                "Breaking leaves drops golden apples instead of apples",
                "Axes chop down the whole tree",
                "Golden apples give extra Absorption");
    }
}
