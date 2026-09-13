package net.rose.elementSMPRefined.core.addons;

import net.rose.elementSMPRefined.core.API.addon.ElementAddon;
import net.rose.elementSMPRefined.core.API.ability.Ability;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.items.api.ElementItem;
import net.rose.elementSMPRefined.core.registry.AbilityRegistry;
import net.rose.elementSMPRefined.core.registry.AddonResourceRegistry;
import net.rose.elementSMPRefined.core.registry.ItemRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.Listener;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Coordinates addon registration and prevents an addon from being registered twice.
 */
public final class AddonManager {
    private final ElementSMPRefined plugin;
    private final Set<String> registeredAddons = new HashSet<>();
    private final Map<String, Listener> listeners = new HashMap<>();
    private final AbilityRegistry abilities;
    private final AddonResourceRegistry resources = new AddonResourceRegistry();

    public AddonManager(ElementSMPRefined plugin) {
        this.plugin = plugin;
        this.abilities = new AbilityRegistry(plugin);
    }

    public void register(ElementAddon addon) {
        if (addon == null || addon.getName() == null || addon.getName().isBlank()) {
            throw new IllegalArgumentException("Addon and addon name are required");
        }

        if (!registeredAddons.add(addon.getName())) {
            plugin.getLogger().warning("Addon " + addon.getName() + " is already registered.");
            return;
        }

        addon.register(plugin.getElementApi());
        plugin.getLogger().info("Registered addon: " + addon.getName());
    }

    public boolean isRegistered(String name) {
        return name != null && registeredAddons.contains(name);
    }

    public void registerElement(net.rose.elementSMPRefined.core.API.element.Element element) {
        plugin.registerAddonElement(element);
    }

    public void registerAbility(String id, Ability ability) {
        abilities.register(id, ability);
    }

    public void registerItem(String id, ElementItem item, ItemRegistry.ItemData data) {
        plugin.getItemManager().registerAddon(id, item, data);
    }

    /**
     * Registers a listener owned by an addon under a unique ID, so what an addon
     * wired up can be inspected later via {@link #getListener(String)}. The ID
     * must be unique across all addons, matching the collision guard already
     * used by {@link AbilityRegistry#register} and {@link ItemRegistry#registerAddon}.
     */
    public void registerListener(String id, Listener listener) {
        if (id == null || id.isBlank() || listener == null) {
            throw new IllegalArgumentException("Listener ID and listener are required");
        }
        if (listeners.putIfAbsent(id, listener) != null) {
            throw new IllegalArgumentException("Listener " + id + " is already registered");
        }
        plugin.registerAddonListener(listener);
    }

    /** Passives use the same Bukkit listener lifecycle, but have a semantic API name. */
    public void registerPassive(String id, Listener listener) {
        registerListener(id, listener);
    }

    public Listener getListener(String id) {
        return listeners.get(id);
    }

    public Set<String> getListenerIds() {
        return Collections.unmodifiableSet(listeners.keySet());
    }

    public void registerDimension(NamespacedKey id, World world) {
        resources.registerDimension(id, world);
    }

    public void registerBiome(NamespacedKey id) {
        resources.registerBiome(id);
    }

    public AbilityRegistry abilities() {
        return abilities;
    }

    public AddonResourceRegistry resources() {
        return resources;
    }
}