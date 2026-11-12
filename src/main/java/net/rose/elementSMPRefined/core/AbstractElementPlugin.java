package net.rose.elementSMPRefined.core;

import net.rose.elementSMPRefined.altar.ReforgeAltarManager;
import net.rose.elementSMPRefined.core.initializers.CommandInitializer;
import net.rose.elementSMPRefined.core.initializers.ListenerInitializer;
import net.rose.elementSMPRefined.core.initializers.RecipeInitializer;
import net.rose.elementSMPRefined.data.DataStore;
import net.rose.elementSMPRefined.hud.CooldownActionBarTask;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.CooldownManager;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.managers.TeamManager;
import net.rose.elementSMPRefined.services.EffectService;
import net.rose.elementSMPRefined.status.DisarmManager;
import net.rose.elementSMPRefined.status.StatusEffectManager;
import net.rose.elementSMPRefined.status.StolenAbilityManager;
import net.rose.elementSMPRefined.util.bukkit.MetadataHelper;
import net.rose.elementSMPRefined.util.damage.TrueDamage;
import net.rose.elementSMPRefined.util.scheduling.TaskScheduler;
import net.rose.elementSMPRefined.util.server.potplugin.enchantlimits.EnchantLimits;
import net.rose.elementSMPRefined.util.server.potplugin.enchantlimits.EnchantLimitsCommand;
import net.rose.elementSMPRefined.util.server.potplugin.enchantlimits.EnchantLimitsListener;
import net.rose.elementSMPRefined.util.server.potplugin.listener.BrewingListener;
import net.rose.elementSMPRefined.util.server.potplugin.potlimits.PotLimitsCommand;
import net.rose.elementSMPRefined.util.server.potplugin.potlimits.PotLimitsListener;
import net.rose.elementSMPRefined.util.server.potplugin.potlimits.PotionLimits;
import net.rose.elementSMPRefined.util.server.potplugin.util.ExtensionRecipes;
import net.rose.elementevents.CustomEventVillager;
import net.rose.elementevents.api.EventManager;
import net.rose.elementevents.lifedeath.LifeDeathEvent;
import net.rose.elementevents.mythic.MythicBookProtection;
import net.rose.elementevents.mythic.MythicElements;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Abstract base class for Element plugin logic.
 *
 * Centralizes common plugin initialization and management logic,
 * including potion limits, enchantment limits, and custom potion recipes.
 */
public abstract class AbstractElementPlugin extends JavaPlugin {

    protected DataStore dataStore;
    protected ConfigManager configManager;
    protected ElementManager elementManager;
    protected CooldownManager cooldownManager;
    protected TeamManager teamManager;

    protected StatusEffectManager statusEffectManager;
    protected DisarmManager disarmManager;
    protected StolenAbilityManager stolenAbilityManager;
    protected EffectService effectService;
    protected CooldownActionBarTask cooldownActionBarTask;

    protected ReforgeAltarManager reforgeAltarManager;
    protected CustomEventVillager eventVillager;
    protected EventManager eventManager;
    protected MythicBookProtection mythicBookProtection;
    protected MythicElements mythicElements;
    protected TaskScheduler taskScheduler;
    protected MetadataHelper metadataHelper;
    protected TrueDamage trueDamage;

    protected CommandInitializer commandInitializer;
    protected ListenerInitializer listenerInitializer;
    protected RecipeInitializer recipeInitializer;

    // Potion systems
    protected PotionLimits potionLimits;
    protected PotLimitsListener potLimitsListener;

    protected EnchantLimits enchantLimits;
    protected EnchantLimitsListener enchantLimitsListener;

