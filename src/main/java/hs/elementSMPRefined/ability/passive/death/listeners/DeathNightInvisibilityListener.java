package hs.elementSMPRefined.ability.passive.death.listeners;

import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.managers.ElementManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Death passive: players become invisible during the night, and lose the
 * effect again once day breaks. Time is checked against each player's own
 * world clock, so it tracks correctly across multiple worlds/dimensions.
 */
public class DeathNightInvisibilityListener implements Listener {

    private static final long NIGHT_START = 13000L; // vanilla night threshold
    private static final long NIGHT_END = 23000L;    // vanilla day threshold
    private static final long CHECK_PERIOD_TICKS = 20L; // once a second

    private final ElementSMPRefined plugin;
    private final ElementManager elementManager;
    private final Set<UUID> nightInvisible = ConcurrentHashMap.newKeySet();

    private BukkitTask monitorTask;

    public DeathNightInvisibilityListener(ElementSMPRefined plugin, ElementManager elementManager) {
        this.plugin = plugin;
        this.elementManager = elementManager;
        startMonitor();
    }

    private void startMonitor() {
        monitorTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (elementManager.getPlayerElement(player) == ElementType.DEATH) {
                    tick(player);
                }
            }
        }, 20L, CHECK_PERIOD_TICKS);
    }

    private void tick(Player player) {
        boolean night = isNight(player.getWorld());
        UUID uuid = player.getUniqueId();

        if (night && nightInvisible.add(uuid)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, true, false));
        } else if (!night && nightInvisible.remove(uuid)) {
            removeInvisibility(player);
        }
    }

    private boolean isNight(World world) {
        long time = world.getTime();
        return time >= NIGHT_START && time < NIGHT_END;
    }

    private void removeInvisibility(Player player) {
        PotionEffect current = player.getPotionEffect(PotionEffectType.INVISIBILITY);
        if (current != null && current.getDuration() == PotionEffect.INFINITE_DURATION) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        nightInvisible.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Strips the passive's invisibility immediately - call when a player leaves
     * Death or when clearing element effects.
     */
    public void clear(Player player) {
        if (nightInvisible.remove(player.getUniqueId())) {
            removeInvisibility(player);
        }
    }

    public void cleanup() {
        if (monitorTask != null) {
            monitorTask.cancel();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (nightInvisible.contains(player.getUniqueId())) {
                removeInvisibility(player);
            }
        }
        nightInvisible.clear();
    }
}