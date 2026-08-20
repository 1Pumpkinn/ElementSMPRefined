package hs.elementSMPRefined.core;

import hs.elementSMPRefined.data.DataStore;
import hs.elementSMPRefined.addons.AddonManager;
import hs.elementSMPRefined.initializers.CommandInitializer;
import hs.elementSMPRefined.initializers.ListenerInitializer;
import hs.elementSMPRefined.initializers.RecipeInitializer;
import hs.elementSMPRefined.managers.*;
import hs.elementSMPRefined.services.EffectService;
import hs.elementSMPRefined.services.ValidationService;
import hs.elementSMPRefined.status.StatusEffectManager;
import hs.elementSMPRefined.util.bukkit.MetadataHelper;
import hs.elementSMPRefined.util.scheduling.TaskScheduler;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Abstract base class for Element plugin logic.
 * Inspired by the Bending project's AbstractBending class (AGPL-3.0 licensed).
 * Centralizes common plugin initialization and management logic.
 */
public abstract class AbstractElementPlugin extends JavaPlugin {
    
    // Core managers
    protected DataStore dataStore;
    protected ConfigManager configManager;
    protected ElementManager elementManager;
    protected ManaManager manaManager;
    protected TrustManager trustManager;
    protected ItemManager itemManager;
    protected AddonManager addonManager;
    
    // Services
    protected StatusEffectManager statusEffectManager;
    protected EffectService effectService;
    protected ValidationService validationService;
    
    // Utilities
    protected TaskScheduler taskScheduler;
    protected MetadataHelper metadataHelper;

    // Initializers
    protected CommandInitializer commandInitializer;
    protected ListenerInitializer listenerInitializer;
    protected RecipeInitializer recipeInitializer;

    @Override
    public final void onEnable() {
        try {
            saveDefaultConfig();
            initializeCore();
            initializeUtilities();
            initializeManagers();
            initializeServices();
            beforeRegisterComponents();
            initializeInitializers();
            registerComponents();
            startBackgroundTasks();
            onPluginEnable();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable plugin", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public final void onDisable() {
        try {
            stopBackgroundTasks();
            cleanup();
            saveAllData();
            onPluginDisable();
            getLogger().info("ElementPlugin disabled successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during plugin shutdown", e);
        }
    }

    /**
     * Template method for subclasses to perform required setup before listeners are registered.
     */
    protected void beforeRegisterComponents() {
        // Override in subclasses if needed
    }

    /**
     * Template method for subclasses to perform additional enable logic
     */
    protected void onPluginEnable() {
        // Override in subclasses if needed
    }

    /**
     * Template method for subclasses to perform additional disable logic
     */
    protected void onPluginDisable() {
        // Override in subclasses if needed
    }

    private void initializeCore() {
        this.configManager = new ConfigManager(this);
        this.dataStore = new DataStore(this);
    }

    private void initializeUtilities() {
        this.taskScheduler = new TaskScheduler(this);
        this.metadataHelper = new MetadataHelper(this);
    }

    private void initializeManagers() {
        this.trustManager = new TrustManager(this, dataStore);
        this.manaManager = new ManaManager(this, dataStore, configManager);
        this.elementManager = new ElementManager(this, dataStore, manaManager, trustManager, configManager);
        this.itemManager = new ItemManager(this, manaManager, configManager);
        this.statusEffectManager = new StatusEffectManager(this);
        this.addonManager = new AddonManager((hs.elementSMPRefined.ElementSMPRefined) this);
    }

    private void initializeServices() {
        this.effectService = new EffectService(this, elementManager);
        this.validationService = new ValidationService(trustManager);
    }

    private void initializeInitializers() {
        this.commandInitializer = new CommandInitializer(this);
        this.listenerInitializer = new ListenerInitializer(this);
        this.recipeInitializer = new RecipeInitializer(this);
    }

    private void registerComponents() {
        commandInitializer.registerCommands();
        listenerInitializer.registerListeners();
        recipeInitializer.registerRecipes();
    }

    private void startBackgroundTasks() {
        manaManager.start();
    }

    private void stopBackgroundTasks() {
        if (manaManager != null) {
            manaManager.stop();
        }
    }

    private void cleanup() {
        if (statusEffectManager != null) {
            statusEffectManager.cleanup();
        }
        if (listenerInitializer != null) {
            listenerInitializer.cleanup();
        }
    }

    private void saveAllData() {
        if (dataStore != null) {
            dataStore.flushAll();
        }
    }

    // Getters for managers and services
    public DataStore getDataStore() { return dataStore; }
    public ConfigManager getConfigManager() { return configManager; }
    public ElementManager getElementManager() { return elementManager; }
    public ManaManager getManaManager() { return manaManager; }
    public TrustManager getTrustManager() { return trustManager; }
    public ItemManager getItemManager() { return itemManager; }
    public AddonManager getAddonManager() { return addonManager; }
    public StatusEffectManager getStatusEffectManager() { return statusEffectManager; }
    public EffectService getEffectService() { return effectService; }
    public ValidationService getValidationService() { return validationService; }
    public TaskScheduler getTaskScheduler() { return taskScheduler; }
    public MetadataHelper getMetadataHelper() { return metadataHelper; }
    
    public CommandInitializer getCommandInitializer() { return commandInitializer; }
    public ListenerInitializer getListenerInitializer() { return listenerInitializer; }
    public RecipeInitializer getRecipeInitializer() { return recipeInitializer; }
}