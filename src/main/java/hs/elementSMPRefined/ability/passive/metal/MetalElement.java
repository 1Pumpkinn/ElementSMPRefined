package hs.elementSMPRefined.ability.passive.metal;

import hs.elementSMPRefined.API.element.BaseElement;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.ability.main.metal.MetalChainAbility;
import hs.elementSMPRefined.ability.main.metal.MetalDashAbility;
import hs.elementSMPRefined.services.EffectService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class MetalElement extends BaseElement {

    public MetalElement(JavaPlugin plugin) {
        super(plugin, new MetalChainAbility(plugin), new MetalDashAbility(plugin));
    }

    public MetalDashAbility getMetalDashAbility() {
        return (MetalDashAbility) ability2;
    }

    @Override
    public ElementType getType() {
        return ElementType.METAL;
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Upside 1: haste 1 permanently
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, PotionEffect.INFINITE_DURATION, 0, true, false));

        // Upside 2: Arrow immunity (handled in listener)
        // No passive effect needed here
    }

    @Override
    public void clearEffects(Player player) {
        super.clearEffects(player);
        EffectService.removeElementPotionEffect(player, PotionEffectType.HASTE);
    }

    @Override
    public String getDisplayName() {
        return ChatColor.GRAY + "Metal";
    }

    @Override
    public String getDescription() {
        return "Masters of chains and iron. Metal users are swift and can dash through enemies.";
    }

    @Override
    public List<String> getPassiveBenefits() {
        return List.of(
                "Haste I",
                "Arrow immunity (Upgrade II)"
        );
    }
}
