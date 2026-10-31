package net.rose.elementSMPRefined.ability.passive.mythic.life;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.ability.main.mythic.life.EnergyBlastAbility;
import net.rose.elementSMPRefined.ability.main.mythic.life.LifeTotemAbility;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.services.EffectService;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LifeElement extends BaseElement {

    private static final int CROP_AURA_RADIUS = 5;
    private static final int CROP_AURA_HEIGHT = 1;
    private static final long CROP_AURA_INTERVAL_TICKS = 40L; // every 2 seconds

    private final Map<UUID, BukkitTask> passiveTasks = new ConcurrentHashMap<>();

    public LifeElement(JavaPlugin plugin, ConfigManager configManager) {
        super(plugin, ElementType.LIFE,
                new LifeTotemAbility(plugin, configManager),
                new EnergyBlastAbility(plugin, configManager));
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Cancel previous task to prevent stacking
        cancelPassiveTask(player);

        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, PotionEffect.INFINITE_DURATION, 0, true, false));

        var attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            if (attr.getBaseValue() < 30.0) attr.setBaseValue(30.0);
            if (player.getHealth() > attr.getBaseValue()) {
                player.setHealth(attr.getBaseValue());
            }
        }

        if (upgradeLevel >= PASSIVE_2_UPGRADE_LEVEL) {
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!player.isOnline()) {
                    cancelPassiveTask(player);
                    return;
                }

                if (player.isDead()) {
                    return;
                }

                growCropsAround(player);

            }, 0L, CROP_AURA_INTERVAL_TICKS);

            passiveTasks.put(player.getUniqueId(), task);
        }
    }

    private void growCropsAround(Player player) {
        Location origin = player.getLocation();
        World world = origin.getWorld();
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        for (int dx = -CROP_AURA_RADIUS; dx <= CROP_AURA_RADIUS; dx++) {
            for (int dz = -CROP_AURA_RADIUS; dz <= CROP_AURA_RADIUS; dz++) {
                for (int dy = -CROP_AURA_HEIGHT; dy <= CROP_AURA_HEIGHT; dy++) {
                    growIfCrop(world.getBlockAt(ox + dx, oy + dy, oz + dz));
                }
            }
        }
    }

    private boolean growIfCrop(Block block) {
        if (block.getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() < ageable.getMaximumAge()) {
                ageable.setAge(ageable.getMaximumAge());
                block.setBlockData(ageable);
                return true;
            }
        }
        return false;
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
        EffectService.removeElementPotionEffect(player, PotionEffectType.REGENERATION);

        var attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(20.0);
            if (player.getHealth() > 20.0) {
                player.setHealth(20.0);
            }
        }
    }

    @Override
    public String getDisplayName() {
        return ChatColor.GREEN + "Life";
    }

    @Override
    public List<String> getPassiveBenefits() {
        return List.of(
                "15 hearts",
                "Regeneration I",
                "Crops within 5 blocks grow fully every 2s (Upgrade II)");
    }
}