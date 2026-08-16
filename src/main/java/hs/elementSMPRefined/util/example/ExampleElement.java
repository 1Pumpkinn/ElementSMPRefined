package hs.elementSMPRefined.util.example;

import hs.elementSMPRefined.API.element.BaseElement;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.API.element.ListenerProvider;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Template for a new element. Copy this class, rename it, and give it a real
 * {@link ElementType}. Extending {@link BaseElement} means you only need to
 * describe what makes this element unique - mana spending, upgrade-level
 * gating, and ability name/description are already handled for you.
 * <p>
 * Optionally implement {@link ListenerProvider} to automatically register your
 * element's passive listeners without modifying ListenerInitializer.
 * <p>
 * To wire it in:
 * <ol>
 *   <li>Add a constant to {@link ElementType}.</li>
 *   <li>Register it in {@code ElementManager.registerAllElements()} -
 *       {@code elementRegistry.register(new ExampleElement(plugin));}</li>
 *   <li>If it needs event listeners (combat hooks, item interactions, etc.),
 *       implement {@link ListenerProvider#getListeners(JavaPlugin)} to return
 *       your listeners - they'll be auto-registered.</li>
 * </ol>
 */
public class ExampleElement extends BaseElement implements ListenerProvider {

    public ExampleElement(JavaPlugin plugin) {
        // Hand BaseElement the two abilities this element casts.
        super(plugin, new ExampleAbility(), new ExampleAbility());
    }

    @Override
    public List<Listener> getListeners(JavaPlugin plugin) {
        // Return any listeners your element's passives need.
        // They will be automatically registered by ListenerInitializer.
        return List.of(
                // new ExamplePassiveListener(plugin, ...)
        );
    }

    @Override
    public ElementType getType() {
        return ElementType.AIR; // replace with a real, unused ElementType
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Permanent passive effects go here. Guard stronger effects behind
        // upgradeLevel so Upgrade II feels like an upgrade.
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, true, false));

        if (upgradeLevel >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, true, false));
        }
    }

    @Override
    public void clearEffects(Player player) {
        // super.clearEffects() deactivates ability1/ability2 - always call it,
        // then strip anything applyUpsides() added.
        super.clearEffects(player);
        player.removePotionEffect(PotionEffectType.SPEED);
    }

    @Override
    public String getDisplayName() {
        return ChatColor.YELLOW + "Example";
    }

    @Override
    public String getDescription() {
        return "An example element template for creating new elements.";
    }

    @Override
    public List<String> getPassiveBenefits() {
        return List.of(
                "Speed I",
                "Speed II (Upgrade II)"
        );
    }
}
