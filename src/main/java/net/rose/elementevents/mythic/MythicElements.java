package net.rose.elementevents.mythic;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.data.YamlFiles;

import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Who currently holds each mythic element. Only one player on the whole server can hold a given
 * mythic element, so this is the single source of truth for that rule. Persisted to
 * {@code plugins/ElementSMPRefined/mythic_elements.yml} on every change (changes are rare).
 */
public class MythicElements {
    private static final String FILE_NAME = "mythic_elements.yml";

    private final ElementSMPRefined plugin;
    private final File file;
    private final Map<ElementType, UUID> owners = new EnumMap<>(ElementType.class);

    public MythicElements(ElementSMPRefined plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
        load();
    }

    private void load() {
        try {
            YamlConfiguration yaml = YamlFiles.loadStrict(file);
            var section = yaml.getConfigurationSection("owners");
            if (section == null) return;
            for (String key : section.getKeys(false)) {
                ElementType type = ElementType.parse(key).orElse(null);
                String raw = section.getString(key);
                if (type == null || !type.isMythic() || raw == null) continue;
                owners.put(type, UUID.fromString(raw));
            }
        } catch (Exception e) {
            plugin.getLogger()
                    .log(
                            Level.SEVERE,
                            "Could not read "
                                    + FILE_NAME
                                    + " - mythic elements will look unclaimed until it is fixed",
                            e);
            try {
                YamlFiles.quarantine(file);
            } catch (Exception ignored) {
                // best effort: the broken file is only copied for manual repair
            }
        }
    }

    private void save() {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            owners.forEach((type, id) -> yaml.set("owners." + type.name().toLowerCase(), id.toString()));
            YamlFiles.saveAtomic(yaml, file);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + FILE_NAME, e);
        }
    }

    public @Nullable UUID ownerOf(ElementType type) {
        return owners.get(type);
    }

    public boolean isClaimed(ElementType type) {
        return owners.containsKey(type);
    }

    /**
     * Claims {@code type} for {@code player}.
     *
     * @return true if it was free or already theirs; false if someone else holds it
     */
    public boolean claim(ElementType type, UUID player) {
        if (!type.isMythic()) return false;
        UUID current = owners.get(type);
        if (current != null) return current.equals(player);
        owners.put(type, player);
        save();
        return true;
    }

    /** Frees {@code type}, but only if {@code player} is the one holding it. */
    public void release(ElementType type, UUID player) {
        if (player.equals(owners.get(type))) {
            owners.remove(type);
            save();
        }
    }
}
