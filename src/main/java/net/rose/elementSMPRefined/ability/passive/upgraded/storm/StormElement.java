package net.rose.elementSMPRefined.ability.passive.upgraded.storm;

import net.rose.elementSMPRefined.ability.main.upgraded.storm.LightningSpearAbility;
import net.rose.elementSMPRefined.ability.main.upgraded.storm.TornadoAbility;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.ability.passive.upgraded.storm.listeners.StormTridentLightningListener;
import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.API.element.ListenerProvider;
import net.rose.elementSMPRefined.managers.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class StormElement extends BaseElement implements ListenerProvider {

    public StormElement(JavaPlugin plugin, ConfigManager configManager) {
        super(plugin, ElementType.STORM,
                new LightningSpearAbility(plugin, configManager),
                new TornadoAbility(plugin, configManager));
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Passives 1 and 2 are handled by AirFallImpactListener (no fall damage) and AirWindBurstListener (Wind Burst).
        // Passive 3 (trident lightning) is handled by StormTridentLightningListener.
    }

    @Override
    public List<Listener> getListeners(JavaPlugin plugin) {
        ElementSMPRefined main = (ElementSMPRefined) plugin;
        return List.of(new StormTridentLightningListener(plugin, main.getElementManager(), main.getTrustManager()));
    }

    @Override
    public String getDisplayName() {
        return ChatColor.YELLOW + "Storm";
    }

    @Override
    public List<String> getPassiveBenefits() {
        return List.of(
                "No fall damage",
                "Wind Burst (Upgrade II, toggle with /toggle windburst)",
                "Thrown tridents summon lightning (Upgrade II)");
    }
}
