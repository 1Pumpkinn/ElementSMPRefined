package net.rose.elementSMPRefined.ability.passive.upgraded.storm;

import net.rose.elementSMPRefined.ability.main.upgraded.storm.LightningSpearAbility;
import net.rose.elementSMPRefined.ability.main.upgraded.storm.StormAbility2Placeholder;
import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Storm - lightning-based element.
 * <p>
 * Ability 1: Lightning Spear (fast lightning projectile that shocks what it hits)
 * Ability 2: not designed yet (placeholder)
 * Passives: none yet
 */
public class StormElement extends BaseElement {

    public StormElement(JavaPlugin plugin, ConfigManager configManager) {
        super(plugin, ElementType.STORM,
                new LightningSpearAbility(plugin, configManager),
                new StormAbility2Placeholder(configManager));
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // No passives yet.
    }

    @Override
    public String getDisplayName() {
        return ChatColor.YELLOW + "Storm";
    }
}