package hs.elementSMPRefined.listeners.player;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.ability.main.metal.MetalDashAbility;
import hs.elementSMPRefined.ability.passive.air.listeners.AirFallImpactListener;
import hs.elementSMPRefined.ability.passive.frost.listeners.FrostPassiveListener;
import hs.elementSMPRefined.config.Constants;
import hs.elementSMPRefined.data.PlayerData;
import hs.elementSMPRefined.gui.ElementSelectionGUI;
import hs.elementSMPRefined.listeners.GUIListener;
import hs.elementSMPRefined.listeners.ability.AbilityListener;
import hs.elementSMPRefined.managers.ElementManager;
import hs.elementSMPRefined.managers.ManaManager;
import hs.elementSMPRefined.services.EffectService;
import hs.elementSMPRefined.util.scheduling.TaskScheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;

public class PlayerLifecycleListener implements Listener {
    private final ElementSMPRefined plugin;
    private final ElementManager elementManager;
    private final ManaManager manaManager;
    private final EffectService effectService;
    private final TaskScheduler scheduler;
    private final FrostPassiveListener frostPassiveListener;
    private final AirFallImpactListener airFallImpactListener;
    private final GUIListener guiListener;
    private final AbilityListener abilityListener;
    private final MetalDashAbility metalDashAbility;

    public PlayerLifecycleListener(ElementSMPRefined plugin, ElementManager elementManager,
                                   ManaManager manaManager, EffectService effectService,
                                   FrostPassiveListener frostPassiveListener,
                                   AirFallImpactListener airFallImpactListener,
                                   GUIListener guiListener,
                                   AbilityListener abilityListener,
                                   MetalDashAbility metalDashAbility) {
        this.plugin = plugin;
        this.elementManager = elementManager;
        this.manaManager = manaManager;
        this.effectService = effectService;
        this.scheduler = new TaskScheduler(plugin);
        this.frostPassiveListener = frostPassiveListener;
        this.airFallImpactListener = airFallImpactListener;
        this.guiListener = guiListener;
        this.abilityListener = abilityListener;
        this.metalDashAbility = metalDashAbility;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData pd = elementManager.data(player.getUniqueId());
        manaManager.get(player.getUniqueId());

        if (pd.getCurrentElement() == null) {
            scheduler.runAfterPlayerLoad(() -> {
                if (player.isOnline()) {
                    new ElementSelectionGUI(plugin, player, false).open();
                }
            });
        } else {
            scheduler.runAfterPlayerLoad(() -> {
                if (player.isOnline()) {
                    effectService.clearAllElementEffects(player);
                    effectService.applyPassiveEffects(player);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        elementManager.cancelRolling(player);
        manaManager.save(playerUuid);
        effectService.clearAllElementEffects(player);
        plugin.getDataStore().save(elementManager.data(playerUuid));
        ElementSelectionGUI.removeGUI(playerUuid);
        if (frostPassiveListener != null) {
            frostPassiveListener.onPlayerQuit(playerUuid);
        }
        if (airFallImpactListener != null) {
            airFallImpactListener.cleanupPlayer(playerUuid);
        }
        if (guiListener != null) {
            guiListener.onPlayerQuit(playerUuid);
        }
        if (abilityListener != null) {
            abilityListener.onPlayerQuit(playerUuid);
        }
        if (metalDashAbility != null) {
            metalDashAbility.onPlayerQuit(playerUuid);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        scheduler.runLater(() -> {
            if (player.isOnline()) {
                effectService.applyPassiveEffects(player);
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTotemUse(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        scheduler.runLater(() -> {
            if (player.isOnline()) {
                effectService.applyPassiveEffects(player);
            }
        }, Constants.Timing.HALF_SECOND);
    }
}