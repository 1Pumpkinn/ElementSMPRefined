package hs.elementSMPRefined.listeners.player;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.ability.main.metal.MetalDashAbility;
import hs.elementSMPRefined.ability.passive.air.listeners.AirFallImpactListener;
import hs.elementSMPRefined.ability.passive.frost.listeners.FrostPassiveListener;
import hs.elementSMPRefined.config.Constants;
import hs.elementSMPRefined.data.PlayerData;
import hs.elementSMPRefined.gui.ElementSelectionGUI;
import hs.elementSMPRefined.items.recipes.AdvancedRerollerItem;
import hs.elementSMPRefined.items.recipes.RerollerItem;
import hs.elementSMPRefined.listeners.GUIListener;
import hs.elementSMPRefined.listeners.ability.AbilityListener;
import hs.elementSMPRefined.managers.ElementManager;
import hs.elementSMPRefined.managers.ManaManager;
import hs.elementSMPRefined.services.EffectService;
import hs.elementSMPRefined.status.DisarmManager;
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

public class PlayerLifecycle implements Listener {
    private final ElementSMPRefined plugin;
    private final ElementManager elementManager;
    private final ManaManager manaManager;
    private final EffectService effectService;
    private final DisarmManager disarmManager;
    private final TaskScheduler scheduler;
    private final FrostPassiveListener frostPassiveListener;
    private final AirFallImpactListener airFallImpactListener;
    private final GUIListener guiListener;
    private final AbilityListener abilityListener;
    private final MetalDashAbility metalDashAbility;

    public PlayerLifecycle(ElementSMPRefined plugin, ElementManager elementManager,
                           ManaManager manaManager, EffectService effectService,
                           DisarmManager disarmManager,
                           FrostPassiveListener frostPassiveListener,
                           AirFallImpactListener airFallImpactListener,
                           GUIListener guiListener,
                           AbilityListener abilityListener,
                           MetalDashAbility metalDashAbility) {
        this.plugin = plugin;
        this.elementManager = elementManager;
        this.manaManager = manaManager;
        this.effectService = effectService;
        this.disarmManager = disarmManager;
        this.scheduler = plugin.getTaskScheduler();
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
        if (disarmManager != null) {
            scheduler.runAfterPlayerLoad(() -> {
                if (player.isOnline()) {
                    disarmManager.reapplyCooldowns(player);
                }
            });
        }

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

        // Hand back any rerollers that were consumed but never resolved
        // because the player disconnected mid-roll (see ElementManager /
        // AdvancedRerollerHandler's queue*RerollerRefund calls).
        scheduler.runAfterPlayerLoad(() -> {
            if (player.isOnline()) {
                refundPendingRerollers(player, pd);
            }
        });
    }

    private void refundPendingRerollers(Player player, PlayerData pd) {
        int basicCount = pd.consumePendingRerollerRefunds();
        int advancedCount = pd.consumePendingAdvancedRerollerRefunds();

        if (basicCount <= 0 && advancedCount <= 0) return;

        if (basicCount > 0) {
            giveItemStack(player, RerollerItem.make(plugin), basicCount);
            player.sendMessage(org.bukkit.ChatColor.YELLOW +
                    "Your Element Reroller" + (basicCount > 1 ? "s were" : " was") +
                    " refunded since your last reroll got interrupted.");
        }
        if (advancedCount > 0) {
            giveItemStack(player, AdvancedRerollerItem.make(plugin), advancedCount);
            player.sendMessage(org.bukkit.ChatColor.YELLOW +
                    "Your Advanced Reroller" + (advancedCount > 1 ? "s were" : " was") +
                    " refunded since your last reroll got interrupted.");
        }

        plugin.getDataStore().save(pd);
    }

    /**
     * Gives the player {@code amount} copies of {@code template}, split
     * across multiple stacks if it exceeds the item's max stack size, and
     * drops anything that doesn't fit in their inventory at their feet
     * instead of silently discarding it.
     */
    private void giveItemStack(Player player, org.bukkit.inventory.ItemStack template, int amount) {
        int maxStack = template.getMaxStackSize();
        int remaining = amount;

        while (remaining > 0) {
            int batchSize = Math.min(remaining, maxStack);
            org.bukkit.inventory.ItemStack stack = template.clone();
            stack.setAmount(batchSize);

            var leftover = player.getInventory().addItem(stack);
            for (org.bukkit.inventory.ItemStack overflow : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }

            remaining -= batchSize;
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
        // Drop the cached PlayerData now that it's safely on disk - without
        // this, DataStore's cache grows for every unique player who has
        // ever joined and never shrinks for the life of the server.
        plugin.getDataStore().invalidateCache(playerUuid);
        // Same cache-growth issue as DataStore above, applied to the trusted-set cache.
        plugin.getTrustManager().onPlayerQuit(playerUuid);
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