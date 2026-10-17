package net.rose.elementSMPRefined.util.damage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

/**
 * Builds the death message for a player killed by an element ability, so it names the
 * ability ("X was slain by Y using Magma Beam") instead of a generic damage-type message.
 */
final class AbilityDeathMessage {

    private AbilityDeathMessage() {}

    /** @param abilityName the ability's display name; legacy colour codes are kept */
    static Component build(Player victim, String abilityName) {
        Component ability = LegacyComponentSerializer.legacySection().deserialize(abilityName);
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
