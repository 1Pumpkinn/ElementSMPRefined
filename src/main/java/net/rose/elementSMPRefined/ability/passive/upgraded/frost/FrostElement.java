package net.rose.elementSMPRefined.ability.passive.upgraded.frost;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.ability.passive.upgraded.frost.listeners.FrostFreezeListener;
import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.API.element.ListenerProvider;
import net.rose.elementSMPRefined.ability.main.upgraded.frost.FrostIceSpikesAbility;
import net.rose.elementSMPRefined.ability.main.upgraded.frost.FrostIcicleBarrageAbility;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.services.EffectService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class FrostElement extends BaseElement implements ListenerProvider {
    public FrostElement(JavaPlugin plugin, ConfigManager configManager) {
        super(plugin, ElementType.FROST,
                new FrostIcicleBarrageAbility(plugin, configManager),
                new FrostIceSpikesAbility(plugin, configManager));
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.BREATH_OF_THE_NAUTILUS, PotionEffect.INFINITE_DURATION, 0, true, false));

    }

    @Override
    public List<Listener> getListeners(JavaPlugin plugin) {
        ElementSMPRefined main = (ElementSMPRefined) plugin;
        return List.of(new FrostFreezeListener(main, main.getElementManager(), main.getTrustManager()));
    }

    @Override
    public void clearEffects(Player player) {
        super.clearEffects(player);
        EffectService.removeElementPotionEffect(player, PotionEffectType.BREATH_OF_THE_NAUTILUS);
    }

    @Override
    public String getDisplayName() {
        return ChatColor.AQUA + "Frost";
    }

    @Override
    public List<String> getPassiveBenefits() {
        return List.of(
                "Breath of the Nautilus",
                "True Invis while staying still in water (Upgrade II)",
                "2.5% chance on hit to freeze a player in ice on the ground for 2 seconds (Upgrade II)");
    }
}