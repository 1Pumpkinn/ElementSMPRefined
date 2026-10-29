package net.rose.elementSMPRefined.ability.passive.basic.air;

import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.ability.main.basic.air.AirDashAbility;
import net.rose.elementSMPRefined.ability.main.basic.air.AirCutterAbility;
import net.rose.elementSMPRefined.ability.passive.basic.air.listeners.AirAntiFallDmgListener;
import net.rose.elementSMPRefined.managers.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class AirElement extends BaseElement {
    private AirAntiFallDmgListener fallImpactListener;

    public AirElement(JavaPlugin plugin, ConfigManager configManager) {
        super(plugin, ElementType.AIR,
                new AirDashAbility(plugin, configManager),
                new AirCutterAbility(plugin, configManager));
    }

    public void setFallImpactListener(AirAntiFallDmgListener listener) {
        this.fallImpactListener = listener;
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
    }

    @Override
    public String getDisplayName() {
        return ChatColor.WHITE + "Air";
    }

    @Override
    public List<String> getPassiveBenefits() {
        return List.of(
                "No fall damage",
                "Wind Burst (Upgrade II, toggle with /toggle windburst)");
    }
}