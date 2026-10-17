package net.rose.elementSMPRefined.ability.passive.upgraded.storm;

import net.rose.elementSMPRefined.ability.main.upgraded.storm.LightningSpearAbility;
import net.rose.elementSMPRefined.ability.main.upgraded.storm.TornadoAbility;
import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Storm - lightning-based element.
 * <p>
 * Ability 1: Lightning Spear (fast lightning projectile that shocks what it hits)
 * Ability 2: Tornado (a roaming funnel that sucks in and damages everything near it)
 * Passive 1: No fall damage (inherited from Air, see AirFallImpactListener)
 * Passive 2: The further you fall, the further nearby entities get knocked back on landing
 *            (inherited from Air, see AirFallImpactListener)
 * Passive 3: Coming soon
 */
public class StormElement extends BaseElement {

    public StormElement(JavaPlugin plugin, ConfigManager configManager) {
        super(plugin, ElementType.STORM,
                new LightningSpearAbility(plugin, configManager),
                new TornadoAbility(plugin, configManager));
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Passives 1 and 2 (no fall damage, fall impact knockback) are handled by AirFallImpactListener.
        // Passive 3 is coming soon.
    }

    @Override
    public String getDisplayName() {
        return ChatColor.YELLOW + "Storm";
    }

    @Override
    public List<String> getPassiveBenefits() {
        return List.of(
                "No fall damage",
                "The further you fall the further nearby entities are knocked back when landing",
                "Coming soon");
    }
}
