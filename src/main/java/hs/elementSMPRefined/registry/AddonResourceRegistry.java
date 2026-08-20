package hs.elementSMPRefined.registry;

import org.bukkit.NamespacedKey;
import org.bukkit.World;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Tracks addon-owned Bukkit dimensions and datapack-defined biome IDs. */
public final class AddonResourceRegistry {
    private final Map<NamespacedKey, World> dimensions = new HashMap<>();
    private final Set<NamespacedKey> biomes = new HashSet<>();

    public void registerDimension(NamespacedKey id, World world) {
        if (id == null || world == null) {
            throw new IllegalArgumentException("Dimension ID and world are required");
        }
        if (dimensions.putIfAbsent(id, world) != null) {
            throw new IllegalArgumentException("Dimension " + id + " is already registered");
        }
    }

    public void registerBiome(NamespacedKey id) {
        if (id == null || !biomes.add(id)) {
            throw new IllegalArgumentException("Biome ID is missing or already registered");
        }
    }

    public World getDimension(NamespacedKey id) {
        return dimensions.get(id);
    }

    public Set<NamespacedKey> getBiomeIds() {
        return Collections.unmodifiableSet(biomes);
    }
}