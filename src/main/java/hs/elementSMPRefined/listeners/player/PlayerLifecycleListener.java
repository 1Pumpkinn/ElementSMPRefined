package hs.elementSMPRefined.listeners.player;

import hs.elementPlugin.ElementPlugin;
import hs.elementPlugin.config.Constants;
import hs.elementPlugin.data.PlayerData;
import hs.elementPlugin.gui.ElementSelectionGUI;
import hs.elementPlugin.managers.ElementManager;
import hs.elementPlugin.managers.ManaManager;
import hs.elementPlugin.services.EffectService;
import hs.elementPlugin.util.scheduling.TaskScheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerLifecycleListener implements Listener {
    private final ElementPlugin plugin;
    private final ElementManager elementManager;
    private final ManaManager manaManager;
    private final EffectService effectService;
    private final TaskScheduler scheduler;

    public PlayerLifecycleListener(ElementPlugin plugin, ElementManager elementManager,
                                   ManaManager manaManager, EffectService effectService) {
        this.plugin = plugin;
        this.elementManager = elementManager;
        this.manaManager = manaManager;
        this.effectService = effectService;
        this.scheduler = new TaskScheduler(plugin);
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
        elementManager.cancelRolling(player);
        manaManager.save(player.getUniqueId());
        effectService.clearAllElementEffects(player);
        plugin.getDataStore().save(elementManager.data(player.getUniqueId()));
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