package hs.elementSMPRefined.ability.passive.death;

import hs.elementSMPRefined.API.element.BaseElement;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.ability.main.death.DeathSummonUndeadAbility;
import hs.elementSMPRefined.ability.main.death.DeathWitherSkullAbility;
import hs.elementSMPRefined.services.EffectService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DeathElement extends BaseElement {
    private final Map<UUID, BukkitTask> passiveTasks = new ConcurrentHashMap<>();

    public DeathElement(JavaPlugin plugin) {
        super(plugin, new DeathWitherSkullAbility(plugin), new DeathSummonUndeadAbility(plugin));
    }

    @Override
    public ElementType getType() {
        return ElementType.DEATH;
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Cancel any existing passive task for this player
        cancelPassiveTask(player);

        // Upside 1: Any raw or undead foods act as golden apples (handled in a listener)
        // Upside 2: Nearby enemies get hunger 1 in a 5x5 radius (if upgradeLevel >= 2)
        if (upgradeLevel >= 2) {
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        cancel();
                        passiveTasks.remove(player.getUniqueId());
                        return;
                    }

                    int radius = 5;
                    for (Player other : player.getWorld().getNearbyPlayers(player.getLocation(), radius)) {
                        if (!other.equals(player)) {
                            other.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 40, 0, true, true, true)); // 2 seconds
                        }
                    }
                }
            }.runTaskTimer(plugin, 0L, 20L); // Every second

            passiveTasks.put(player.getUniqueId(), task);
        }
    }

    private void cancelPassiveTask(Player player) {
        BukkitTask task = passiveTasks.remove(player.getUniqueId());
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    @Override
    public void clearEffects(Player player) {
        super.clearEffects(player);
        cancelPassiveTask(player);
        EffectService.removeElementPotionEffect(player, PotionEffectType.NIGHT_VISION);
    }

    @Override
    public String getDisplayName() {
        return ChatColor.DARK_PURPLE + "Death";
    }

    @Override
    public String getDescription() {
        return ChatColor.GRAY + "Master of decay and the undead. Death users can corrupt food and summon wither powers.";
    }

    @Override
    public List<String> getPassiveBenefits() {
        return List.of(
                "Permanent Night Vision",
                "Raw/undead foods heal you",
                "Nearby enemies get Hunger (Upgrade II)"
        );
    }
}
