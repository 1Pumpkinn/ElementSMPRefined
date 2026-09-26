package net.rose.elementSMPRefined.core.initializers;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.API.element.ListenerProvider;
import net.rose.elementSMPRefined.ability.main.metal.MetalShardAbility;
import net.rose.elementSMPRefined.ability.passive.air.AirElement;
import net.rose.elementSMPRefined.ability.passive.air.listeners.AirFallImpactListener;
import net.rose.elementSMPRefined.ability.passive.air.listeners.AirCombatListener;
import net.rose.elementSMPRefined.ability.passive.earth.listeners.EarthVeinMinerListener;
import net.rose.elementSMPRefined.ability.passive.fire.listeners.FireSmeltListener;
import net.rose.elementSMPRefined.ability.passive.frost.listeners.FrostFrozenPunchListener;
import net.rose.elementSMPRefined.ability.passive.frost.listeners.FrostPassiveListener;
import net.rose.elementSMPRefined.ability.passive.metal.MetalElement;
import net.rose.elementSMPRefined.ability.passive.metal.listeners.MetalArrowImmunityListener;
import net.rose.elementSMPRefined.ability.passive.metal.listeners.MetalChainStunListener;
import net.rose.elementSMPRefined.listeners.GUIListener;
import net.rose.elementSMPRefined.listeners.ability.AbilityListener;
import net.rose.elementSMPRefined.listeners.handler.AdvancedRerollerHandler;
import net.rose.elementSMPRefined.listeners.item.ElementCombatListener;
import net.rose.elementSMPRefined.listeners.item.ElementItemCraftingListener;
import net.rose.elementSMPRefined.listeners.item.PlayerDeathListener;
import net.rose.elementSMPRefined.listeners.item.ElementItemInteractionListener;
import net.rose.elementSMPRefined.listeners.handler.RerollerHandler;
import net.rose.elementSMPRefined.listeners.handler.UpgraderHandler;
import net.rose.elementSMPRefined.listeners.player.InvisibilityNameHider;
import net.rose.elementSMPRefined.listeners.player.PlayerLifecycle;
import net.rose.elementSMPRefined.listeners.status.DisarmListener;
import net.rose.elementSMPRefined.listeners.status.StatusEffectListener;
import net.rose.elementSMPRefined.util.server.DimensionDisable;
import net.rose.elementSMPRefined.util.server.GracePeriod;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles registration of all plugin listeners.
 * Centralizes listener registration logic and manages listener dependencies.
 */
public class ListenerInitializer {
    private final ElementSMPRefined plugin;
    private final PluginManager pluginManager;

    // Store references to listeners that need cleanup or cross-references
    private PlayerLifecycle playerLifecycleListener;
    private AirFallImpactListener airFallImpactListener;
    private FrostPassiveListener frostPassiveListener;
    private GUIListener guiListener;
    private AbilityListener abilityListener;
    private MetalShardAbility metalShardAbility;
    private GracePeriod gracePeriod;

    public ListenerInitializer(JavaPlugin plugin) {
        this.plugin = (ElementSMPRefined) plugin;
        this.pluginManager = plugin.getServer().getPluginManager();
    }

    public void registerListeners() {
        registerCoreListeners();
        registerItemListeners();
        registerElementListeners();
        registerLifecycleListener();
    }

    private void registerCoreListeners() {
        pluginManager.registerEvents(new InvisibilityNameHider(), plugin);
        pluginManager.registerEvents(plugin.getEffectService(), plugin);
        pluginManager.registerEvents(new net.rose.elementSMPRefined.listeners.combat.CombatListener(plugin.getTrustManager()), plugin);

        this.abilityListener = new AbilityListener(plugin, plugin.getElementManager(), plugin.getDisarmManager());
        pluginManager.registerEvents(abilityListener, plugin);

        pluginManager.registerEvents(new StatusEffectListener(plugin), plugin);
        pluginManager.registerEvents(new DisarmListener(plugin.getDisarmManager()), plugin);

        this.guiListener = new GUIListener(plugin);
        pluginManager.registerEvents(guiListener, plugin);

        pluginManager.registerEvents(new DimensionDisable(plugin.getConfigManager()), plugin);

        this.gracePeriod = new GracePeriod(plugin, plugin.getConfigManager(), plugin.getTaskScheduler());
        pluginManager.registerEvents(gracePeriod, plugin);
        gracePeriod.autoStartIfConfigured();

        // Also refreshes a player's action bar immediately on AbilityActivateEvent -
        // see CooldownActionBarTask's class doc for why that's needed alongside its
        // periodic tick.
        pluginManager.registerEvents(plugin.getCooldownActionBarTask(), plugin);
    }