    @Override
    public final void onEnable() {
        try {
            saveDefaultConfig();

            initializeCore();
            initializeUtilities();
            initializeManagers();
            initializeServices();

            // Initialize potion and enchantment systems
            initializePotionSystems();

            beforeRegisterComponents();

            initializeInitializers();
            registerComponents();

            // Register potion-related listeners and commands
            registerPotionComponents();

            startBackgroundTasks();

            onPluginEnable();

            getLogger().info("ElementPlugin enabled successfully!");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable plugin", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public final void onDisable() {
        // Each step is isolated: one failure must not skip the rest (the data flush and the
        // entity / recipe sweeps all still have to run).
        runShutdownStep("cleanup", this::cleanup);
        runShutdownStep("data save", this::saveAllData);
        runShutdownStep("potion recipe unregister", ExtensionRecipes::unregister);
        runShutdownStep(
                "item recipe unregister",
                () -> {
                    if (recipeInitializer != null) {
                        recipeInitializer.unregisterRecipes();
                    }
                });
        runShutdownStep("plugin disable hook", this::onPluginDisable);

        getLogger().info("ElementPlugin disabled successfully!");
    }

    private void runShutdownStep(String name, Runnable step) {
        try {
            step.run();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during plugin shutdown (" + name + ")", e);
        }
    }

    /**
     * Template method for subclasses to perform required setup
     * before listeners are registered.
     */
    protected void beforeRegisterComponents() {}

    /**
     * Template method for subclasses to perform additional enable logic.
     */
    protected void onPluginEnable() {}

    /**
     * Template method for subclasses to perform additional disable logic.
     */
    protected void onPluginDisable() {}

    private void initializeCore() {
        this.configManager = new ConfigManager(this);
        this.configManager.mergeNewDefaults();

        this.dataStore = new DataStore(this);
    }

    private void initializeUtilities() {
        this.taskScheduler = new TaskScheduler(this);
        this.metadataHelper = new MetadataHelper(this);
        this.trueDamage = new TrueDamage(this);
    }

    private void initializeManagers() {
        this.teamManager = new TeamManager(this, configManager);
        this.cooldownManager = new CooldownManager();

        this.elementManager =
                new ElementManager(this, dataStore, cooldownManager, teamManager, configManager);

        this.statusEffectManager = new StatusEffectManager(this);
        this.disarmManager = new DisarmManager(this);
        this.stolenAbilityManager = new StolenAbilityManager();
        this.reforgeAltarManager = new ReforgeAltarManager(this);
        this.eventVillager =
                new CustomEventVillager((net.rose.elementSMPRefined.ElementSMPRefined) this);
        this.eventManager = new EventManager((net.rose.elementSMPRefined.ElementSMPRefined) this);
        this.eventManager.register(
                new LifeDeathEvent((net.rose.elementSMPRefined.ElementSMPRefined) this));
        this.mythicElements =
                new MythicElements((net.rose.elementSMPRefined.ElementSMPRefined) this);
        this.mythicBookProtection =
                new MythicBookProtection((net.rose.elementSMPRefined.ElementSMPRefined) this);
    }

    private void initializeServices() {
        this.effectService = new EffectService(this, elementManager);

        this.cooldownActionBarTask =
                new CooldownActionBarTask(this, elementManager, cooldownManager);
    }

    /**
     * Initializes potion limits, enchantment limits,
     * and custom potion recipes.
     */
    private void initializePotionSystems() {

        // Register custom potion recipes
        ExtensionRecipes.register(this);

        // Initialize potion limits
        potionLimits = new PotionLimits(this);
        potionLimits.load();

        // Initialize enchantment limits
        enchantLimits = new EnchantLimits(this);
        enchantLimits.load();
    }

    /**
     * Registers potion listeners and commands.
     */
    private void registerPotionComponents() {

        // Brewing listener
        getServer().getPluginManager().registerEvents(new BrewingListener(), this);

        // Potion limits listener
        potLimitsListener = new PotLimitsListener(this, potionLimits);

        getServer().getPluginManager().registerEvents(potLimitsListener, this);

        potLimitsListener.startSweepTask();

        // Potion limits command
        PluginCommand potCommand = getCommand("potlimits");

        if (potCommand != null) {
            potCommand.setExecutor(new PotLimitsCommand(potionLimits, potLimitsListener));
        } else {
            getLogger().warning("Command 'potlimits' is missing from plugin.yml!");
        }

        // Enchantment limits listener
        enchantLimitsListener = new EnchantLimitsListener(this, enchantLimits);

        getServer().getPluginManager().registerEvents(enchantLimitsListener, this);

        enchantLimitsListener.startSweepTask();

        // Enchantment limits command
        PluginCommand enchantCommand = getCommand("enchantlimits");

        if (enchantCommand != null) {
            enchantCommand.setExecutor(
                    new EnchantLimitsCommand(enchantLimits, enchantLimitsListener));
        } else {
            getLogger().warning("Command 'enchantlimits' is missing from plugin.yml!");
        }
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
        reforgeAltarManager.start();
        mythicBookProtection.start();
    }

    private void cleanup() {

        if (eventManager != null) {
            eventManager.cleanup();
        }

        if (mythicBookProtection != null) {
            mythicBookProtection.stop();
        }

        if (reforgeAltarManager != null) {
            reforgeAltarManager.stop();
        }

        if (statusEffectManager != null) {
            statusEffectManager.cleanup();
        }

        if (disarmManager != null) {
            disarmManager.cleanup();
        }

        if (stolenAbilityManager != null) {
            stolenAbilityManager.cleanup();
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

    // Getters

    public DataStore getDataStore() {
        return dataStore;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ElementManager getElementManager() {
        return elementManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public StatusEffectManager getStatusEffectManager() {
        return statusEffectManager;
    }

    public DisarmManager getDisarmManager() {
        return disarmManager;
    }

    public StolenAbilityManager getStolenAbilityManager() {
        return stolenAbilityManager;
    }

    public EffectService getEffectService() {
        return effectService;
    }

    public CooldownActionBarTask getCooldownActionBarTask() {
        return cooldownActionBarTask;
    }

    public ReforgeAltarManager getReforgeAltarManager() {
        return reforgeAltarManager;
    }

    public CustomEventVillager getEventVillager() {
        return eventVillager;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public MythicElements getMythicElements() {
        return mythicElements;
    }

    public MythicBookProtection getMythicBookProtection() {
        return mythicBookProtection;
    }

    public TaskScheduler getTaskScheduler() {
        return taskScheduler;
    }

    public MetadataHelper getMetadataHelper() {
        return metadataHelper;
    }

    public ListenerInitializer getListenerInitializer() {
        return listenerInitializer;
    }

    public TrueDamage getTrueDamage() {
        return trueDamage;
    }

    // Potion getters

    public PotionLimits getPotionLimits() {
        return potionLimits;
    }

    public PotLimitsListener getPotLimitsListener() {
        return potLimitsListener;
    }

    public EnchantLimits getEnchantLimits() {
        return enchantLimits;
    }

    public EnchantLimitsListener getEnchantLimitsListener() {
        return enchantLimitsListener;
    }
}
