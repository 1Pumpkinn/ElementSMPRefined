package hs.elementSMPRefined;

import hs.elementSMPRefined.commands.*;
import hs.elementSMPRefined.listeners.GUIListener;
import hs.elementSMPRefined.listeners.StatusEffectListener;
import hs.elementSMPRefined.listeners.player.GameModeListener;
import hs.elementSMPRefined.listeners.player.PlayerLifecycleListener;
import hs.elementSMPRefined.managers.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class ElementSMPRefined extends JavaPlugin {
    private hs.elementSMPRefined.data.DataStore dataStore;
    private ConfigManager configManager;
    private ElementManager elementManager;
    private ManaManager manaManager;
    private TrustManager trustManager;
    private ItemManager itemManager;
    private hs.elementSMPRefined.elements.abilities.AbilityRegistry abilityRegistry;
    private hs.elementSMPRefined.services.EffectService effectService;
    private hs.elementSMPRefined.services.ValidationService validationService;
    private hs.elementSMPRefined.util.scheduling.TaskScheduler taskScheduler;
    private hs.elementSMPRefined.util.bukkit.MetadataHelper metadataHelper;
    private hs.elementSMPRefined.status.StatusEffectManager statusEffectManager;
    private hs.elementSMPRefined.elements.impl.earth.listeners.EarthFriendlyMobListener earthFriendlyMobListener;
    private hs.elementSMPRefined.elements.impl.death.listeners.DeathFriendlyMobListener deathFriendlyMobListener;
    private hs.elementSMPRefined.elements.impl.frost.listeners.FrostPassiveListener frostPassiveListener;
    private hs.elementSMPRefined.listeners.GUIListener guiListener;
    private hs.elementSMPRefined.listeners.ability.AbilityListener abilityListener;
    private hs.elementSMPRefined.elements.abilities.impl.metal.MetalDashAbility metalDashAbility;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            initializeCore();
            initializeManagers();
            initializeServices();
            initializeUtilities();
            registerComponents();
            startBackgroundTasks();

            getLogger().info("ElementPlugin v" + getDescription().getVersion() + " enabled successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable plugin", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        try {
            stopBackgroundTasks();
            if (statusEffectManager != null) {
                statusEffectManager.cleanup();
            }
            if (earthFriendlyMobListener != null) {
                earthFriendlyMobListener.cleanup();
            }
            if (deathFriendlyMobListener != null) {
                deathFriendlyMobListener.cleanup();
            }
            if (frostPassiveListener != null) {
                frostPassiveListener.cleanup();
            }
            saveAllData();
            getLogger().info("ElementPlugin disabled successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during plugin shutdown", e);
        }
    }

    private void initializeCore() {
        getLogger().info("Initializing core components...");
        this.configManager = new ConfigManager(this);
        this.dataStore = new hs.elementSMPRefined.data.DataStore(this);
        getLogger().info("Core components initialized");
    }

    private void initializeManagers() {
        getLogger().info("Initializing managers...");

        this.trustManager = new TrustManager(this);
        this.manaManager = new ManaManager(this, dataStore, configManager);
        this.elementManager = new ElementManager(this, dataStore, manaManager, trustManager, configManager);
        this.itemManager = new ItemManager(this, manaManager, configManager);
        this.statusEffectManager = new hs.elementSMPRefined.status.StatusEffectManager(this);

        getLogger().info("Managers initialized");
    }

    private void initializeServices() {
        getLogger().info("Initializing services...");

        this.effectService = new hs.elementSMPRefined.services.EffectService(this, elementManager);
        this.validationService = new hs.elementSMPRefined.services.ValidationService(trustManager);
        this.abilityRegistry = new hs.elementSMPRefined.elements.abilities.AbilityRegistry(this);

        getLogger().info("Services initialized");
    }

    private void initializeUtilities() {
        getLogger().info("Initializing utilities...");

        this.taskScheduler = new hs.elementSMPRefined.util.scheduling.TaskScheduler(this);
        this.metadataHelper = new hs.elementSMPRefined.util.bukkit.MetadataHelper(this);

        getLogger().info("Utilities initialized");
    }

    private void registerComponents() {
        registerCommands();
        registerListeners();
        registerRecipes();
    }

    private void registerCommands() {
        getLogger().info("Registering commands...");

        CommandRegister.register(this)
                .command("elements", new ElementInfoCommand(this))
                .command("trust", new TrustCommand(this, trustManager))
                .command("element", new ElementCommand(this))
                .command("mana", new ManaCommand(manaManager, configManager))
                .command("util", new UtilCommand(this))
                .command("togglerecipe", new ToggleRecipeCommand(this));

        getLogger().info("Commands registered");
    }

    private static class CommandRegister {
        private final ElementSMPRefined plugin;

        private CommandRegister(ElementSMPRefined plugin) {
            this.plugin = plugin;
        }

        static CommandRegister register(ElementSMPRefined plugin) {
            return new CommandRegister(plugin);
        }

        CommandRegister command(String name, org.bukkit.command.CommandExecutor executor) {
            var cmd = plugin.getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(executor);
                if (executor instanceof org.bukkit.command.TabCompleter completer) {
                    cmd.setTabCompleter(completer);
                }
            }
            return this;
        }
    }

    private void registerListeners() {
        getLogger().info("Registering listeners...");
        PluginManager pm = Bukkit.getPluginManager();

        PlayerLifecycleListener playerLifecycleListener = new PlayerLifecycleListener(this, elementManager, manaManager, effectService);
        pm.registerEvents(playerLifecycleListener, this);
        pm.registerEvents(effectService, this);
        pm.registerEvents(new GameModeListener(manaManager, configManager), this);
        pm.registerEvents(new hs.elementSMPRefined.listeners.combat.CombatListener(trustManager, elementManager), this);
        this.abilityListener = new hs.elementSMPRefined.listeners.ability.AbilityListener(this, elementManager);
        pm.registerEvents(abilityListener, this);
        pm.registerEvents(new StatusEffectListener(this), this);
        registerItemListeners(pm);
        this.guiListener = new GUIListener(this);
        pm.registerEvents(guiListener, this);
        registerElementListeners(pm);

        // Set listener references after they're created
        playerLifecycleListener.setFrostPassiveListener(frostPassiveListener);
        playerLifecycleListener.setGuiListener(guiListener);
        playerLifecycleListener.setAbilityListener(abilityListener);

        // Get MetalDashAbility from MetalElement
        var metalElement = elementManager.get(hs.elementSMPRefined.elements.ElementType.METAL);
        if (metalElement instanceof hs.elementSMPRefined.elements.impl.metal.MetalElement metalElementImpl) {
            this.metalDashAbility = metalElementImpl.getMetalDashAbility();
            playerLifecycleListener.setMetalDashAbility(metalDashAbility);
        }

        getLogger().info("Listeners registered");
    }

    private void registerItemListeners(PluginManager pm) {
        pm.registerEvents(new hs.elementSMPRefined.listeners.item.ElementItemInteractionListener(this, elementManager, itemManager), this);
        pm.registerEvents(new hs.elementSMPRefined.listeners.item.ElementItemCraftingListener(this, elementManager), this);
        pm.registerEvents(new hs.elementSMPRefined.listeners.item.ElementItemDeathListener(this, elementManager), this);
        pm.registerEvents(new hs.elementSMPRefined.listeners.item.ElementInventoryProtectionListener(this, elementManager), this);
        pm.registerEvents(new hs.elementSMPRefined.listeners.item.ElementCombatListener(itemManager), this);
        pm.registerEvents(new hs.elementSMPRefined.listeners.item.RerollerHandler(this, elementManager), this);
        pm.registerEvents(new hs.elementSMPRefined.listeners.item.AdvancedRerollerHandler(this, elementManager), this);
        pm.registerEvents(new hs.elementSMPRefined.listeners.item.UpgraderHandler(this, elementManager), this);
    }

    private void registerElementListeners(PluginManager pm) {
        pm.registerEvents(new hs.elementSMPRefined.elements.impl.air.listeners.FallDamageListener(elementManager), this);
        pm.registerEvents(new hs.elementSMPRefined.elements.impl.air.listeners.AirCombatListener(elementManager), this);
        pm.registerEvents(new hs.elementSMPRefined.elements.impl.water.listeners.WaterDrowningImmunityListener(elementManager), this);
        pm.registerEvents(new hs.elementSMPRefined.elements.impl.fire.listeners.FireImmunityListener(elementManager), this);
        pm.registerEvents(new hs.elementSMPRefined.elements.impl.fire.listeners.FireCombatListener(elementManager, trustManager), this);
        pm.registerEvents(new hs.elementSMPRefined.elements.impl.fire.listeners.FireballProtectionListener(), this);
        pm.registerEvents(new hs.elementSMPRefined.elements.impl.earth.listeners.EarthCharmListener(elementManager, this), this);
        this.earthFriendlyMobListener = new hs.elementSMPRefined.elements.impl.earth.listeners.EarthFriendlyMobListener(this, trustManager);
        pm.registerEvents(earthFriendlyMobListener, this);
        pm.registerEvents(new hs.elementSMPRefined.elements.impl.earth.listeners.EarthOreDropListener(elementManager), this);
        pm.registerEvents(new hs.elementSMPRefined.elements.impl.life.listeners.LifeRegenListener(elementManager), this);
        pm.registerEvents(new hs.elementSMPRefined.elements.impl.life.LifeElementCraftListener(this, elementManager), this);
        pm.registerEvents(new hs.elementSMPRefined.elements.impl.death.listeners.DeathRawFoodListener(elementManager), this);
        this.deathFriendlyMobListener = new hs.elementSMPRefined.elements.impl.death.listeners.DeathFriendlyMobListener(this, trustManager);
        pm.registerEvents(deathFriendlyMobListener, this);
        pm.registerEvents(new hs.elementSMPRefined.elements.impl.death.DeathElementCraftListener(this, elementManager), this);
        pm.registerEvents(new hs.elementSMPRefined.elements.impl.metal.listeners.MetalArrowImmunityListener(elementManager), this);
        pm.registerEvents(new hs.elementSMPRefined.elements.impl.metal.listeners.MetalChainStunListener(this), this);
        this.frostPassiveListener = new hs.elementSMPRefined.elements.impl.frost.listeners.FrostPassiveListener(this, elementManager);
        pm.registerEvents(frostPassiveListener, this);
        pm.registerEvents(new hs.elementSMPRefined.elements.impl.frost.listeners.FrostFrozenPunchListener(this, elementManager), this);
    }


    private void registerRecipes() {
        taskScheduler.runLaterSeconds(() -> {
            getLogger().info("Registering recipes...");
            hs.elementSMPRefined.recipes.UtilRecipes.registerRecipes(this);
            getLogger().info("Recipes registered");
        }, 1);
    }

    private void startBackgroundTasks() {
        manaManager.start();
    }

    private void stopBackgroundTasks() {
        if (manaManager != null) {
            manaManager.stop();
        }
    }

    private void saveAllData() {
        if (dataStore != null) {
            dataStore.flushAll();
        }
    }

    public hs.elementSMPRefined.data.DataStore getDataStore() { return dataStore; }
    public ConfigManager getConfigManager() { return configManager; }
    public ElementManager getElementManager() { return elementManager; }
    public ManaManager getManaManager() { return manaManager; }
    public TrustManager getTrustManager() { return trustManager; }
    public ItemManager getItemManager() { return itemManager; }
    public hs.elementSMPRefined.status.StatusEffectManager getStatusEffectManager() { return statusEffectManager; }
    public hs.elementSMPRefined.elements.abilities.AbilityRegistry getAbilityRegistry() { return abilityRegistry; }
    public hs.elementSMPRefined.services.EffectService getEffectService() { return effectService; }
    public hs.elementSMPRefined.services.ValidationService getValidationService() { return validationService; }
    public hs.elementSMPRefined.util.scheduling.TaskScheduler getTaskScheduler() { return taskScheduler; }
    public hs.elementSMPRefined.util.bukkit.MetadataHelper getMetadataHelper() { return metadataHelper; }

}