package net.rose.elementSMPRefined.ability.passive.upgraded.metal;

import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.ability.main.upgraded.metal.MetalShieldAbility;
import net.rose.elementSMPRefined.ability.main.upgraded.metal.MetalAnchorCoreAbility;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.services.EffectService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Metal - upgraded from Earth.
 *
 * Passive 1: Hero of the Village (inherited from Earth)
 * Passive 2: Vein Miner (inherited from Earth, see EarthVeinMinerListener)
 * Passive 3: not decided yet
 */
public class MetalElement extends BaseElement {

    public MetalElement(JavaPlugin plugin, ConfigManager configManager) {
        super(plugin, ElementType.METAL, new MetalShieldAbility(plugin, configManager), new MetalAnchorCoreAbility(plugin, configManager));
    }

    public MetalAnchorCoreAbility getMetalAnchorCoreAbility() {
        return (MetalAnchorCoreAbility) ability2;
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Passive 1: Hero of the Village, same as Earth.
        player.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, PotionEffect.INFINITE_DURATION, 0, true, false));

        // Passive 2 (Vein Miner) is handled by EarthVeinMinerListener.
        // Passive 3: TBD.
    }

    @Override
    public void clearEffects(Player player) {
        super.clearEffects(player);
        EffectService.removeElementPotionEffect(player, PotionEffectType.HERO_OF_THE_VILLAGE);
        // Metal used to give permanent Haste - clear any left over from before that passive was removed.
        EffectService.removeElementPotionEffect(player, PotionEffectType.HASTE);
    }

    @Override
    public String getDisplayName() {
        return ChatColor.GRAY + "Metal";
    }

    @Override
    public List<String> getPassiveBenefits() {
        return List.of(
                "Hero of The Village",
                "Vein Miner"
        );
    }
}