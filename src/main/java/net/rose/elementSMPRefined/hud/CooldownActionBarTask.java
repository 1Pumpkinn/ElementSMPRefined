package net.rose.elementSMPRefined.hud;

import net.kyori.adventure.text.Component;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.core.API.element.Element;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.API.event.AbilityActivateEvent;
import net.rose.elementSMPRefined.data.PlayerData;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.managers.CooldownManager;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.util.scheduling.TaskScheduler;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.Map;

public class CooldownActionBarTask implements Listener {
    private final ElementSMPRefined plugin;
    private final ElementManager elementManager;
    private final CooldownManager cooldownManager;
    private BukkitTask task;
    private final Map<ElementType, String[]> strippedNames = new EnumMap<>(ElementType.class);

    public CooldownActionBarTask(JavaPlugin plugin, ElementManager elementManager, CooldownManager cooldownManager) {
        this.plugin = (ElementSMPRefined) plugin;
        this.elementManager = elementManager;
        this.cooldownManager = cooldownManager;
    }

    public void start(TaskScheduler scheduler) {
        if (task != null) return;
        task = scheduler.runTimer(this::tick, Constants.Timing.TICKS_PER_SECOND, Constants.Timing.TICKS_PER_SECOND);
    }

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

    @EventHandler
    public void onAbilityActivate(AbilityActivateEvent event) {
        updateFor(event.getPlayer());
    }

    private void updateFor(Player player) {
        PlayerData pd = elementManager.data(player.getUniqueId());
        ElementType type = pd.getCurrentElement();
        if (type == null) return;

        Element element = elementManager.get(type);
        if (element == null) return;

        long remaining1 = cooldownManager.getRemainingSeconds(player, element.getAbility1Id());
        long remaining2 = cooldownManager.getRemainingSeconds(player, element.getAbility2Id());

        // Ability names carry legacy '&'-style color codes baked in (for
        // chat-message use elsewhere) - Adventure logs a warning every time
        // one of those hits Component.text() raw, and this runs every
        // second for every player with an element, so strip them here
        // rather than spamming the console.
        // The names never change while the server runs, so strip them once per element instead of
        // twice per player per second.
        String[] names = strippedNames.computeIfAbsent(type, t -> new String[]{
                ChatColor.stripColor(element.getAbility1Name()),
                ChatColor.stripColor(element.getAbility2Name())});
        String ability1Name = names[0];
        String ability2Name = names[1];

        Component bar = Lang.cooldownActionBar(ability1Name, remaining1, ability2Name, remaining2,
                pd.getCurrentElementUpgradeLevel());
        player.sendActionBar(bar);
    }
}