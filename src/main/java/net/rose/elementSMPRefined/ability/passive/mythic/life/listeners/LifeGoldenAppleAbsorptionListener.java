package net.rose.elementSMPRefined.ability.passive.mythic.life.listeners;

import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ElementManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Life passive 3: golden apples grant extra Absorption.
 * <p>
 * Vanilla gives Absorption I (2 hearts) for a golden apple and Absorption IV (8 hearts) for an enchanted one,
 * both for 2 minutes. A Life player gets the stronger amplifiers below instead. Each Absorption level is 4 HP
 * (2 hearts) per amplifier + 1.
 */
public class LifeGoldenAppleAbsorptionListener implements Listener {
    /** Absorption II = 4 hearts. */
    private static final int GOLDEN_APPLE_AMPLIFIER = 1;
    /** Absorption VI = 12 hearts. */
    private static final int ENCHANTED_GOLDEN_APPLE_AMPLIFIER = 5;
    private static final int DURATION_TICKS = 2400; // 2 minutes, same as vanilla

    private final JavaPlugin plugin;
    private final ElementManager elementManager;

    public LifeGoldenAppleAbsorptionListener(JavaPlugin plugin, ElementManager elementManager) {
        this.plugin = plugin;
        this.elementManager = elementManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Material item = event.getItem().getType();
        int amplifier;
        if (item == Material.GOLDEN_APPLE) {
            amplifier = GOLDEN_APPLE_AMPLIFIER;
        } else if (item == Material.ENCHANTED_GOLDEN_APPLE) {
            amplifier = ENCHANTED_GOLDEN_APPLE_AMPLIFIER;
        } else {
            return;
        }

        Player player = event.getPlayer();
        var data = elementManager.data(player.getUniqueId());
        if (data == null || data.getCurrentElement() != ElementType.LIFE) return;

        // Vanilla applies the apple's own effects after this event, so wait a tick and then top them up.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.isDead()) return;
            PotionEffect current = player.getPotionEffect(PotionEffectType.ABSORPTION);
            if (current != null && current.getAmplifier() >= amplifier) return;
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, DURATION_TICKS, amplifier, false, true, true));
        });
    }
}
