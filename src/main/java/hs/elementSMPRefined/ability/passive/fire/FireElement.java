package hs.elementSMPRefined.ability.passive.fire;

import hs.elementSMPRefined.API.element.BaseElement;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.ability.main.fire.FireGeyserAbility;
import hs.elementSMPRefined.ability.main.fire.MeteorCrashAbility;
import hs.elementSMPRefined.services.EffectService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class FireElement extends BaseElement {

    public FireElement(JavaPlugin plugin) {
        super(plugin, new FireGeyserAbility(plugin), new MeteorCrashAbility(plugin));
    }

    @Override
    public ElementType getType() {
        return ElementType.FIRE;
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Passive 1: Infinite Fire Resistance
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, true, false));

        // Passive 2: Fire Aspect on hits (handled in listener - applies to all attacks)
        // No passive effect needed here
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
        return ChatColor.GRAY + "Masters of flame and destruction. Fire users are immune to fire damage and ignite enemies with every attack.";
    }

    @Override
    public List<String> getPassiveBenefits() {
        return List.of(
                "Immune to fire/lava damage",
                "Apply Fire Aspect to all attacks (Upgrade II)"
        );
    }
}