package hs.elementSMPRefined.ability.passive.water;

import hs.elementSMPRefined.API.element.BaseElement;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.ability.main.water.WaterBeamAbility;
import hs.elementSMPRefined.ability.main.water.WaterGeyserAbility;
import hs.elementSMPRefined.services.EffectService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class WaterElement extends BaseElement {

    public WaterElement(JavaPlugin plugin) {
        super(plugin, new WaterGeyserAbility(plugin), new WaterBeamAbility(plugin));
    }

    @Override
    public ElementType getType() { return ElementType.WATER; }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Upside 1: Infinite conduit power
        player.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, Integer.MAX_VALUE, 0, true, false));

        if (upgradeLevel >= 2) {
            // Upside 2: Dolphins grace 5 (level 4 = dolphins grace 5)
            player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, Integer.MAX_VALUE, 4, true, false));
        }
    }

    @Override
    public void clearEffects(Player player) {
        super.clearEffects(player);
        EffectService.removeElementPotionEffect(player, PotionEffectType.CONDUIT_POWER);
        EffectService.removeElementPotionEffect(player, PotionEffectType.DOLPHINS_GRACE);
    }

    @Override
    public String getDisplayName() {
        return ChatColor.AQUA + "Water";
    }

    @Override
    public String getDescription() {
        return "Harness the flowing power of water to control the battlefield.";
    }

    @Override
    public List<String> getPassiveBenefits() {
        return List.of(
                "Infinite Water Breathing",
                "Conduit Power permanently",
                "Dolphin's Grace V (Upgrade II)"
        );
    }
}
