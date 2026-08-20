package hs.elementSMPRefined.ability.passive.air;

import hs.elementSMPRefined.API.element.BaseElement;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.ability.main.air.AirDashAbility;
import hs.elementSMPRefined.ability.main.air.SlicingWindAbility;
import hs.elementSMPRefined.ability.passive.air.listeners.AirFallImpactListener;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class AirElement extends BaseElement {
    private AirFallImpactListener fallImpactListener;

    public AirElement(JavaPlugin plugin) {
        super(plugin, new AirDashAbility(plugin), new SlicingWindAbility(plugin));
    }

    public void setFallImpactListener(AirFallImpactListener listener) {
        this.fallImpactListener = listener;
    }

    public AirFallImpactListener getFallImpactListener() {
        return fallImpactListener;
    }

    @Override
    public ElementType getType() {
        return ElementType.AIR;
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Passive 1: No fall damage (handled in AirFallImpactListener)
        // Passive 2: The further you fall, the further nearby entities get
        // knocked back on landing (handled in AirFallImpactListener)
        // No potion effects needed
    }

    @Override
    public String getDisplayName() {
        return ChatColor.WHITE + "Air";
    }

    @Override
    public String getDescription() {
        return "Master the swift and agile power of air. Take no fall damage and knock back enemies with the force of your landing.";
    }

    @Override
    public List<String> getPassiveBenefits() {
        return List.of(
                "No fall damage",
                "The further you fall, the further nearby entities are knocked back on landing",
                "5% chance to apply Slow Falling to enemies (Upgrade II)"
        );
    }
}
