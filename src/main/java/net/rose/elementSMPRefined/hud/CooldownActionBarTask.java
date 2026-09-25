package net.rose.elementSMPRefined.hud;

import net.kyori.adventure.text.Component;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.config.Constants;
import net.rose.elementSMPRefined.core.API.element.Element;
import net.rose.elementSMPRefined.core.API.element.ElementId;
import net.rose.elementSMPRefined.data.PlayerData;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.managers.CooldownManager;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.util.scheduling.TaskScheduler;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Repeating task that shows each online player's current element's two
 * ability cooldowns in their action bar, refreshed once a second.
 * <p>
 * Always sends for a player with an element selected (showing "Ready" for
 * an ability that's off cooldown), so the HUD is a constant, always-visible
 * readout rather than something that only appears while cooling down. Sends
 * nothing only for a player with no element at all.
 * <p>
 * Started from {@code AbstractElementPlugin#startBackgroundTasks()} and
 * stopped on plugin disable via {@link #stop()} - mirrors the
 * start/stop/cleanup shape of {@link net.rose.elementSMPRefined.util.server.GracePeriod}.
 */
public class CooldownActionBarTask {
    private final ElementSMPRefined plugin;
    private final ElementManager elementManager;
    private final CooldownManager cooldownManager;
    private BukkitTask task;

    public CooldownActionBarTask(JavaPlugin plugin, ElementManager elementManager, CooldownManager cooldownManager) {
        this.plugin = (ElementSMPRefined) plugin;
        this.elementManager = elementManager;
        this.cooldownManager = cooldownManager;
    }

    /** Starts the once-a-second refresh. No-op if already running. */
    public void start(TaskScheduler scheduler) {
        if (task != null) return;
        task = scheduler.runTimer(this::tick, Constants.Timing.TICKS_PER_SECOND, Constants.Timing.TICKS_PER_SECOND);
    }

    /** Stops the refresh, e.g. on plugin disable. Idempotent. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            updateFor(player);
        }
    }

    private void updateFor(Player player) {
        PlayerData pd = elementManager.data(player.getUniqueId());
        ElementId id = pd.getCurrentElementId();
        if (id == null) return;

        Element element = elementManager.get(id);
        if (element == null) return;

        long remaining1 = cooldownManager.getRemainingSeconds(player, element.getAbility1Id());
        long remaining2 = cooldownManager.getRemainingSeconds(player, element.getAbility2Id());

        // Ability names carry legacy '&'-style color codes baked in (for
        // chat-message use elsewhere) - Adventure logs a warning every time
        // one of those hits Component.text() raw, and this runs every
        // second for every player with an element, so strip them here
        // rather than spamming the console.
        String ability1Name = ChatColor.stripColor(element.getAbility1Name());
        String ability2Name = ChatColor.stripColor(element.getAbility2Name());

        Component bar = Lang.cooldownActionBar(ability1Name, remaining1, ability2Name, remaining2);
        player.sendActionBar(bar);
    }
}