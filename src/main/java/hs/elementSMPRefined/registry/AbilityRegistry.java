package hs.elementSMPRefined.registry;

import hs.elementSMPRefined.API.ability.Ability;
import hs.elementSMPRefined.ElementSMPRefined;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Registry for addon abilities that need to be discovered by ID. */
public final class AbilityRegistry {
    private final ElementSMPRefined plugin;
    private final Map<String, Ability> abilities = new HashMap<>();

    public AbilityRegistry(ElementSMPRefined plugin) {
        this.plugin = plugin;
    }

    public void register(String id, Ability ability) {
        if (id == null || id.isBlank() || ability == null) {
            throw new IllegalArgumentException("Ability ID and ability are required");
        }
        if (abilities.putIfAbsent(id, ability) != null) {
            throw new IllegalArgumentException("Ability " + id + " is already registered");
        }
        plugin.getLogger().info("Registered ability: " + id);
    }

    public Ability get(String id) {
        return abilities.get(id);
    }

    public Collection<Ability> getAll() {
        return Collections.unmodifiableCollection(abilities.values());
    }
}