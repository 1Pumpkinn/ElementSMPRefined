package net.rose.elementSMPRefined.ability.passive.metal;

import net.rose.elementSMPRefined.API.element.BaseElement;
import net.rose.elementSMPRefined.API.element.ElementType;
import net.rose.elementSMPRefined.ability.main.metal.MetalChainAbility;
import net.rose.elementSMPRefined.ability.main.metal.MetalShardAbility;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.services.EffectService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class MetalElement extends BaseElement {

    public MetalElement(JavaPlugin plugin, ConfigManager configManager) {
        super(plugin, new MetalChainAbility(plugin, configManager), new MetalShardAbility(plugin, configManager));
    }

    public MetalShardAbility getMetalDashAbility() {
        return (MetalShardAbility) ability2;
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
