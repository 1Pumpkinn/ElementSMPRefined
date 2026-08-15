package hs.elementSMPRefined.initializers;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.ability.passive.air.listeners.AirFallImpactListener;
import hs.elementSMPRefined.ability.passive.death.listeners.DeathFriendlyMobListener;
import hs.elementSMPRefined.ability.passive.frost.listeners.FrostPassiveListener;
import hs.elementSMPRefined.ability.passive.metal.MetalElement;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.listeners.GUIListener;
import hs.elementSMPRefined.listeners.StatusEffectListener;
import hs.elementSMPRefined.listeners.ability.AbilityListener;
import hs.elementSMPRefined.listeners.combat.CombatListener;
import hs.elementSMPRefined.listeners.item.AdvancedRerollerHandler;
import hs.elementSMPRefined.listeners.item.ElementCombatListener;
import hs.elementSMPRefined.listeners.item.ElementItemCraftingListener;
import hs.elementSMPRefined.listeners.item.ElementItemDeathListener;
import hs.elementSMPRefined.listeners.item.ElementItemInteractionListener;
import hs.elementSMPRefined.listeners.item.ElementInventoryProtectionListener;
import hs.elementSMPRefined.listeners.item.RerollerHandler;
import hs.elementSMPRefined.listeners.item.UpgraderHandler;
import hs.elementSMPRefined.listeners.player.GameModeListener;
import hs.elementSMPRefined.listeners.player.PlayerLifecycleListener;
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
        setupListenerReferences();
    }

    private void registerCoreListeners() {
        this.playerLifecycleListener = new PlayerLifecycleListener(
                plugin, 
                plugin.getElementManager(), 
                plugin.getManaManager(), 
                plugin.getEffectService()
        );
        pluginManager.registerEvents(playerLifecycleListener, plugin);
        pluginManager.registerEvents(plugin.getEffectService(), plugin);
        pluginManager.registerEvents(new GameModeListener(plugin.getManaManager(), plugin.getConfigManager()), plugin);
        pluginManager.registerEvents(new CombatListener(plugin.getTrustManager(), plugin.getElementManager()), plugin);
        
        this.abilityListener = new AbilityListener(plugin, plugin.getElementManager());
        pluginManager.registerEvents(abilityListener, plugin);
        
        pluginManager.registerEvents(new StatusEffectListener(plugin), plugin);
        
        this.guiListener = new GUIListener(plugin);
        pluginManager.registerEvents(guiListener, plugin);
    }

    private void registerItemListeners() {
        pluginManager.registerEvents(new ElementItemInteractionListener(plugin, plugin.getElementManager(), plugin.getItemManager()), plugin);
        pluginManager.registerEvents(new ElementItemCraftingListener(plugin, plugin.getElementManager()), plugin);
        pluginManager.registerEvents(new ElementItemDeathListener(plugin, plugin.getElementManager()), plugin);
        pluginManager.registerEvents(new ElementInventoryProtectionListener(plugin, plugin.getElementManager()), plugin);
        pluginManager.registerEvents(new ElementCombatListener(plugin.getItemManager()), plugin);
        pluginManager.registerEvents(new RerollerHandler(plugin, plugin.getElementManager()), plugin);
        pluginManager.registerEvents(new AdvancedRerollerHandler(plugin, plugin.getElementManager()), plugin);
        pluginManager.registerEvents(new UpgraderHandler(plugin, plugin.getElementManager()), plugin);
    }

    private void registerElementListeners() {
        // Air fall impact listener
        this.airFallImpactListener = new AirFallImpactListener(plugin, plugin.getElementManager());
        pluginManager.registerEvents(airFallImpactListener, plugin);
        
        // Set the listener reference in AirElement
        var airElement = plugin.getElementManager().get(ElementType.AIR);
        if (airElement instanceof hs.elementSMPRefined.ability.passive.air.AirElement airElementImpl) {
            airElementImpl.setFallImpactListener(airFallImpactListener);
        }
        
        // Register other element listeners
        pluginManager.registerEvents(new hs.elementSMPRefined.ability.passive.air.listeners.AirCombatListener(plugin.getElementManager()), plugin);
        pluginManager.registerEvents(new hs.elementSMPRefined.ability.passive.water.listeners.WaterDrowningImmunityListener(plugin.getElementManager()), plugin);
        pluginManager.registerEvents(new hs.elementSMPRefined.ability.passive.fire.listeners.FireImmunityListener(plugin.getElementManager()), plugin);
        pluginManager.registerEvents(new hs.elementSMPRefined.ability.passive.fire.listeners.FireCombatListener(plugin.getElementManager(), plugin.getTrustManager()), plugin);
        pluginManager.registerEvents(new hs.elementSMPRefined.ability.passive.fire.listeners.FireballProtectionListener(), plugin);
        pluginManager.registerEvents(new hs.elementSMPRefined.ability.passive.earth.listeners.EarthVeinMinerListener(plugin.getElementManager()), plugin);
        pluginManager.registerEvents(new hs.elementSMPRefined.ability.passive.life.listeners.LifeRegenListener(plugin.getElementManager()), plugin);
        pluginManager.registerEvents(new hs.elementSMPRefined.ability.passive.life.LifeElementCraftListener(plugin, plugin.getElementManager()), plugin);
        pluginManager.registerEvents(new hs.elementSMPRefined.ability.passive.death.listeners.DeathRawFoodListener(plugin.getElementManager()), plugin);
        
        this.deathFriendlyMobListener = new DeathFriendlyMobListener(plugin, plugin.getTrustManager());
        pluginManager.registerEvents(deathFriendlyMobListener, plugin);
        
        pluginManager.registerEvents(new hs.elementSMPRefined.ability.passive.death.DeathElementCraftListener(plugin, plugin.getElementManager()), plugin);
        pluginManager.registerEvents(new hs.elementSMPRefined.ability.passive.metal.listeners.MetalArrowImmunityListener(plugin.getElementManager()), plugin);
        pluginManager.registerEvents(new hs.elementSMPRefined.ability.passive.metal.listeners.MetalChainStunListener(plugin), plugin);
        
        this.frostPassiveListener = new FrostPassiveListener(plugin, plugin.getElementManager());
        pluginManager.registerEvents(frostPassiveListener, plugin);
        
        pluginManager.registerEvents(new hs.elementSMPRefined.ability.passive.frost.listeners.FrostFrozenPunchListener(plugin, plugin.getElementManager()), plugin);

        // Get MetalDashAbility from MetalElement
        var metalElement = plugin.getElementManager().get(ElementType.METAL);
        if (metalElement instanceof MetalElement metalElementImpl) {
            this.metalDashAbility = metalElementImpl.getMetalDashAbility();
        }
    }

    private void setupListenerReferences() {
        // Set cross-references after all listeners are registered
        if (playerLifecycleListener != null) {
            playerLifecycleListener.setFrostPassiveListener(frostPassiveListener);
            playerLifecycleListener.setGuiListener(guiListener);
            playerLifecycleListener.setAbilityListener(abilityListener);
            playerLifecycleListener.setAirFallImpactListener(airFallImpactListener);
            playerLifecycleListener.setMetalDashAbility(metalDashAbility);
        }
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