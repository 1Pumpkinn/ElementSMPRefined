package net.rose.elementSMPRefined.ability.passive.basic.fire;

import net.rose.elementSMPRefined.ability.main.basic.fire.HellChainAbility;
import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.ability.main.basic.fire.StarFallAbility;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.services.EffectService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class FireElement extends BaseElement {

    public FireElement(JavaPlugin plugin, ConfigManager configManager) {
        super(plugin, ElementType.FIRE,
                new HellChainAbility(plugin, configManager),
                new StarFallAbility(plugin, configManager));
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Passive 1 (start): Auto Smelt on mined ores (handled in FireSmeltListener)

        // Passive 2 (Upgrade II): Infinite Fire Resistance
        if (upgradeLevel >= PASSIVE_2_UPGRADE_LEVEL) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, true, false));
        } else {
            EffectService.removeElementPotionEffect(player, PotionEffectType.FIRE_RESISTANCE);
        }
    }

    @Override
    protected boolean canCancelAbility1(ElementContext context) {
        // Hell Chain is out - pressing the ability again lets go of it, no cooldown check.
        return ability1.isActiveFor(context.getPlayer());
    }

    @Override
    public void clearEffects(Player player) {
        super.clearEffects(player);
        EffectService.removeElementPotionEffect(player, PotionEffectType.FIRE_RESISTANCE);
    }

    @Override
    public String getDisplayName() {
        return ChatColor.RED + "Fire";
    }

    @Override
    public List<String> getPassiveBenefits() {
        return List.of(
                "Auto Smelt (toggle with /toggle firesmelt)",
                "Fire Resistance (Upgrade II)");
    }
}