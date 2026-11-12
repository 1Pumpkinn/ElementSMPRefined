package net.rose.elementevents.lifedeath;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementevents.mythic.MythicBook.Kind;

import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Counts kills for the Life &amp; Death event and delivers books won while offline. (Showing the
 * boss bar to late joiners is handled for every event by the {@code EventManager}.)
 */
public class LifeDeathListener implements Listener {

    private final ElementSMPRefined plugin;
    private final LifeDeathEvent event;

    public LifeDeathListener(ElementSMPRefined plugin, LifeDeathEvent event) {
        this.plugin = plugin;
        this.event = event;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        if (!event.isActive()) return;
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        Kind kind = classify(e.getEntity());
        if (kind != null) event.recordKill(killer, kind);
    }

    /**
     * Hostile ({@link Enemy}: zombies, creepers, slimes, ghasts, phantoms, hoglins, ...) counts for
     * Death. Passive (animals, water mobs, bats) counts for Life. Players, villagers, golems and
     * tamed pets count for nothing.
     */
    static @Nullable Kind classify(LivingEntity entity) {
        if (entity instanceof Player) return null;
        if (entity instanceof Enemy) return Kind.DEATH;
        if (entity instanceof Tameable tameable && tameable.isTamed()) return null;
        if (entity instanceof Animals || entity instanceof WaterMob || entity instanceof Ambient) {
            return Kind.LIFE;
        }
        return null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        plugin.getTaskScheduler()
                .runAfterPlayerLoad(
                        () -> {
                            if (player.isOnline()) event.deliverPending(player);
                        });
    }
}
