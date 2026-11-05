package net.rose.elementevents.api.config;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.data.YamlFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Loads {@code plugins/ElementSMPRefined/event_villagers.yml}: a map of villager type id to its
 * settings. The default file is copied out of the jar the first time. If the file is broken on
 * reload, the previously loaded types are kept and the error is logged (a broken edit never
 * wipes working villagers).
 */
public class EventVillagerConfig {

    private static final String FILE_NAME = "event_villagers.yml";

    private final ElementSMPRefined plugin;
    private final File file;
    private Map<String, ConfigurationSection> types = Map.of();

    public EventVillagerConfig(ElementSMPRefined plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }
        reload();
    }

    /** @return true if the file loaded; false if it was broken and the old types were kept */
    public boolean reload() {
        try {
            YamlConfiguration yaml = YamlFiles.loadStrict(file);
            ConfigurationSection root = yaml.getConfigurationSection("villagers");
            Map<String, ConfigurationSection> loaded = new LinkedHashMap<>();
            if (root != null) {
                for (String id : root.getKeys(false)) {
                    ConfigurationSection section = root.getConfigurationSection(id);
                    if (section != null) loaded.put(id.toLowerCase(Locale.ROOT), section);
                }
            }
            this.types = Collections.unmodifiableMap(loaded);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load " + FILE_NAME + " - keeping the previous villager types", e);
            return false;
        }
    }

    public ConfigurationSection getType(String id) {
        return id == null ? null : types.get(id.toLowerCase(Locale.ROOT));
    }

    public Set<String> getTypeIds() {
        return types.keySet();
    }
}
