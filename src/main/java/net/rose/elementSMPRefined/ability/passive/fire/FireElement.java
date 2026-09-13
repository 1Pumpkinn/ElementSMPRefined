package net.rose.elementSMPRefined.ability.passive.fire;

import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.ability.main.fire.FireGeyserAbility;
import net.rose.elementSMPRefined.ability.main.fire.MeteorCrashAbility;
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
        super(plugin, new FireGeyserAbility(plugin, configManager), new MeteorCrashAbility(plugin, configManager));
    }

    @Override
    public ElementType getType() {
        return ElementType.FIRE;
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Passive: Auto Smelt on mined ores (handled in FireSmeltListener)
        // No passive effect needed here

        // Upgrade II: Infinite Fire Resistance
        if (upgradeLevel >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, true, false));
        } else {
            EffectService.removeElementPotionEffect(player, PotionEffectType.FIRE_RESISTANCE);
        }
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
    public String getDescription() {
        return ChatColor.GRAY + "Masters of flame and destruction. Fire users auto-smelt the ores they mine, and gain fire immunity at Upgrade II.";
    }

    @Override
    public List<String> getPassiveBenefits() {
        return List.of(
                "Auto smelt mined ores",
                "Immune to fire/lava damage (Upgrade II)"
        );
    }
}