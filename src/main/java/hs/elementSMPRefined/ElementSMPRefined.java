package hs.elementSMPRefined;

import hs.elementPlugin.commands.*;
import hs.elementPlugin.data.DataStore;
import hs.elementPlugin.elements.abilities.AbilityRegistry;
import hs.elementPlugin.listeners.GUIListener;
import hs.elementPlugin.listeners.combat.CombatListener;
import hs.elementPlugin.listeners.item.*;
import hs.elementPlugin.listeners.player.*;
import hs.elementPlugin.managers.*;
import hs.elementPlugin.services.EffectService;
import hs.elementPlugin.services.ValidationService;
import hs.elementPlugin.util.bukkit.MetadataHelper;
import hs.elementPlugin.util.scheduling.TaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class ElementSMPRefined extends JavaPlugin {
    private DataStore dataStore;
    private ConfigManager configManager;
    private ElementManager elementManager;
    private ManaManager manaManager;
    private TrustManager trustManager;
    private ItemManager itemManager;
    private AbilityRegistry abilityRegistry;
    private EffectService effectService;
    private ValidationService validationService;
    private TaskScheduler taskScheduler;
    private MetadataHelper metadataHelper;

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
            saveAllData();
            getLogger().info("ElementPlugin disabled successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during plugin shutdown", e);
        }
    }

    private void initializeCore() {
        getLogger().info("Initializing core components...");
        this.configManager = new ConfigManager(this);
        this.dataStore = new DataStore(this);
        getLogger().info("Core components initialized");
    }

    private void initializeManagers() {
        getLogger().info("Initializing managers...");

        this.trustManager = new TrustManager(this);
        this.manaManager = new ManaManager(this, dataStore, configManager);
        this.elementManager = new ElementManager(this, dataStore, manaManager, trustManager, configManager);
        this.itemManager = new ItemManager(this, manaManager, configManager);

        getLogger().info("Managers initialized");
    }

    private void initializeServices() {
        getLogger().info("Initializing services...");

        this.effectService = new EffectService(this, elementManager);
        this.validationService = new ValidationService(trustManager);
        this.abilityRegistry = new AbilityRegistry(this);

        getLogger().info("Services initialized");
    }

    private void initializeUtilities() {
        getLogger().info("Initializing utilities...");

        this.taskScheduler = new TaskScheduler(this);
        this.metadataHelper = new MetadataHelper(this);

        getLogger().info("Utilities initialized");
    }

    private void registerComponents() {
        registerCommands();
        registerListeners();
        registerRecipes();
    }

    private void registerCommands() {
        getLogger().info("Registering commands...");

        CommandRegistrar.register(this)
                .command("elements", new ElementInfoCommand(this))
                .command("trust", new TrustCommand(this, trustManager))
                .command("element", new ElementCommand(this))
                .command("mana", new ManaCommand(manaManager, configManager))
                .command("util", new UtilCommand(this))
                .command("togglerecipe", new ToggleRecipeCommand(this));

        getLogger().info("Commands registered");
    }

    private static class CommandRegistrar {
        private final ElementSMPRefined plugin;

        private CommandRegistrar(ElementSMPRefined plugin) {
            this.plugin = plugin;
        }

        static CommandRegistrar register(ElementSMPRefined plugin) {
            return new CommandRegistrar(plugin);
        }

        CommandRegistrar command(String name, org.bukkit.command.CommandExecutor executor) {
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

        pm.registerEvents(new PlayerLifecycleListener(this, elementManager, manaManager, effectService), this);
        pm.registerEvents(effectService, this);
        pm.registerEvents(new GameModeListener(manaManager, configManager), this);
        pm.registerEvents(new CombatListener(trustManager, elementManager), this);
        pm.registerEvents(new hs.elementPlugin.listeners.ability.AbilityListener(this, elementManager), this);
        registerItemListeners(pm);
        pm.registerEvents(new GUIListener(this), this);
        registerElementListeners(pm);

        getLogger().info("Listeners registered");
    }

    private void registerItemListeners(PluginManager pm) {
        pm.registerEvents(new hs.elementPlugin.listeners.item.ElementItemUseListener(this, elementManager, itemManager), this);
        pm.registerEvents(new hs.elementPlugin.listeners.item.ElementItemCraftListener(this, elementManager), this);
        pm.registerEvents(new hs.elementPlugin.listeners.item.ElementItemDeathListener(this, elementManager), this);
        pm.registerEvents(new hs.elementPlugin.listeners.item.ElementItemDropListener(this), this);
        pm.registerEvents(new hs.elementPlugin.listeners.item.ElementItemPickupListener(this, elementManager), this);
        pm.registerEvents(new hs.elementPlugin.listeners.item.ElementInventoryProtectionListener(this, elementManager), this);
        pm.registerEvents(new hs.elementPlugin.listeners.item.ElementCombatProjectileListener(itemManager), this);
        pm.registerEvents(new hs.elementPlugin.listeners.item.RerollerListener(this), this);
        pm.registerEvents(new hs.elementPlugin.listeners.item.AdvancedRerollerListener(this), this);
        pm.registerEvents(new hs.elementPlugin.listeners.item.UpgraderListener(this, elementManager), this);
    }

    private void registerElementListeners(PluginManager pm) {
        pm.registerEvents(new hs.elementPlugin.elements.impl.air.listeners.FallDamageListener(elementManager), this);
        pm.registerEvents(new hs.elementPlugin.elements.impl.air.listeners.AirCombatListener(elementManager), this);
        pm.registerEvents(new hs.elementPlugin.elements.impl.water.listeners.WaterDrowningImmunityListener(elementManager), this);
        pm.registerEvents(new hs.elementPlugin.elements.impl.fire.listeners.FireImmunityListener(elementManager), this);
        pm.registerEvents(new hs.elementPlugin.elements.impl.fire.listeners.FireCombatListener(elementManager, trustManager), this);
        pm.registerEvents(new hs.elementPlugin.elements.impl.fire.listeners.FireballProtectionListener(), this);
        pm.registerEvents(new hs.elementPlugin.elements.impl.earth.listeners.EarthCharmListener(elementManager, this), this);
        pm.registerEvents(new hs.elementPlugin.elements.impl.earth.listeners.EarthFriendlyMobListener(this, trustManager), this);
        pm.registerEvents(new hs.elementPlugin.elements.impl.earth.listeners.EarthOreDropListener(elementManager), this);
        pm.registerEvents(new hs.elementPlugin.elements.impl.life.listeners.LifeRegenListener(elementManager), this);
        pm.registerEvents(new hs.elementPlugin.elements.impl.life.LifeElementCraftListener(this, elementManager), this);
        pm.registerEvents(new hs.elementPlugin.elements.impl.death.listeners.DeathRawFoodListener(elementManager), this);
        pm.registerEvents(new hs.elementPlugin.elements.impl.death.listeners.DeathFriendlyMobListener(this, trustManager), this);
        pm.registerEvents(new hs.elementPlugin.elements.impl.death.DeathElementCraftListener(this, elementManager), this);
        pm.registerEvents(new hs.elementPlugin.elements.impl.metal.listeners.MetalArrowImmunityListener(elementManager), this);
        pm.registerEvents(new hs.elementPlugin.elements.impl.metal.listeners.MetalChainStunListener(), this);
        pm.registerEvents(new hs.elementPlugin.elements.impl.frost.listeners.FrostPassiveListener(this, elementManager), this);
        pm.registerEvents(new hs.elementPlugin.elements.impl.frost.listeners.FrostFrozenPunchListener(this, elementManager), this);
    }


    private void registerRecipes() {
        taskScheduler.runLaterSeconds(() -> {
            getLogger().info("Registering recipes...");
            hs.elementPlugin.recipes.UtilRecipes.registerRecipes(this);
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

    public DataStore getDataStore() { return dataStore; }
    public ConfigManager getConfigManager() { return configManager; }
    public ElementManager getElementManager() { return elementManager; }
    public ManaManager getManaManager() { return manaManager; }
    public TrustManager getTrustManager() { return trustManager; }
    public ItemManager getItemManager() { return itemManager; }
    public AbilityRegistry getAbilityRegistry() { return abilityRegistry; }
    public EffectService getEffectService() { return effectService; }
    public ValidationService getValidationService() { return validationService; }
    public TaskScheduler getTaskScheduler() { return taskScheduler; }
    public MetadataHelper getMetadataHelper() { return metadataHelper; }

}