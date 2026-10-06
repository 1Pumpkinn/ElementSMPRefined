package net.rose.elementSMPRefined.ability.passive.upgraded.lava;

import net.rose.elementSMPRefined.ability.main.upgraded.lava.MagmaBeamAbility;
import net.rose.elementSMPRefined.ability.main.upgraded.lava.EruptionAbility;
import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.services.EffectService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class LavaElement extends BaseElement {

    public LavaElement(JavaPlugin plugin, ConfigManager configManager) {
        super(plugin, ElementType.LAVA, new EruptionAbility(plugin, configManager), new MagmaBeamAbility(plugin, configManager));
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Passive: permanent fire/lava immunity, so the caster can't be hurt by their own eruption fire.
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, true, false));
    }

    @Override
    public void clearEffects(Player player) {
        super.clearEffects(player);
        EffectService.removeElementPotionEffect(player, PotionEffectType.FIRE_RESISTANCE);
    }

    @Override
    public String getDisplayName() {
        return ChatColor.GOLD + "Lava";
    }

    @Override
    public List<String> getPassiveBenefits() {
        return List.of(
                "Immune to fire/lava damage"
        );
    }
}
