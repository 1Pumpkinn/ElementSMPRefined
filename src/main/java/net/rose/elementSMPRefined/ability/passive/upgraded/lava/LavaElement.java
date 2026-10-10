package net.rose.elementSMPRefined.ability.passive.upgraded.lava;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.ability.main.upgraded.lava.EruptionAbility;
import net.rose.elementSMPRefined.ability.main.upgraded.lava.MagmaBeamAbility;
import net.rose.elementSMPRefined.ability.passive.upgraded.lava.listeners.LavaIncreaseDamageListener;
import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.API.element.ListenerProvider;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.services.EffectService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Lava - upgraded from Fire.
 *
 * Passive 1: Auto smelt mined ores (inherited from Fire, see FireSmeltListener)
 * Passive 2: Permanent fire/lava immunity (inherited from Fire's Upgrade II perk)
 * Passive 3: +15% melee damage at 4 hearts or below (LavaLowHealthDamageListener)
 */
public class LavaElement extends BaseElement implements ListenerProvider {

    public LavaElement(JavaPlugin plugin, ConfigManager configManager) {
        super(plugin, ElementType.LAVA, new EruptionAbility(plugin, configManager), new MagmaBeamAbility(plugin, configManager));
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Passive 2: permanent fire/lava immunity, so the caster can't be hurt by their own eruption fire.
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, true, false));

        // Passive 1 (auto smelt) is handled by FireSmeltListener.
        // Passive 3 (low health damage) is handled by LavaLowHealthDamageListener.
    }

    @Override
    public void clearEffects(Player player) {
        super.clearEffects(player);
        EffectService.removeElementPotionEffect(player, PotionEffectType.FIRE_RESISTANCE);
    }

    @Override
    public List<Listener> getListeners(JavaPlugin plugin) {
        ElementSMPRefined main = (ElementSMPRefined) plugin;
        return List.of(new LavaIncreaseDamageListener(main.getElementManager(), main.getTrustManager()));
    }

    @Override
    public String getDisplayName() {
        return ChatColor.GOLD + "Lava";
    }

    @Override
    public List<String> getPassiveBenefits() {
        return List.of(
                "Auto smelt ores",
                "Fire Resistance",
                "+15% melee damage at 4 hearts and below"
        );
    }
}