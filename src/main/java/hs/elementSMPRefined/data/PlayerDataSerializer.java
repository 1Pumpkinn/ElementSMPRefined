package hs.elementSMPRefined.data;

import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.API.element.ElementId;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
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

        String elementName = section.getString("element");
        if (elementName != null) {
            try {
                data.setCurrentElementWithoutReset(parseId(elementName));
            } catch (IllegalArgumentException ignored) {
                // Unknown/renamed element in storage - leave unset rather than crash the load.
            }
        }

        data.setMana(section.getInt("mana", PlayerData.DEFAULT_MANA));
        data.setCurrentElementUpgradeLevel(section.getInt("currentUpgradeLevel", 0));

        for (String name : section.getStringList("items")) {
            try {
                data.addElementItem(parseId(name));
            } catch (IllegalArgumentException ignored) {
                // Skip invalid/renamed element item entries.
            }
        }

        ConfigurationSection trust = section.getConfigurationSection("trust");
        if (trust != null) {
            for (String key : trust.getKeys(false)) {
                try {
                    data.addTrustedPlayer(UUID.fromString(key));
                } catch (IllegalArgumentException ignored) {
                    // Corrupt UUID entry - skip rather than fail the whole load.
                }
            }
        }

        return data;
    }

    /** Writes {@code data} into {@code section}, replacing whatever was there before. */
    public static void serialize(PlayerData data, ConfigurationSection section) {
        section.set("element", data.getCurrentElementId() == null ? null : data.getCurrentElementId().toString());
        section.set("mana", data.getMana());
        section.set("currentUpgradeLevel", data.getCurrentElementUpgradeLevel());

        List<String> items = new ArrayList<>();
        for (ElementId id : data.getOwnedItemIds()) {
            items.add(id.toString());
        }
        section.set("items", items);

        section.set("trust", null); // clear stale entries before rewriting
        if (!data.getTrustedPlayers().isEmpty()) {
            ConfigurationSection trust = section.createSection("trust");
            for (UUID trusted : data.getTrustedPlayers()) {
                trust.set(trusted.toString(), true);
            }
        }
    }

    private static ElementId parseId(String value) {
        if (value.contains(":")) {
            return ElementId.parse(value);
        }
        return ElementId.builtin(ElementType.valueOf(value.toUpperCase()));
    }
}