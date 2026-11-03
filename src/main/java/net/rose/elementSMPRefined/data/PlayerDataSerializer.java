package net.rose.elementSMPRefined.data;

import net.rose.elementSMPRefined.core.API.element.ElementType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.UUID;

/**
 * Converts between {@link PlayerData} and Bukkit {@link ConfigurationSection}s.
 * <p>
 * Kept separate from {@code PlayerData} on purpose: the model has zero
 * knowledge of the storage format this way. If players.yml ever becomes
 * SQL/JSON/whatever, this is the only class that needs a rewrite.
 */
public final class PlayerDataSerializer {

    private PlayerDataSerializer() {
    }

    /** Builds a {@link PlayerData} from a config section, tolerating missing/corrupt fields. */
    public static PlayerData deserialize(UUID uuid, ConfigurationSection section) {
        PlayerData data = new PlayerData(uuid);
        if (section == null) {
            return data;
        }

        // Unknown/renamed element in storage - leave unset rather than crash the load.
        ElementType.parse(section.getString("element")).ifPresent(data::setCurrentElementWithoutReset);

        data.setCurrentElementUpgradeLevel(section.getInt("currentUpgradeLevel", 0));
        data.setPendingRerollerRefunds(section.getInt("pendingRerollerRefunds", 0));
        data.setPendingAdvancedRerollerRefunds(section.getInt("pendingAdvancedRerollerRefunds", 0));
        for (PassiveToggle toggle : PassiveToggle.values()) {
            data.setToggleEnabled(toggle, section.getBoolean(toggle.configKey(), true));
        }

        return data;
    }

    /** Writes {@code data} into {@code section}, replacing whatever was there before. */
    public static void serialize(PlayerData data, ConfigurationSection section) {
        ElementType current = data.getCurrentElement();
        section.set("element", current == null ? null : current.name());
        section.set("currentUpgradeLevel", data.getCurrentElementUpgradeLevel());
        section.set("pendingRerollerRefunds", data.getPendingRerollerRefunds());
        section.set("pendingAdvancedRerollerRefunds", data.getPendingAdvancedRerollerRefunds());
        for (PassiveToggle toggle : PassiveToggle.values()) {
            section.set(toggle.configKey(), data.isToggleEnabled(toggle));
        }

        section.set("items", null); // element cores were removed - drop the old owned-items list

        section.set("trust", null); // the old /trust lists are gone - teams live in teams.yml now
    }
}
