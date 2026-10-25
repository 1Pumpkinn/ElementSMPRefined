package net.rose.elementSMPRefined.ability.passive.upgraded.metal.listeners;

    import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ElementManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Metal Element Passive 3: Frenzy (unlocks at Upgrade II)
 *
 * While a Metal player is at 4 hearts (8 HP) or below, their attack speed is
 * {@value #BONUS_PERCENT}% higher, so their weapon recharges faster between swings.
 *
 * Implemented as a transient attack-speed attribute modifier (never saved with the player) that a
 * small repeating task adds or removes as health crosses the threshold. Polling instead of
 * listening for events means it also reacts to regeneration, potions, other plugins calling
 * setHealth, element changes, respawns and joins without needing a handler for each.
 */
public class MetalLowHealthAttackSpeedListener implements Listener {

    private static final double HEALTH_THRESHOLD = 8.0;   // 4 hearts
    private static final double BONUS = 0.8;              // +80% attack speed (keep MetalElement's info text in sync)
    private static final int BONUS_PERCENT = 80;
    private static final long CHECK_INTERVAL_TICKS = 5L;

    private static final NamespacedKey MODIFIER_KEY =
            new NamespacedKey("elementsmp", "metal_low_health_attack_speed");

    private final ElementManager elementManager;

    public MetalLowHealthAttackSpeedListener(JavaPlugin plugin, ElementManager elementManager) {
        this.elementManager = elementManager;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAll, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
    }

    private void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    private void refresh(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attribute == null) return;

        boolean shouldHave = qualifies(player);
        AttributeModifier existing = find(attribute);

        if (shouldHave && existing == null) {
            attribute.addTransientModifier(new AttributeModifier(
                    MODIFIER_KEY, BONUS, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        } else if (!shouldHave && existing != null) {
            attribute.removeModifier(existing);
        }
    }

    private boolean qualifies(Player player) {
        if (!player.isOnline() || player.isDead()) return false;
        if (player.getHealth() > HEALTH_THRESHOLD) return false;

        var data = elementManager.data(player.getUniqueId());
        return data != null && data.getCurrentElement() == ElementType.METAL
                && data.getUpgradeLevel(ElementType.METAL) >= BaseElement.PASSIVE_2_UPGRADE_LEVEL;
    }

    private static AttributeModifier find(AttributeInstance attribute) {
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (MODIFIER_KEY.equals(modifier.getKey())) return modifier;
        }
        return null;
    }

    /** Drops the bonus immediately (used when Metal's effects are cleared, e.g. on an element change). */
    public static void clear(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attribute == null) return;
        AttributeModifier existing = find(attribute);
        if (existing != null) attribute.removeModifier(existing);
    }
}
