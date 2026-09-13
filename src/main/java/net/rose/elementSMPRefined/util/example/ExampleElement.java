package net.rose.elementSMPRefined.util.example;

import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.API.element.ListenerProvider;
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
        // Hand BaseElement the two abilities this element casts. First one is
        // ability1 (Upgrade I), second is ability2 (needs Upgrade II) -
        // use two different Ability classes here, this is just reusing one for the demo.
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
        // Passive effects for holding this element. Not a one-time thing -
        // this gets re-run periodically (and after events like drinking milk)
        // to keep the effects topped up, so just re-apply them here each time
        // rather than worrying about "is it already active". Gate stronger
        // effects behind upgradeLevel so Upgrade II actually feels stronger.
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, true, false));

        if (upgradeLevel >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, true, false));
        }
    }

    @Override
    public void clearEffects(Player player) {
        // Runs when the player switches away from this element (or logs off
        // holding it). super.clearEffects() turns off any active ability1/2 -
        // always call it first, then remove whatever applyUpsides() added above.
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