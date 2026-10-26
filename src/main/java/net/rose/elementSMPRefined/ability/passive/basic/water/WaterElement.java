package net.rose.elementSMPRefined.ability.passive.basic.water;

import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.API.element.ListenerProvider;
import net.rose.elementSMPRefined.ability.main.basic.water.KrakenGraspAbility;
import net.rose.elementSMPRefined.ability.main.basic.water.WaterDashAbility;
import net.rose.elementSMPRefined.ability.passive.basic.water.listeners.WaterInvisibilityListener;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.services.EffectService;
import org.bukkit.ChatColor;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class WaterElement extends BaseElement implements ListenerProvider {

    public WaterElement(JavaPlugin plugin, ConfigManager configManager) {
        super(plugin, ElementType.WATER,
                new WaterDashAbility(plugin, configManager),
                new KrakenGraspAbility(plugin, configManager));
    }

    /** Pressing ability 2 again while the grasp is out lets go (no cooldown check). */
    @Override
    protected boolean canCancelAbility2(ElementContext context) {
        return ability2.isActiveFor(context.getPlayer());
    }

    @Override
    public List<Listener> getListeners(JavaPlugin plugin) {
        // 'this.plugin' is already the typed ElementSMPRefined held by BaseElement.
        return List.of(new WaterInvisibilityListener(this.plugin, this.plugin.getElementManager()));
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Passive 1: Breath of the Nautilus DO NOT CHANGE THIS EFFECT IT'S BREATH OF THE NATULIST
        player.addPotionEffect(new PotionEffect(PotionEffectType.BREATH_OF_THE_NAUTILUS, PotionEffect.INFINITE_DURATION, 0, true, false));

        // Passive 2 (Upgrade II): in-water invisibility, handled by WaterInvisibilityListener,
        // which checks the level.
    }

    @Override
    public void clearEffects(Player player) {
        super.clearEffects(player);
        EffectService.removeElementPotionEffect(player, PotionEffectType.BREATH_OF_THE_NAUTILUS);
    }

    @Override
    public String getDisplayName() {
        return ChatColor.AQUA + "Water";
    }

    @Override
    public List<String> getPassiveBenefits() {
        return List.of(
                "Breath of the Nautilus",
                "True Invis while staying still in Water (Upgrade II)");
    }
}