package hs.elementSMPRefined.services;

import hs.elementSMPRefined.config.Constants;
import hs.elementSMPRefined.data.PlayerData;
import hs.elementSMPRefined.API.element.Element;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.managers.ElementManager;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Coordinates element passive effect application and health management.
 * 
 * This service does NOT store hardcoded effect lists - each element owns
 * its effects in {@link Element#applyUpsides(Player, int)}.
 * EffectService simply ensures effects are re-applied when needed (after
 * milk, respawn, etc.) and manages health attributes.
 */
public class EffectService implements Listener {
    private final JavaPlugin plugin;
    private final ElementManager elementManager;

    public EffectService(JavaPlugin plugin, ElementManager elementManager) {
        this.plugin = plugin;
        this.elementManager = elementManager;
        startMonitoring();
    }

    /**
     * Clear ALL element effects from a player.
     * Used when switching elements or logging out.
     */
    public void clearAllElementEffects(Player player) {
        PlayerData pd = elementManager.data(player.getUniqueId());
        ElementType currentElement = pd.getCurrentElement();

        // Clear effects from ALL elements (including current one when switching)
        for (ElementType type : ElementType.values()) {
            Element element = elementManager.get(type);
            if (element != null) {
                element.clearEffects(player);
            }
        }

        // Reset health if not Life element
        updatePlayerHealth(player, currentElement);
    }

    /**
     * Remove a potion effect only if it was applied by the element system.
     * Element effects use infinite duration values, while player potions have
     * shorter durations. This preserves legitimate potion effects from
     * drinking/splashing potions.
     */
    public static void removeElementPotionEffect(Player player, PotionEffectType type) {
        PotionEffect effect = player.getPotionEffect(type);
        if (effect != null && isElementPotionEffect(effect)) {
            player.removePotionEffect(type);
        }
    }

    private static boolean isElementPotionEffect(PotionEffect effect) {
        return effect.getDuration() > 1000000
                || effect.getDuration() == PotionEffect.INFINITE_DURATION;
    }

    /**
     * Apply passive effects for player's current element.
     * Delegates to the element to define its own effects.
     */
    public void applyPassiveEffects(Player player) {
        PlayerData pd = elementManager.data(player.getUniqueId());
        ElementType type = pd.getCurrentElement();

        if (type == null) return;

        Element element = elementManager.get(type);
        if (element != null) {
            element.applyUpsides(player, pd.getUpgradeLevel(type));
        }
    }

    /**
     * Validate and restore effects if they were lost.
     * Called after events that remove potion effects (milk, etc.).
     */
    public void reapplyEffects(Player player) {
        if (player.isOnline()) {
            applyPassiveEffects(player);
            updatePlayerHealth(player, elementManager.data(player.getUniqueId()).getCurrentElement());
        }
    }

    /**
     * Ensure player's max health matches their current element.
     * Life element grants bonus health; all others use normal health.
     */
    private void updatePlayerHealth(Player player, ElementType elementType) {
        var attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;

        double targetHealth = elementType == ElementType.LIFE ?
                Constants.Health.LIFE_MAX : Constants.Health.NORMAL_MAX;

        if (attr.getBaseValue() != targetHealth) {
            attr.setBaseValue(targetHealth);
            if (!player.isDead() && player.getHealth() > targetHealth) {
                player.setHealth(targetHealth);
            }
        }
    }

    /**
     * Start periodic health validation (effects are re-applied on-demand).
     */
    private void startMonitoring() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PlayerData pd = elementManager.data(player.getUniqueId());
                    if (pd.getCurrentElement() != null) {
                        updatePlayerHealth(player, pd.getCurrentElement());
                    }
                }
            }
        }.runTaskTimer(plugin, Constants.Timing.TWO_SECONDS, Constants.Timing.TWO_SECONDS);
    }

    /**
     * When milk is consumed, all potion effects are cleared. Reapply element effects.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMilkConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != org.bukkit.Material.MILK_BUCKET) return;

        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> reapplyEffects(player), 1L);
    }
}