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

/**
 * Frost - upgraded from Water.
 *
 * Passive 1: Breath of the Nautilus (inherited from Water)
 * Passive 2 (Upgrade II): True invisibility while standing still in water (inherited from Water,
 *            see WaterInvisibilityListener, which handles both Water and Frost)
 * Passive 3 (Upgrade II): Flash Freeze - chance on a melee hit to encase a player in ice on the ground and
 *            stun them for 2 seconds (see FrostFreezeListener)
 */
public class FrostElement extends BaseElement implements ListenerProvider {
    public FrostElement(JavaPlugin plugin, ConfigManager configManager) {
        super(plugin, ElementType.FROST,
                new FrostIcicleBarrageAbility(plugin, configManager),
                new FrostIceSpikesAbility(plugin, configManager));
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Passive 1 (from Water): Breath of the Nautilus, same effect Water uses.
        player.addPotionEffect(new PotionEffect(PotionEffectType.BREATH_OF_THE_NAUTILUS, PotionEffect.INFINITE_DURATION, 0, true, false));

        // Passive 2 (from Water) is handled by WaterInvisibilityListener.

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
                "5% chance on hit to freeze a player in ice on the ground for 2 seconds (Upgrade II)");
    }
}