package net.rose.elementSMPRefined.ability.passive.death;

import net.rose.elementSMPRefined.API.element.BaseElement;
import net.rose.elementSMPRefined.API.element.ElementType;
import net.rose.elementSMPRefined.API.element.ListenerProvider;
import net.rose.elementSMPRefined.ability.main.death.DeathBackstabAbility;
import net.rose.elementSMPRefined.ability.main.death.DeathSideStepAbility;
import net.rose.elementSMPRefined.ability.passive.death.listeners.DeathNightInvisibilityListener;
import net.rose.elementSMPRefined.ability.passive.death.listeners.DeathWitherOnHitListener;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.managers.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class DeathElement extends BaseElement implements ListenerProvider {
    private DeathNightInvisibilityListener nightInvisibilityListener;

    public DeathElement(JavaPlugin plugin, ConfigManager configManager) {
        super(plugin, new DeathSideStepAbility(plugin, configManager), new DeathBackstabAbility(plugin, configManager));
    }

    @Override
    public List<Listener> getListeners(JavaPlugin plugin) {
        ElementSMPRefined elementPlugin = (ElementSMPRefined) plugin;
        this.nightInvisibilityListener = new DeathNightInvisibilityListener(elementPlugin, elementPlugin.getElementManager());
        return List.of(
                nightInvisibilityListener,
                new DeathWitherOnHitListener(elementPlugin.getElementManager(), elementPlugin.getTrustManager())
        );
    }

    @Override
    public ElementType getType() {
        return ElementType.DEATH;
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Passive 1: Invisibility at night (handled by DeathNightInvisibilityListener)
        // Passive 2: Chance to apply Wither on hit (handled by DeathWitherOnHitListener)
        // Neither passive is a standing potion effect applied here.
    }

    @Override
    public void clearEffects(Player player) {
        super.clearEffects(player);
        if (nightInvisibilityListener != null) {
            nightInvisibilityListener.clear(player);
        }
    }

    @Override
    public String getDisplayName() {
        return ChatColor.DARK_PURPLE + "Death";
    }

    @Override
    public String getDescription() {
        return ChatColor.GRAY + "Master of decay. Death users slip away in the dark and rot what they strike.";
    }

    @Override
    public List<String> getPassiveBenefits() {
        return List.of(
                "Invisible at night",
                "Chance to apply Wither for 5s on hit"
        );
    }
}