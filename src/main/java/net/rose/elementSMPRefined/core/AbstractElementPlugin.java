package net.rose.elementSMPRefined.core;

import net.rose.elementSMPRefined.data.DataStore;
import net.rose.elementSMPRefined.core.initializers.CommandInitializer;
import net.rose.elementSMPRefined.core.initializers.ListenerInitializer;
import net.rose.elementSMPRefined.core.initializers.RecipeInitializer;
import net.rose.elementSMPRefined.hud.CooldownActionBarTask;
import net.rose.elementSMPRefined.managers.*;
import net.rose.elementSMPRefined.services.EffectService;
import net.rose.elementSMPRefined.services.ValidationService;
import net.rose.elementSMPRefined.status.DisarmManager;
import net.rose.elementSMPRefined.status.StatusEffectManager;
import net.rose.elementSMPRefined.util.bukkit.MetadataHelper;
import net.rose.elementSMPRefined.util.scheduling.TaskScheduler;
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
    protected CooldownManager cooldownManager;
    protected TrustManager trustManager;
    protected ItemManager itemManager;

    // Services
    protected StatusEffectManager statusEffectManager;
    protected DisarmManager disarmManager;
    protected EffectService effectService;
    protected ValidationService validationService;
    protected CooldownActionBarTask cooldownActionBarTask;

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
        this.cooldownManager = new CooldownManager();
        this.elementManager = new ElementManager(this, dataStore, cooldownManager, trustManager, configManager);
        this.itemManager = new ItemManager(this, configManager);
        this.statusEffectManager = new StatusEffectManager(this);
        this.disarmManager = new DisarmManager(this);
    }

    private void initializeServices() {
        this.effectService = new EffectService(this, elementManager);
        this.validationService = new ValidationService(trustManager);
        this.cooldownActionBarTask = new CooldownActionBarTask(this, elementManager, cooldownManager);
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
        cooldownActionBarTask.start(taskScheduler);
    }



    private void cleanup() {
        if (statusEffectManager != null) {
            statusEffectManager.cleanup();
        }
        if (disarmManager != null) {
            disarmManager.cleanup();
        }
        if (listenerInitializer != null) {
            listenerInitializer.cleanup();
        }
        if (cooldownActionBarTask != null) {
            cooldownActionBarTask.stop();
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
    public CooldownManager getCooldownManager() { return cooldownManager; }
    public TrustManager getTrustManager() { return trustManager; }
    public ItemManager getItemManager() { return itemManager; }
    public StatusEffectManager getStatusEffectManager() { return statusEffectManager; }
    public DisarmManager getDisarmManager() { return disarmManager; }
    public EffectService getEffectService() { return effectService; }
    public ValidationService getValidationService() { return validationService; }
    public CooldownActionBarTask getCooldownActionBarTask() { return cooldownActionBarTask; }
    public TaskScheduler getTaskScheduler() { return taskScheduler; }
    public MetadataHelper getMetadataHelper() { return metadataHelper; }

    public CommandInitializer getCommandInitializer() { return commandInitializer; }
    public ListenerInitializer getListenerInitializer() { return listenerInitializer; }
    public RecipeInitializer getRecipeInitializer() { return recipeInitializer; }
}