package net.rose.elementSMPRefined.listeners.player;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.ability.main.metal.MetalShardAbility;
import net.rose.elementSMPRefined.ability.passive.air.listeners.AirFallImpactListener;
import net.rose.elementSMPRefined.ability.passive.frost.listeners.FrostPassiveListener;
import net.rose.elementSMPRefined.config.Constants;
import net.rose.elementSMPRefined.data.PlayerData;
import net.rose.elementSMPRefined.items.recipes.AdvancedRerollerItem;
import net.rose.elementSMPRefined.items.recipes.RerollerItem;
import net.rose.elementSMPRefined.listeners.GUIListener;
import net.rose.elementSMPRefined.listeners.ability.AbilityListener;
import net.rose.elementSMPRefined.managers.CooldownManager;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.services.EffectService;
import net.rose.elementSMPRefined.status.DisarmManager;
import net.rose.elementSMPRefined.util.scheduling.TaskScheduler;
import net.rose.elementSMPRefined.util.visual.ElementColours;
import net.rose.elementSMPRefined.util.visual.SoundUtils;
import net.rose.elementSMPRefined.lang.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.Random;
import java.util.UUID;

public class PlayerLifecycle implements Listener {
    private final ElementSMPRefined plugin;
    private final ElementManager elementManager;
    private final EffectService effectService;
    private final DisarmManager disarmManager;
    private final TaskScheduler scheduler;
    private final FrostPassiveListener frostPassiveListener;
    private final AirFallImpactListener airFallImpactListener;
    private final GUIListener guiListener;
    private final AbilityListener abilityListener;
    private final MetalShardAbility metalShardAbility;
    private final Random random = new Random();

    public PlayerLifecycle(ElementSMPRefined plugin, ElementManager elementManager,
                           CooldownManager cooldownManager, EffectService effectService,
                           DisarmManager disarmManager,
                           FrostPassiveListener frostPassiveListener,
                           AirFallImpactListener airFallImpactListener,
                           GUIListener guiListener,
                           AbilityListener abilityListener,
                           MetalShardAbility metalDashAbility) {
        this.plugin = plugin;
        this.elementManager = elementManager;
        this.effectService = effectService;
        this.disarmManager = disarmManager;
        this.scheduler = plugin.getTaskScheduler();
        this.frostPassiveListener = frostPassiveListener;
        this.airFallImpactListener = airFallImpactListener;
        this.guiListener = guiListener;
        this.abilityListener = abilityListener;
        this.metalShardAbility = metalDashAbility;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData pd = elementManager.data(player.getUniqueId());
        if (disarmManager != null) {
            scheduler.runAfterPlayerLoad(() -> {
                if (player.isOnline()) {
                    disarmManager.reapplyCooldowns(player);
                }
            });
        }

        if (pd.getCurrentElement() == null) {
            // First join: skip ElementSelectionGUI entirely and roll the
            // player's starter element the same way the basic reroller
            // item does (title animation, then a random basic element).
            scheduler.runAfterPlayerLoad(() -> {
                if (player.isOnline()) {
                    rollStarterElement(player);
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

    /**
     * Runs the same "Rolling..." title animation as {@code RerollerHandler}
     * and assigns a random basic element once it finishes. Used for a
     * player's very first element assignment instead of opening
     * {@code ElementSelectionGUI}, so first join and a basic reroll feel
     * identical.
     */
    private void rollStarterElement(Player player) {
        if (!elementManager.beginRolling(player)) return;

        ElementType[] basicElements = elementManager.getBasicElements();
        ElementType targetElement = basicElements[random.nextInt(basicElements.length)];

        SoundUtils.playTo(player, SoundUtils.UI.ROLL);

        String[] names = java.util.Arrays.stream(basicElements)
                .map(Enum::name)
                .toArray(String[]::new);
        NamedTextColor[] colors = java.util.Arrays.stream(basicElements)
                .map(type -> {
                    var element = elementManager.get(type);
                    return element != null ? ElementColours.fromLegacy(element.getDisplayName()) : NamedTextColor.AQUA;
                })
                .toArray(NamedTextColor[]::new);

        final int steps = Constants.Animation.ROLL_STEPS;
        final long interval = Constants.Animation.ROLL_DELAY_TICKS;

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    // Player disconnected mid-roll - nothing was consumed
                    // (this isn't an item use), so just stop; they'll roll
                    // again next join since getCurrentElement() is still null.
                    cancel();
                    elementManager.endRolling(player);
                    return;
                }

                if (!elementManager.isCurrentlyRolling(player)) {
                    cancel();
                    elementManager.endRolling(player);
                    return;
                }

                if (tick >= steps) {
                    cancel();
                    try {
                        elementManager.assignBasicElement(player, targetElement);
                        player.sendMessage(Component.text("Your element has been rolled!").color(NamedTextColor.GREEN));
                    } finally {
                        elementManager.endRolling(player);
                    }
                    return;
                }

                String name = names[tick % names.length];
                NamedTextColor color = colors[tick % colors.length];
                player.showTitle(Title.title(
                        Component.text("Rolling...").color(NamedTextColor.GOLD),
                        Component.text(name).color(color),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ZERO)
                ));
                tick++;
            }
        }.runTaskTimer(plugin, 0L, interval);
    }

    private void refundPendingRerollers(Player player, PlayerData pd) {
        int basicCount = pd.consumePendingRerollerRefunds();
        int advancedCount = pd.consumePendingAdvancedRerollerRefunds();

        if (basicCount <= 0 && advancedCount <= 0) return;

        if (basicCount > 0) {
            giveItemStack(player, RerollerItem.make(plugin), basicCount);
            player.sendMessage(Lang.lifecycleYourElementReroller((basicCount > 1 ? "s were" : " was")));
        }
        if (advancedCount > 0) {
            giveItemStack(player, AdvancedRerollerItem.make(plugin), advancedCount);
            player.sendMessage(Lang.lifecycleYourAdvancedReroller((advancedCount > 1 ? "s were" : " was")));
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
        effectService.clearAllElementEffects(player);
        plugin.getDataStore().save(elementManager.data(playerUuid));
        // Drop the cached PlayerData now that it's safely on disk - without
        // this, DataStore's cache grows for every unique player who has
        // ever joined and never shrinks for the life of the server.
        plugin.getDataStore().invalidateCache(playerUuid);
        // Same cache-growth issue as DataStore above, applied to the trusted-set cache.
        plugin.getTrustManager().onPlayerQuit(playerUuid);
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
        if (metalShardAbility != null) {
            metalShardAbility.onPlayerQuit(playerUuid);
        }
    }

    /**
     * Without this, an ability's active-state flag (bubble shields, healing beams,
     * grasp holds, the water geyser's fall immunity, etc.) only clears itself once
     * that ability's own internal timeout elapses - it never checks whether the
     * player died, only whether they went offline. Death doesn't end an ability
     * early anywhere in this codebase without this hook, so a shield, beam, or
     * carry could keep ticking against/around a corpse for the ability's full
     * duration before self-expiring. Clearing on death this way brings dying in
     * line with quitting and switching elements, which already snap every
     * ability's active flag immediately instead of waiting it out.
     * <p>
     * Uses the single-element clear (not {@link EffectService#clearAllElementEffects})
     * since only the player's current element could have anything active, and deaths
     * are frequent enough (PvP) that the full every-element sweep isn't worth it here.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerData pd = elementManager.data(player.getUniqueId());
        effectService.clearElementEffects(player, pd.getCurrentElementId());
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