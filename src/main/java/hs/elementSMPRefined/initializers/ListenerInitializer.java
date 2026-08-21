package hs.elementSMPRefined.initializers;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.API.element.ListenerProvider;
import hs.elementSMPRefined.ability.passive.air.AirElement;
import hs.elementSMPRefined.ability.passive.air.listeners.AirFallImpactListener;
import hs.elementSMPRefined.ability.passive.air.listeners.AirCombatListener;
import hs.elementSMPRefined.ability.passive.death.listeners.DeathFriendlyMobListener;
import hs.elementSMPRefined.ability.passive.death.listeners.DeathPassiveHunger;
import hs.elementSMPRefined.ability.passive.death.listeners.DeathRawFoodListener;
import hs.elementSMPRefined.ability.passive.earth.listeners.EarthVeinMinerListener;
import hs.elementSMPRefined.ability.passive.fire.listeners.FireCombatListener;
import hs.elementSMPRefined.ability.passive.frost.listeners.FrostFrozenPunchListener;
import hs.elementSMPRefined.ability.passive.frost.listeners.FrostPassiveListener;
import hs.elementSMPRefined.ability.passive.metal.MetalElement;
import hs.elementSMPRefined.ability.passive.metal.listeners.MetalArrowImmunityListener;
import hs.elementSMPRefined.ability.passive.metal.listeners.MetalChainStunListener;
import hs.elementSMPRefined.listeners.GUIListener;
import hs.elementSMPRefined.listeners.ability.AbilityListener;
import hs.elementSMPRefined.listeners.combat.CombatListener;
import hs.elementSMPRefined.listeners.item.AdvancedRerollerHandler;
import hs.elementSMPRefined.listeners.item.ElementCombatListener;
import hs.elementSMPRefined.listeners.item.ElementItemCraftingListener;
import hs.elementSMPRefined.listeners.item.ElementItemDeathListener;
import hs.elementSMPRefined.listeners.item.ElementItemInteractionListener;
import hs.elementSMPRefined.listeners.item.RerollerHandler;
import hs.elementSMPRefined.listeners.item.UpgraderHandler;
import hs.elementSMPRefined.listeners.player.GameModeListener;
import hs.elementSMPRefined.listeners.player.PlayerLifecycleListener;
import hs.elementSMPRefined.listeners.status.DisarmListener;
import hs.elementSMPRefined.listeners.status.StatusEffectListener;
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
    private PlayerLifecycleListener playerLifecycleListener;
    private AirFallImpactListener airFallImpactListener;
    private DeathFriendlyMobListener deathFriendlyMobListener;
    private FrostPassiveListener frostPassiveListener;
    private GUIListener guiListener;
    private AbilityListener abilityListener;
    private hs.elementSMPRefined.ability.main.metal.MetalDashAbility metalDashAbility;

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
        pluginManager.registerEvents(plugin.getEffectService(), plugin);
        pluginManager.registerEvents(new GameModeListener(plugin.getManaManager(), plugin.getConfigManager()), plugin);
        pluginManager.registerEvents(new CombatListener(plugin.getTrustManager(), plugin.getElementManager()), plugin);

        this.abilityListener = new AbilityListener(plugin, plugin.getElementManager(), plugin.getDisarmManager());
        pluginManager.registerEvents(abilityListener, plugin);

        pluginManager.registerEvents(new StatusEffectListener(plugin), plugin);
        pluginManager.registerEvents(new DisarmListener(plugin.getDisarmManager()), plugin);

        this.guiListener = new GUIListener(plugin);
        pluginManager.registerEvents(guiListener, plugin);
    }

    private void registerItemListeners() {
        pluginManager.registerEvents(new ElementItemInteractionListener(plugin, plugin.getItemManager()), plugin);
        pluginManager.registerEvents(new ElementItemCraftingListener(plugin, plugin.getElementManager()), plugin);
        pluginManager.registerEvents(new ElementItemDeathListener(plugin, plugin.getElementManager()), plugin);
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

        // Store special listeners that need cross-references
        storeSpecialListeners();
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

        this.deathFriendlyMobListener = new DeathFriendlyMobListener(plugin, plugin.getTrustManager());
        pluginManager.registerEvents(deathFriendlyMobListener, plugin);

        this.frostPassiveListener = new FrostPassiveListener(plugin, plugin.getElementManager());
        pluginManager.registerEvents(frostPassiveListener, plugin);

        pluginManager.registerEvents(new EarthVeinMinerListener(plugin.getElementManager()), plugin);

        var metalElement = plugin.getElementManager().get(ElementType.METAL);
        if (metalElement instanceof MetalElement metalElementImpl) {
            this.metalDashAbility = metalElementImpl.getMetalDashAbility();
        }

        // Upgrade II passives that were implemented but never wired in
        pluginManager.registerEvents(new AirCombatListener(plugin.getElementManager()), plugin);
        pluginManager.registerEvents(new FireCombatListener(plugin.getElementManager(), plugin.getTrustManager()), plugin);
        pluginManager.registerEvents(new DeathRawFoodListener(plugin.getElementManager()), plugin);
        pluginManager.registerEvents(new MetalArrowImmunityListener(plugin.getElementManager(), plugin.getTrustManager()), plugin);
        pluginManager.registerEvents(new MetalChainStunListener(plugin), plugin);
        pluginManager.registerEvents(new FrostFrozenPunchListener(plugin, plugin.getElementManager()), plugin);

        // Death Upgrade II passive (nearby enemies get Hunger) is a periodic pulse, not an event
        var deathPassiveHunger = new DeathPassiveHunger(plugin.getElementManager());
        plugin.getTaskScheduler().runTimerSeconds(
                () -> plugin.getServer().getOnlinePlayers().forEach(deathPassiveHunger::applyPassiveHunger),
                3, 3
        );
    }

    private void storeSpecialListeners() {
        // Listeners are now available for PlayerLifecycleListener
    }

    private void registerLifecycleListener() {
        this.playerLifecycleListener = new PlayerLifecycleListener(
                plugin,
                plugin.getElementManager(),
                plugin.getManaManager(),
                plugin.getEffectService(),
                plugin.getDisarmManager(),
                frostPassiveListener,
                airFallImpactListener,
                guiListener,
                abilityListener,
                metalDashAbility
        );
        pluginManager.registerEvents(playerLifecycleListener, plugin);
    }

    public void cleanup() {
        if (deathFriendlyMobListener != null) {
            deathFriendlyMobListener.cleanup();
        }
        if (frostPassiveListener != null) {
            frostPassiveListener.cleanup();
        }
    }

    // Getters for listeners that need to be accessed elsewhere
    public AirFallImpactListener getAirFallImpactListener() {
        return airFallImpactListener;
    }

    public DeathFriendlyMobListener getDeathFriendlyMobListener() {
        return deathFriendlyMobListener;
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
}