    private void registerItemListeners() {
        pluginManager.registerEvents(new ElementItemInteractionListener(plugin, plugin.getItemManager()), plugin);
        pluginManager.registerEvents(new ElementItemCraftingListener(plugin, plugin.getElementManager()), plugin);
        pluginManager.registerEvents(new PlayerDeathListener(plugin, plugin.getElementManager()), plugin);
        pluginManager.registerEvents(new ElementCombatListener(plugin.getItemManager()), plugin);
        pluginManager.registerEvents(new RerollerHandler(plugin, plugin.getElementManager()), plugin);
        pluginManager.registerEvents(new AdvancedRerollerHandler(plugin, plugin.getElementManager()), plugin);
        pluginManager.registerEvents(new UpgraderHandler(plugin, plugin.getElementManager()), plugin);
    }

    private void registerElementListeners() {
        // Register core element listeners
        registerCoreElementListeners();

        // Register ListenerProvider listeners (auto-discovered from elements)
        plugin.getElementManager().getAllElements().forEach(element -> {
            if (element instanceof ListenerProvider provider) {
                provider.getListeners(plugin).forEach(listener -> {
                    pluginManager.registerEvents(listener, plugin);
                });
            }
        });
    }

    /**
     * Register core element listeners that don't fit the ListenerProvider pattern
     * (e.g., listeners that need special setup or cross-references)
     */
    private void registerCoreElementListeners() {
        this.airFallImpactListener = new AirFallImpactListener(plugin, plugin.getElementManager());
        pluginManager.registerEvents(airFallImpactListener, plugin);

        var airElement = plugin.getElementManager().get(ElementType.AIR);
        if (airElement instanceof AirElement airElementImpl) {
            airElementImpl.setFallImpactListener(airFallImpactListener);
        }

        this.frostPassiveListener = new FrostPassiveListener(plugin, plugin.getElementManager());
        pluginManager.registerEvents(frostPassiveListener, plugin);

        pluginManager.registerEvents(new EarthVeinMinerListener(plugin.getElementManager()), plugin);

        var metalElement = plugin.getElementManager().get(ElementType.METAL);
        if (metalElement instanceof MetalElement metalElementImpl) {
            this.metalShardAbility = metalElementImpl.getMetalDashAbility();
        }

        pluginManager.registerEvents(new AirCombatListener(plugin.getElementManager()), plugin);
        pluginManager.registerEvents(new FireSmeltListener(plugin.getElementManager()), plugin);
        pluginManager.registerEvents(new MetalArrowImmunityListener(plugin.getElementManager(), plugin.getTrustManager()), plugin);
        pluginManager.registerEvents(new MetalChainStunListener(plugin), plugin);
        pluginManager.registerEvents(new FrostFrozenPunchListener(plugin, plugin.getElementManager()), plugin);
    }

    private void registerLifecycleListener() {
        this.playerLifecycleListener = new PlayerLifecycle(
                plugin,
                plugin.getElementManager(),
                plugin.getCooldownManager(),
                plugin.getEffectService(),
                plugin.getDisarmManager(),
                frostPassiveListener,
                airFallImpactListener,
                guiListener,
                abilityListener,
                metalShardAbility
        );
        pluginManager.registerEvents(playerLifecycleListener, plugin);
    }

    public void cleanup() {
        if (frostPassiveListener != null) {
            frostPassiveListener.cleanup();
        }
        if (gracePeriod != null) {
            gracePeriod.cleanup();
        }
    }

    // Getters for listeners that need to be accessed elsewhere
    public AirFallImpactListener getAirFallImpactListener() {
        return airFallImpactListener;
    }

    public FrostPassiveListener getFrostPassiveListener() {
        return frostPassiveListener;
    }

    public GUIListener getGuiListener() {
        return guiListener;
    }

    public AbilityListener getAbilityListener() {
        return abilityListener;
    }

    public GracePeriod getGracePeriod() {
        return gracePeriod;
    }
}