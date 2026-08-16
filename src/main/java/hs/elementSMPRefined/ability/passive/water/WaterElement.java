package hs.elementSMPRefined.ability.passive.water;

import hs.elementSMPRefined.API.element.BaseElement;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.API.element.ListenerProvider;
import hs.elementSMPRefined.ability.main.water.WaterBubbleAbility;
import hs.elementSMPRefined.ability.main.water.WaterPullDownAbility;
import hs.elementSMPRefined.ability.passive.water.listeners.WaterDrowningImmunityListener;
import hs.elementSMPRefined.ability.passive.water.listeners.WaterInvisibilityListener;
import hs.elementSMPRefined.services.EffectService;
import org.bukkit.ChatColor;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class WaterElement extends BaseElement implements ListenerProvider {

    public WaterElement(JavaPlugin plugin) {
        super(plugin, new WaterBubbleAbility(plugin), new WaterPullDownAbility(plugin));
    }

    @Override
    public List<Listener> getListeners(JavaPlugin plugin) {
        return List.of(
                new WaterDrowningImmunityListener(((hs.elementSMPRefined.ElementSMPRefined) plugin).getElementManager()),
                new WaterInvisibilityListener(((hs.elementSMPRefined.ElementSMPRefined) plugin), ((hs.elementSMPRefined.ElementSMPRefined) plugin).getElementManager())
        );
    }

    @Override
    public ElementType getType() { return ElementType.WATER; }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Passive 1: Breath of the Nautilus DO NOT CHANGE THIS EFFECT IT'S BREATH OF THE NATULIST
        player.addPotionEffect(new PotionEffect(PotionEffectType.BREATH_OF_THE_NAUTILUS, PotionEffect.INFINITE_DURATION, 0, true, false));

        if (upgradeLevel >= 2) {
            // Passive 2 is not a effect so nothing here!
        }
    }

    @Override
    public void clearEffects(Player player) {
        super.clearEffects(player);
        EffectService.removeElementPotionEffect(player, PotionEffectType.WATER_BREATHING);
    }

    @Override
    public String getDisplayName() {
        return ChatColor.AQUA + "Water";
    }

    @Override
    public String getDescription() {
        return "Control the tides with a bubble shield, drowning pull, and the Breath of the Nautilus.";
    }

    @Override
    public List<String> getPassiveBenefits() {
        return List.of(
                "Breath of the Nautilus (infinite water breathing)",
                "True invisibility while still in water"
        );
    }
}
