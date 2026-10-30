package net.rose.elementSMPRefined.core.initializers;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.API.element.ListenerProvider;
import net.rose.elementSMPRefined.ability.main.upgraded.metal.MetalMagnetarAbility;
import net.rose.elementSMPRefined.ability.passive.basic.air.AirElement;
import net.rose.elementSMPRefined.ability.passive.basic.air.listeners.AirAntiFallDmgListener;
import net.rose.elementSMPRefined.ability.passive.basic.air.listeners.AirWindBurstListener;
import net.rose.elementSMPRefined.ability.passive.basic.earth.listeners.EarthVeinMinerListener;
import net.rose.elementSMPRefined.ability.passive.basic.fire.listeners.FireSmeltListener;
import net.rose.elementSMPRefined.ability.passive.upgraded.metal.MetalElement;
import net.rose.elementSMPRefined.ability.passive.upgraded.metal.listeners.MetalChainStunListener;
import net.rose.elementSMPRefined.listeners.handler.ForgedUpgraderHandler;
import net.rose.elementSMPRefined.listeners.handler.ReforgeAltarListener;
import net.rose.elementSMPRefined.listeners.item.PlayerDeathListener;
import net.rose.elementSMPRefined.listeners.handler.RerollerHandler;
import net.rose.elementSMPRefined.listeners.handler.ElementAdvancementListener;
import net.rose.elementSMPRefined.listeners.handler.UpgraderHandler;
import net.rose.elementSMPRefined.listeners.player.InvisibilityNameHider;
import net.rose.elementSMPRefined.listeners.player.PlayerLifecycle;
import net.rose.elementSMPRefined.listeners.status.DisarmListener;
import net.rose.elementSMPRefined.listeners.status.StatusEffectListener;
import net.rose.elementSMPRefined.util.server.DimensionDisable;
import net.rose.elementSMPRefined.util.server.GracePeriod;
import net.rose.elementSMPRefined.util.server.MaceCooldown;
import net.rose.elementSMPRefined.util.visual.ElementSmpTablist;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles registration of all plugin listeners.
 * Centralizes listener registration logic and manages listener dependencies.
 */
public class ListenerInitializer {
    private final ElementSMPRefined plugin;
    private final PluginManager pluginManager;

    private PlayerLifecycle playerLifecycleListener;
    private AirAntiFallDmgListener airAntiFallDmgListener;
    private MetalMagnetarAbility metalMagnetarAbility;
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

        pluginManager.registerEvents(new StatusEffectListener(plugin), plugin);
        pluginManager.registerEvents(new DisarmListener(plugin.getDisarmManager()), plugin);

        pluginManager.registerEvents(new DimensionDisable(plugin.getConfigManager()), plugin);
        pluginManager.registerEvents(new MaceCooldown(plugin), plugin);

        this.gracePeriod = new GracePeriod(plugin, plugin.getConfigManager(), plugin.getTaskScheduler());
        pluginManager.registerEvents(gracePeriod, plugin);

        // Also refreshes a player's action bar immediately on AbilityActivateEvent -
        // see CooldownActionBarTask's class doc for why that's needed alongside its
        // periodic tick.
        pluginManager.registerEvents(plugin.getCooldownActionBarTask(), plugin);
        ElementSmpTablist.register(plugin);
    }

    private void registerItemListeners() {
        pluginManager.registerEvents(new PlayerDeathListener(plugin, plugin.getElementManager()), plugin);
        pluginManager.registerEvents(new RerollerHandler(plugin, plugin.getElementManager()), plugin);
        ElementAdvancementListener advancements = new ElementAdvancementListener(plugin);
        pluginManager.registerEvents(new ForgedUpgraderHandler(plugin, plugin.getElementManager(), advancements), plugin);
        pluginManager.registerEvents(new ReforgeAltarListener(plugin, plugin.getReforgeAltarManager()), plugin);
        pluginManager.registerEvents(new UpgraderHandler(plugin, plugin.getElementManager()), plugin);
        pluginManager.registerEvents(advancements, plugin);
    }

    private void registerElementListeners() {
        registerCoreElementListeners();

        plugin.getElementManager().getAllElements().forEach(element -> {
            if (element instanceof ListenerProvider provider) {
                provider.getListeners(plugin).forEach(listener -> {
                    pluginManager.registerEvents(listener, plugin);
                });
            }
        });
    }

    private void registerCoreElementListeners() {
        this.airAntiFallDmgListener = new AirAntiFallDmgListener(plugin.getElementManager());
        pluginManager.registerEvents(airAntiFallDmgListener, plugin);
        pluginManager.registerEvents(new net.rose.elementSMPRefined.listeners.combat.LifeTotemListener(), plugin);
        pluginManager.registerEvents(new AirWindBurstListener(plugin, plugin.getElementManager(), plugin.getTrustManager()), plugin);

        var airElement = plugin.getElementManager().get(ElementType.AIR);
        if (airElement instanceof AirElement airElementImpl) {
            airElementImpl.setFallImpactListener(airAntiFallDmgListener);
        }

        pluginManager.registerEvents(new EarthVeinMinerListener(plugin.getElementManager()), plugin);

        var metalElement = plugin.getElementManager().get(ElementType.METAL);
        if (metalElement instanceof MetalElement metalElementImpl) {
            this.metalMagnetarAbility = metalElementImpl.getMagnetarAbility();
        }

        pluginManager.registerEvents(new FireSmeltListener(plugin.getElementManager()), plugin);
        pluginManager.registerEvents(new MetalChainStunListener(plugin), plugin);
    }

    private void registerLifecycleListener() {
        this.playerLifecycleListener = new PlayerLifecycle(
                plugin,
                plugin.getElementManager(),
                plugin.getCooldownManager(),
                plugin.getEffectService(),
                plugin.getDisarmManager(),
                airAntiFallDmgListener,
                metalMagnetarAbility
        );
        pluginManager.registerEvents(playerLifecycleListener, plugin);
    }

    public void cleanup() {
        if (gracePeriod != null) {
            gracePeriod.cleanup();
        }
    }

    public GracePeriod getGracePeriod() {
        return gracePeriod;
    }
}