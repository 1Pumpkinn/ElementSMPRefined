package net.rose.elementSMPRefined.util.damage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Builds the death message for a player killed by an element ability, so it names the
 * ability ("X was slain by Y using Magma Beam") instead of a generic damage-type message.
 * <p>
 * The ability name is always yellow, whatever colour the ability uses for itself elsewhere.
 */
final class AbilityDeathMessage {

    private AbilityDeathMessage() {}

    /** @param abilityName the ability's display name; any colour codes in it are ignored */
    static Component build(Player victim, String abilityName) {
        Component ability = Component.text(ChatColor.stripColor(abilityName), NamedTextColor.YELLOW);
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            return Component.translatable("death.attack.player.item", victim.name(), killer.name(), ability);
        }
        return Component.empty()
                .append(victim.name())
                .append(Component.text(" was killed by "))
                .append(ability);
    }
}