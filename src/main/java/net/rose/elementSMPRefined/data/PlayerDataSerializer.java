package net.rose.elementSMPRefined.data;

import net.rose.elementSMPRefined.core.API.element.ElementType;
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

        // Unknown/renamed element in storage - leave unset rather than crash the load.
        // parse() also reads the old "elements:fire" format, so existing data keeps loading.
        ElementType.parse(section.getString("element")).ifPresent(data::setCurrentElementWithoutReset);

        data.setCurrentElementUpgradeLevel(section.getInt("currentUpgradeLevel", 0));
        data.setPendingRerollerRefunds(section.getInt("pendingRerollerRefunds", 0));
        data.setPendingAdvancedRerollerRefunds(section.getInt("pendingAdvancedRerollerRefunds", 0));

        for (String name : section.getStringList("items")) {
            // Skip invalid/renamed element item entries.
            ElementType.parse(name).ifPresent(data::addElementItem);
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
        ElementType current = data.getCurrentElement();
        section.set("element", current == null ? null : current.name());
        section.set("currentUpgradeLevel", data.getCurrentElementUpgradeLevel());
        section.set("pendingRerollerRefunds", data.getPendingRerollerRefunds());
        section.set("pendingAdvancedRerollerRefunds", data.getPendingAdvancedRerollerRefunds());

        List<String> items = new ArrayList<>();
        for (ElementType type : data.getOwnedItems()) {
            items.add(type.name());
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
}
