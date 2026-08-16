package hs.elementSMPRefined.ability.passive.water;

import hs.elementSMPRefined.API.element.BaseElement;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.ability.main.water.WaterBubbleAbility;
import hs.elementSMPRefined.ability.main.water.WaterPullDownAbility;
import hs.elementSMPRefined.services.EffectService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class WaterElement extends BaseElement {

    public WaterElement(JavaPlugin plugin) {
        super(plugin, new WaterBubbleAbility(plugin), new WaterPullDownAbility(plugin));
    }

    @Override
    public ElementType getType() { return ElementType.WATER; }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Passive 1: Breath of the Nautilus (infinite water breathing)
        player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, Integer.MAX_VALUE, 0, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, Integer.MAX_VALUE, 0, true, false));

        if (upgradeLevel >= 2) {
            // Passive 2: Dolphins grace 5 (upgrade II)
            player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, Integer.MAX_VALUE, 4, true, false));
        }
    }

    @Override
    public void clearEffects(Player player) {
        super.clearEffects(player);
        EffectService.removeElementPotionEffect(player, PotionEffectType.WATER_BREATHING);
        EffectService.removeElementPotionEffect(player, PotionEffectType.CONDUIT_POWER);
        EffectService.removeElementPotionEffect(player, PotionEffectType.DOLPHINS_GRACE);
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
                "Conduit Power permanently",
                "True invisibility while still in water",
                "Dolphin's Grace V (Upgrade II)"
        );
    }
}
