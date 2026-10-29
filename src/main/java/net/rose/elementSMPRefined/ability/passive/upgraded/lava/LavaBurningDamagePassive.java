package net.rose.elementSMPRefined.ability.passive.upgraded.lava;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

public class LavaBurningDamagePassive {

    private static final double ATTACK_DAMAGE_BONUS = 1; // keep LavaElement's info text in sync
    private static final long CHECK_INTERVAL_TICKS = 5L;

    private static final NamespacedKey MODIFIER_KEY =
            new NamespacedKey("elementsmp", "lava_burning_attack_damage");

    private final ElementSMPRefined plugin;

    public LavaBurningDamagePassive(ElementSMPRefined plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAll, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
    }

    public void clear(Player player) {
        setBonus(player, false);
    }

    private void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            setBonus(player, qualifies(player));
        }
    }

    private boolean qualifies(Player player) {
        if (!player.isOnline() || player.isDead()) return false;
        if (player.getFireTicks() <= 0 && !player.isInLava()) return false;

        var data = plugin.getElementManager().data(player.getUniqueId());
        return data != null && data.getCurrentElement() == ElementType.LAVA
                && data.getUpgradeLevel(ElementType.LAVA) >= BaseElement.PASSIVE_2_UPGRADE_LEVEL;
    }

    private static void setBonus(Player player, boolean on) {
        AttributeInstance attribute = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attribute == null) return;

        AttributeModifier existing = null;
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (MODIFIER_KEY.equals(modifier.getKey())) {
                existing = modifier;
                break;
            }
        }

        if (on && existing == null) {
            attribute.addTransientModifier(new AttributeModifier(
                    MODIFIER_KEY, ATTACK_DAMAGE_BONUS, AttributeModifier.Operation.ADD_NUMBER));
        } else if (!on && existing != null) {
            attribute.removeModifier(existing);
        }
    }
}
